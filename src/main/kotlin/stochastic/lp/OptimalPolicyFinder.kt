package stochastic.lp

import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withEta
import core.ue.UserEquipmentStateConfig.Companion.allStates
import core.UserEquipmentStateManager
import ue.UserEquipmentState
import kotlin.system.measureTimeMillis

object RangedOptimalPolicyFinder {

    fun findOptimalPolicy(baseSystemConfig: OffloadingSystemConfig, rangeStart: Double, rangeEnd: Double, precision: Int): StochasticOffloadingPolicy {

        var optimalPolicy: StochasticOffloadingPolicy? = null

        for (i in 0..precision) {
            val eta = rangeStart + (rangeEnd - rangeStart) * (i.toDouble() / precision)
            println("cycle $i of $precision | eta = $eta | alpha = ${baseSystemConfig.alpha}")
            val etaConfig = baseSystemConfig.withEta(eta)
            val optimalPolicyWithGivenEta = OptimalPolicyFinder.findOptimalPolicy(etaConfig, true)

            if (optimalPolicy == null || optimalPolicyWithGivenEta.averageDelay < optimalPolicy.averageDelay) {
                optimalPolicy = optimalPolicyWithGivenEta
            }
        }

        return optimalPolicy!!
    }
}

object OptimalPolicyFinder {

    fun findOptimalPolicy(systemConfig: OffloadingSystemConfig, useEquationCache: Boolean = false): StochasticOffloadingPolicy {
        val optimalConfig = OffloadingSolver(systemConfig).findOptimalStochasticConfig(useEquationCache)

        return StochasticOffloadingPolicy(
            systemConfig = systemConfig,
            stochasticPolicyConfig = optimalConfig
        )
    }

    class EffectivePolicyNotExistsException : Exception()
}

class OffloadingSolver(
    private val systemConfig: OffloadingSystemConfig
) {
    var equation4RowsCache: List<EquationRow>? = null

    fun findOptimalStochasticConfig(useEquationCache: Boolean = false): StochasticPolicyConfig {
        val offloadingLPCreator: OffloadingLPCreator = OffloadingLPCreator(systemConfig)
        lateinit var standardLinearProgram: StandardLinearProgram

        lateinit var offloadingLP: OffloadingLinearProgram

        val creationTime: Long = measureTimeMillis {
            if (useEquationCache && equation4RowsCache != null) {
                offloadingLP = offloadingLPCreator.createOffloadingLinearProgramExcludingEquation4()
                val rows = offloadingLP.standardLinearProgram.rows.toMutableList()
                for (i in 0..systemConfig.stateCount()) {
                    check(rows[3 + i] == null)
                    rows[3 + i] = equation4RowsCache!![i]
                }
                standardLinearProgram = StandardLinearProgram(rows)
            } else {
                offloadingLP = offloadingLPCreator.createOffloadingLinearProgram()
                standardLinearProgram = offloadingLP.standardLinearProgram
                if (equation4RowsCache == null) {
                    equation4RowsCache = standardLinearProgram.rows.subList(3, 3 + systemConfig.stateCount()).requireNoNulls()
                }
            }
        }
        val indexMapping = offloadingLP.indexMapping
        val solution: LPSolution = LPSolver.solve(standardLinearProgram)

        val policyConfig = createPolicyConfig(solution, indexMapping)

        return policyConfig
    }

    fun createPolicyConfig(solution: LPSolution, indexMapping: OffloadingLPCreator.IndexMapping): StochasticPolicyConfig {
        // checks
        val allStates = systemConfig.stateConfig.allStates()
        val possibleActionProvider = UserEquipmentStateManager(systemConfig.stateConfig)
        var expectedVariableCount = 0
        for (state in allStates) {
            val possibleActions = possibleActionProvider.getPossibleActions(state)
            expectedVariableCount += possibleActions.size
        }
        check(solution.variableValues.size == expectedVariableCount)
        check(indexMapping.stateActionByCoefficientIndex.size == expectedVariableCount)

        // create
        val stateProbabilities = mutableMapOf<UserEquipmentState, Double>()

        val stateActionProbabilities: Map<StateAction, Double> = solution.variableValues.mapIndexed { index, d ->
            indexMapping.stateActionByCoefficientIndex[index]!! to d
        }.toMap()

        stateActionProbabilities.forEach { (key: StateAction, value: Double) ->
            if (stateProbabilities.containsKey(key.state)) {
                stateProbabilities[key.state] = stateProbabilities[key.state]!! + value
            } else {
                stateProbabilities[key.state] = value
            }
        }

        val decisions: MutableMap<StateAction, Double> =
            stateActionProbabilities.mapValues { (key: StateAction, value: Double) ->
                if (stateProbabilities[key.state] == 0.0) {
                    0.0
                } else {
                    value / stateProbabilities[key.state]!!
                }
            }.toMutableMap()

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
            eta = systemConfig.eta,
            averageDelay = solution.objectiveValue,
            systemConfig = systemConfig,
            stateProbabilities = stateProbabilities
        )

    }

    data class StochasticPolicyConfig(
        val eta: Double,
        val averageDelay: Double,
        val decisionProbabilities: Map<StateAction, Double>,
        val stateProbabilities: Map<UserEquipmentState, Double>,
        val systemConfig: OffloadingSystemConfig
    ) {

        fun fullQueueProbability(): Double {
            val probabilityQueueFull: Double = systemConfig.stateConfig.allStates()
                .filter { it.taskQueueLength == systemConfig.taskQueueCapacity }
                .sumOf { state -> stateProbabilities[state]!! }
            return probabilityQueueFull
        }
    }
}

interface IndexToStateActionMapper {

    fun mapToStateAction(index: Int, systemConfig: OffloadingSystemConfig): StateAction
}