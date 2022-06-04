package stochastic.lp

import core.EtaGenerator
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
        precision: Int
    ): StochasticOffloadingPolicy {

        var optimalPolicy: StochasticOffloadingPolicy? = null

        val etaConfigs = EtaGenerator.generate(baseSystemConfig.numberOfQueues, precision)
        require(baseSystemConfig.eta == null)

        var i = 1
        val equation4RowsCacheByEtaType: MutableMap<OffloadingEtaType, List<EquationRow>> = mutableMapOf()

        for (etaConfig in etaConfigs) {
            val systemConfigWithEtas = baseSystemConfig.withEtaConfig(etaConfig)
            println("cycle $i of ${etaConfigs.size} | etaConfig = $etaConfig | alpha = ${baseSystemConfig.alpha}")
            i++
            try {
                val optimalPolicyWithGivenEta = findOptimalPolicyWithGivenEta(systemConfigWithEtas, equation4RowsCacheByEtaType)

                if (optimalPolicy == null || optimalPolicyWithGivenEta.averageDelay < optimalPolicy.averageDelay) {
                    optimalPolicy = optimalPolicyWithGivenEta
                }
            } catch (e: IneffectivePolicyException) {
                continue
            }
        }

        if (optimalPolicy == null) {
            throw NoEffectivePolicyFoundException("No effective policy was found for the given system config which has alpha = ${baseSystemConfig.alpha}")
        }

        return optimalPolicy
    }


    fun findOptimalPolicyWithGivenEta(
        systemConfig: OffloadingSystemConfig,
        equation4RowsCacheByEtaType: MutableMap<OffloadingEtaType, List<EquationRow>>? = null
    ): StochasticOffloadingPolicy {
        require(systemConfig.eta != null)
        val optimalConfig = OffloadingSolver(systemConfig, equation4RowsCacheByEtaType).findOptimalStochasticConfig()

        return StochasticOffloadingPolicy(
            systemConfig = systemConfig,
            stochasticPolicyConfig = optimalConfig
        )
    }

}

class OffloadingSolver(
    private val systemConfig: OffloadingSystemConfig,
    private val equation4CacheByEtaType: MutableMap<OffloadingEtaType, List<EquationRow>>? = null
) {

    private val allStates: List<UserEquipmentState> = UserEquipmentStateManager.getAllStatesForConfig(systemConfig)

    fun findOptimalStochasticConfig(): StochasticPolicyConfig {
        val offloadingLPCreator = OffloadingLPCreator(systemConfig)
        lateinit var standardLinearProgram: StandardLinearProgram
        lateinit var offloadingLP: OffloadingLinearProgram

        val creationTime: Long = measureTimeMillis {
            val etaType: OffloadingEtaType = OffloadingEtaType.fromEtaConfig(systemConfig.eta!!)
            val equation4Cache = equation4CacheByEtaType?.get(etaType)

            if (equation4Cache != null) {
                offloadingLP = offloadingLPCreator.createOffloadingLinearProgramExcludingEquation4()
                val rows = offloadingLP.standardLinearProgram.rows.toMutableList()
                for (i in allStates.indices) {
                    check(rows[2 + systemConfig.numberOfQueues + i] == null)
                    rows[2 + systemConfig.numberOfQueues + i] = equation4Cache[i]
                }
                standardLinearProgram = StandardLinearProgram(rows)
            } else {
                offloadingLP = offloadingLPCreator.createOffloadingLinearProgram()
                standardLinearProgram = offloadingLP.standardLinearProgram
                if (equation4CacheByEtaType != null) {
                    updateCache(standardLinearProgram, etaType)
                }
            }
        }

         println("Creation Time : $creationTime ms")
        val indexMapping = offloadingLP.indexMapping
        lateinit var solution: LPSolution
        val solveTime = measureTimeMillis {
            solution = LPSolver.solve(standardLinearProgram)
        }
        if (solution.isAbnormal) {
            throw IneffectivePolicyException("")
        }
         println("Solve Time: $solveTime ms")
        // println("solution = $solution")

        val policyConfig = createPolicyConfig(solution, indexMapping)

        return policyConfig
    }

    private fun updateCache(
        standardLinearProgram: StandardLinearProgram,
        etaType: OffloadingEtaType
    ) {
        equation4CacheByEtaType!![etaType] = standardLinearProgram.rows.subList(
            2 + systemConfig.numberOfQueues,
            2 + systemConfig.numberOfQueues + allStates.size
        ).requireNoNulls()
    }

    private fun getStateActionProbabilities(
        solution: LPSolution,
        indexMapping: OffloadingLPCreator.IndexMapping
    ): Map<StateAction, Double> {
        return solution.variableValues
            .mapIndexed { index, d ->
                indexMapping.stateActionByCoefficientIndex[index]!! to d
            }
            .toMap()
    }

    private fun createPolicyConfig(
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
            etaConfig = systemConfig.eta!!,
            averageDelay = solution.objectiveValue,
            systemConfig = systemConfig,
            stateProbabilities = stateProbabilities,
            stateActionProbabilities = stateActionProbabilities
        )

    }
}

class IneffectivePolicyException(message: String) : RuntimeException(message)
class NoEffectivePolicyFoundException(message: String) : RuntimeException(message)