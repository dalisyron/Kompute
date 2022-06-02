package stochastic.lp

import core.EtaGenerator
import core.StateManagerConfig
import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withEtaConfig
import core.UserEquipmentStateManager
import core.policy.Action
import core.ue.UserEquipmentState
import kotlin.system.measureTimeMillis

object RangedOptimalPolicyFinder {

    fun findOptimalPolicy(
        baseSystemConfig: OffloadingSystemConfig,
        rangeStart: Double,
        rangeEnd: Double,
        precision: Int
    ): StochasticOffloadingPolicy {

        var optimalPolicy: StochasticOffloadingPolicy? = null

        val etaConfigs = EtaGenerator.generate(baseSystemConfig.numberOfQueues, precision)

        var i = 1

        for (etaConfig in etaConfigs) {
            val systemConfigWithEtas = baseSystemConfig.withEtaConfig(etaConfig)
            println("cycle $i of ${etaConfigs.size} | etaConfig = $etaConfig | alpha = ${baseSystemConfig.alpha}")
            try {
                val optimalPolicyWithGivenEta = OptimalPolicyFinder.findOptimalPolicy(systemConfigWithEtas, true)

                if (optimalPolicy == null || optimalPolicyWithGivenEta.averageDelay < optimalPolicy.averageDelay) {
                    optimalPolicy = optimalPolicyWithGivenEta
                }
            } catch (e: IneffectivePolicyException) {
                continue
            }
            i++
        }

        if (optimalPolicy == null) {
            throw NoEffectivePolicyFoundException("No effective policy was found for the given system config which has alpha = ${baseSystemConfig.alpha}")
        }

        return optimalPolicy
    }


}

object OptimalPolicyFinder {

    fun findOptimalPolicy(
        systemConfig: OffloadingSystemConfig,
        useEquationCache: Boolean = false
    ): StochasticOffloadingPolicy {
        val optimalConfig = OffloadingSolver(systemConfig).findOptimalStochasticConfig(useEquationCache)

        return StochasticOffloadingPolicy(
            systemConfig = systemConfig,
            stochasticPolicyConfig = optimalConfig
        )
    }
}

class OffloadingSolver(
    private val systemConfig: OffloadingSystemConfig
) {
    lateinit var allStates: List<UserEquipmentState>

    val stateManager = UserEquipmentStateManager(
        StateManagerConfig(
            userEquipmentStateConfig = systemConfig.stateConfig,
            limitation = systemConfig.getLimitation()
        )
    )

    init {
        allStates = stateManager.allStates()
    }

    var equation4RowsCache: List<EquationRow>? = null

    fun findOptimalStochasticConfig(useEquationCache: Boolean = false): StochasticPolicyConfig {
        val offloadingLPCreator = OffloadingLPCreator(systemConfig)
        lateinit var standardLinearProgram: StandardLinearProgram

        lateinit var offloadingLP: OffloadingLinearProgram

        val creationTime: Long = measureTimeMillis {
//            if (useEquationCache && equation4RowsCache != null) {
//                offloadingLP = offloadingLPCreator.createOffloadingLinearProgramExcludingEquation4()
//                val rows = offloadingLP.standardLinearProgram.rows.toMutableList()
//                for (i in allStates.indices) {
//                    check(rows[2 + systemConfig.numberOfQueues + i] == null)
//                    rows[2 + systemConfig.numberOfQueues + i] = equation4RowsCache!![i]
//                }
//                standardLinearProgram = StandardLinearProgram(rows)
//            } else {
                offloadingLP = offloadingLPCreator.createOffloadingLinearProgram()
                standardLinearProgram = offloadingLP.standardLinearProgram
//                if (equation4RowsCache == null) {
//                    equation4RowsCache =
//                        standardLinearProgram.rows.subList(2 + systemConfig.numberOfQueues, 2 + systemConfig.numberOfQueues + allStates.size).requireNoNulls()
//                }
            }

        // println("Creation Time : $creationTime ms")
        val indexMapping = offloadingLP.indexMapping
        lateinit var solution: LPSolution
        val solveTime = measureTimeMillis {
            solution = LPSolver.solve(standardLinearProgram)
        }
        if (solution.isAbnormal) {
            throw IneffectivePolicyException("")
        }
        // println("Solve Time: $solveTime ms")
        // println("solution = $solution")

        val policyConfig = createPolicyConfig(solution, indexMapping)

        return policyConfig
    }

    private fun checkSolution(solution: LPSolution, indexMapping: OffloadingLPCreator.IndexMapping) {
        val stateActionProbabilities: MutableMap<StateAction, Double> = solution.variableValues.mapIndexed { index, d ->
            indexMapping.stateActionByCoefficientIndex[index]!! to d
        }.toMap().toMutableMap()


        for (state in allStates) {
            for (action in systemConfig.allActions) {
                val key = StateAction(state, action)
                if (!stateActionProbabilities.containsKey(key)) {
                    stateActionProbabilities[key] = 0.0
                }
            }
        }

        println("CHECKING")
        stateActionProbabilities.keys.forEach {
            if (stateActionProbabilities[it]!! > 0.0) {
                println("P($it) = ${stateActionProbabilities[it]}")
            }
        }
    }

    fun getStateActionProbabilities(
        solution: LPSolution,
        indexMapping: OffloadingLPCreator.IndexMapping
    ): Map<StateAction, Double> {
        return solution.variableValues
            .mapIndexed { index, d ->
                indexMapping.stateActionByCoefficientIndex[index]!! to d
            }
            .toMap()
    }

    fun validateStateActionProbabilities(stateActionProbabilities: Map<StateAction, Double>) {

    }

    fun createPolicyConfig(
        solution: LPSolution,
        indexMapping: OffloadingLPCreator.IndexMapping
    ): StochasticPolicyConfig {
        // checks
        val possibleActionProvider = UserEquipmentStateManager(systemConfig.getStateManagerConfig())
        var expectedVariableCount = 0
        for (state in allStates) {
            val possibleActions = possibleActionProvider.getPossibleActions(state)
            expectedVariableCount += possibleActions.size
        }
        check(solution.variableValues.size == expectedVariableCount)
        check(indexMapping.stateActionByCoefficientIndex.size == expectedVariableCount)

        // create
        val stateProbabilities = mutableMapOf<UserEquipmentState, Double>()
        val stateActionProbabilities: Map<StateAction, Double> = getStateActionProbabilities(solution, indexMapping)

        stateActionProbabilities.forEach { (key: StateAction, value: Double) ->
            if (stateProbabilities.containsKey(key.state)) {
                stateProbabilities[key.state] = stateProbabilities[key.state]!! + value
            } else {
                stateProbabilities[key.state] = value
            }
        }

        val queueFullProbability = stateActionProbabilities
            .filter {
                it.key.state.taskQueueLengths.any { it == systemConfig.taskQueueCapacity } && it.key.action != Action.NoOperation
            }
            .values
            .sum()


        val decisions: MutableMap<StateAction, Double> =
            stateActionProbabilities.mapValues { (key: StateAction, stateActionProbability: Double) ->
                val stateProbability = stateProbabilities[key.state]!!
                if (stateProbability == 0.0) {
                    0.0
                } else {
                    stateActionProbability / stateProbability
                }
            }.toMutableMap()

        if (queueFullProbability > (1.0 / allStates.size)) {
            throw IneffectivePolicyException("queueFullProbability = $queueFullProbability | eta = ${systemConfig.eta} | alpha = ${systemConfig.alpha} | averageDelay = ${solution.objectiveValue}")
        }

        for (state in allStates) {
            val possibleActions = possibleActionProvider.getPossibleActions(state)
            for (action in systemConfig.allActions) {
                if (!possibleActions.contains(action)) {
                    check(!stateActionProbabilities.contains(StateAction(state, action)))
                    decisions[StateAction(state, action)] = 0.0
                }
            }
        }

        for (state in allStates) {
            for (action in systemConfig.allActions) {
                check(decisions.contains(StateAction(state, action))) {
                    StateAction(state, action)
                }
            }
        }

        return StochasticPolicyConfig(
            decisionProbabilities = decisions,
            etaConfig = systemConfig.eta,
            averageDelay = solution.objectiveValue,
            systemConfig = systemConfig,
            stateProbabilities = stateProbabilities,
            stateActionProbabilities = stateActionProbabilities
        )

    }

    data class StochasticPolicyConfig(
        val etaConfig: List<Double>,
        val averageDelay: Double,
        val decisionProbabilities: Map<StateAction, Double>,
        val stateProbabilities: Map<UserEquipmentState, Double>,
        val stateActionProbabilities: Map<StateAction, Double>,
        val systemConfig: OffloadingSystemConfig
    )
}

class IneffectivePolicyException(message: String) : RuntimeException(message)
class NoEffectivePolicyFoundException(message: String) : RuntimeException(message)