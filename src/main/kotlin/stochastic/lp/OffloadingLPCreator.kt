package stochastic.lp

import dtmc.symbol.ParameterSymbol
import dtmc.symbol.Symbol
import policy.Action
import stochastic.dtmc.IndependentTransitionCalculator
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentStateConfig
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig.Companion.allStates
import dtmc.PossibleActionProvider
import dtmc.UserEquipmentStateManager

class OffloadingLinearProgram(
    val variableCount: Int,
    val cObjective: Map<StateAction, Double>,
    val rhsObjective: Double,
    val cEquation2: Map<StateAction, Double>,
    val rhsEquation2: Double,
    val cEquation3: Map<StateAction, Double>,
    val rhsEquation3: Double,
    val cEquation4: Map<UserEquipmentState, Map<StateAction, Double>>,
    val rhsEquation4: Double,
    val cEquation5: Map<StateAction, Double>,
    val rhsEquation5: Double,
    val impossibleStateActions: Set<StateAction>
)

data class StateAction(
    val state: UserEquipmentState,
    val action: Action
) {

    override fun toString(): String {
        return "($state | $action)"
    }
}

class OffloadingLPCreator(
    val systemConfig: OffloadingSystemConfig,
) : StandardOffloadingLinearProgramCreator.StateActionToIndexMapper {
    private val possibleActionProvider: PossibleActionProvider = UserEquipmentStateManager(systemConfig.stateConfig)

    private val variableCount: Int =
        (systemConfig.taskQueueCapacity + 1) * (systemConfig.tuNumberOfPackets + 1) * systemConfig.cpuNumberOfSections * systemConfig.allActions.size

    private val symbolMapping: Map<Symbol, Double> = run {
        mapOf(
            ParameterSymbol.Beta to systemConfig.beta,
            ParameterSymbol.BetaC to 1.0 - systemConfig.beta,
            ParameterSymbol.Alpha to systemConfig.alpha,
            ParameterSymbol.AlphaC to 1.0 - systemConfig.alpha
        )
    }
    val itCalculator = IndependentTransitionCalculator(systemConfig.stateConfig)

    private fun getCoefficientsForObjective(): Map<StateAction, Double> {
        val cObjective: MutableMap<StateAction, Double> = mutableMapOf()
        systemConfig.stateConfig.allStates().forEach { state ->
            systemConfig.allActions.forEach { action ->
                cObjective[StateAction(state, action)] = state.taskQueueLength / systemConfig.alpha
            }
        }
        return cObjective
    }

    private fun expectedTaskTime(): Double {
        val eta: Double = systemConfig.eta
        val numberOfSections: Int = systemConfig.cpuNumberOfSections
        return (eta * numberOfSections + (1 - eta) * systemConfig.expectedTCloud())
    }

    private fun rhsObjective(): Double {
        return -expectedTaskTime()
    }

    private fun getCoefficientsForEquation2(): Map<StateAction, Double> {
        val cEquation2: MutableMap<StateAction, Double> = mutableMapOf()
        val pLoc = systemConfig.pLoc
        val pTx = systemConfig.pTx
        val beta = systemConfig.beta
        systemConfig.stateConfig.allStates().forEach { state ->
            systemConfig.allActions.map { action ->
                val stateAction = StateAction(state, action)
                cEquation2[stateAction] = 0.0
                val isActionPossible = possibleActionProvider.getPossibleActions(state).contains(action)
                if (!isActionPossible) return@map

                if (state.tuState > 0 || (action in listOf(
                        Action.AddToTransmissionUnit,
                        Action.AddToBothUnits
                    ))
                ) {
                    cEquation2[stateAction] = cEquation2[stateAction]!! + beta * pTx
                }

                if (state.cpuState > 0 || (action in listOf(
                        Action.AddToCPU,
                        Action.AddToBothUnits
                    ))
                ) {
                    cEquation2[stateAction] = cEquation2[stateAction]!! + pLoc
                }
            }
        }
        return cEquation2
    }

    private fun getCoefficientsForEquation3(): Map<StateAction, Double> {
        val cEquation3: MutableMap<StateAction, Double> = mutableMapOf()
        systemConfig.stateConfig.allStates().forEach { state ->
            systemConfig.allActions.forEach { action ->
                val stateAction = StateAction(state, action)
                val possibleActions = possibleActionProvider.getPossibleActions(state)

                if (possibleActions.contains(action)) {
                    cEquation3[stateAction] = when (action) {
                        Action.AddToCPU -> {
                            (1.0 - systemConfig.eta)
                        }
                        Action.AddToBothUnits -> {
                            (1.0 - 2 * systemConfig.eta)
                        }
                        Action.AddToTransmissionUnit -> {
                            (-systemConfig.eta)
                        }
                        Action.NoOperation -> {
                            0.0
                        }
                    }
                } else {
                    cEquation3[stateAction] = 0.0
                }
            }
        }
        return cEquation3
    }

    private fun getCoefficientsForEquation4(): Map<UserEquipmentState, Map<StateAction, Double>> {
        val cEquation4: MutableMap<UserEquipmentState, MutableMap<StateAction, Double>> = mutableMapOf()
        systemConfig.stateConfig.allStates().forEach { dest ->
            cEquation4[dest] = mutableMapOf()
            systemConfig.stateConfig.allStates().forEach { source ->
                systemConfig.allActions.forEach { action ->
                    val fraction = itCalculator.getIndependentTransitionFraction(source, dest, action)
                    val independentTransitionValue: Double = fraction.resolveByMapping(symbolMapping)
                    if (source == dest) {
                        cEquation4[dest]!![StateAction(source, action)] = independentTransitionValue - 1
                    } else {
                        cEquation4[dest]!![StateAction(source, action)] = independentTransitionValue
                    }
                }
            }
        }
        return cEquation4
    }

    private fun getCoefficientsForEquation5(): Map<StateAction, Double> {
        val cEquation5: MutableMap<StateAction, Double> = mutableMapOf()
        systemConfig.stateConfig.allStates().forEach { state ->
            systemConfig.allActions.forEach { action ->
                val stateAction = StateAction(state, action)
                cEquation5[stateAction] = 1.0
            }
        }
        return cEquation5
    }

    fun createStandardLinearProgram(): StandardLinearProgram {
        val offloadingLinearProgram = createOffloadingLinearProgram()

        val standardOffloadingLinearProgramCreator = StandardOffloadingLinearProgramCreator(
            offloadingLinearProgram = offloadingLinearProgram,
            stateConfig = systemConfig.stateConfig,
            stateActionToIndexMapper = this,
        )

        return standardOffloadingLinearProgramCreator.createStandardLinearProgram()
    }

    fun createOffloadingLinearProgram(): OffloadingLinearProgram {
        val cObjective = getCoefficientsForObjective()
        val cEquation2 = getCoefficientsForEquation2()
        val cEquation3 = getCoefficientsForEquation3()
        val cEquation4 = getCoefficientsForEquation4()
        val cEquation5 = getCoefficientsForEquation5()
        val rhsObjective: Double = rhsObjective()
        val rhsEquation2: Double = systemConfig.pMax
        val rhsEquation3: Double = 0.0
        val rhsEquation4: Double = 0.0
        val rhsEquation5 = 1.0
        val impossibleStateActions = getImpossibleStateActions()

        val offloadingLinearProgram = OffloadingLinearProgram(
            cObjective = cObjective,
            rhsObjective = rhsObjective,
            cEquation2 = cEquation2,
            rhsEquation2 = rhsEquation2,
            cEquation3 = cEquation3,
            rhsEquation3 = rhsEquation3,
            cEquation4 = cEquation4,
            rhsEquation4 = rhsEquation4,
            cEquation5 = cEquation5,
            rhsEquation5 = rhsEquation5,
            variableCount = variableCount,
            impossibleStateActions = impossibleStateActions
        )

        return offloadingLinearProgram
    }

    private fun getImpossibleStateActions(): Set<StateAction> {
        val result: MutableSet<StateAction> = mutableSetOf()
        systemConfig.stateConfig.allStates().forEach { state ->
            val possibleActions = possibleActionProvider.getPossibleActions(state)
            systemConfig.allActions.forEach { action ->
                if (!possibleActions.contains(action)) {
                    result.add(StateAction(state, action))
                }
            }
        }
        return result
    }

    override fun stateActionToIndex(stateAction: StateAction): Int {
        val (x, y, z) = stateAction.state
        var idx = 0
        idx += x * (systemConfig.tuNumberOfPackets + 1) * (systemConfig.cpuNumberOfSections) * systemConfig.actionCount
        idx += y * (systemConfig.cpuNumberOfSections) * systemConfig.actionCount
        idx += z * systemConfig.actionCount
        idx += stateAction.action.order

        return idx
    }
}

internal class StandardOffloadingLinearProgramCreator(
    private val offloadingLinearProgram: OffloadingLinearProgram,
    private val stateConfig: UserEquipmentStateConfig,
    private val stateActionToIndexMapper: StateActionToIndexMapper,
) {

    private fun coefficientsToList(map: Map<StateAction, Double>): List<Double> {
        check(map.size == offloadingLinearProgram.variableCount)

        return map.mapKeys { (key: StateAction, _) ->
            stateActionToIndexMapper.stateActionToIndex(key)
        }.toList().sortedBy { it.first }.map { it.second }
    }

    fun createStandardLinearProgram(): StandardLinearProgram {
        val rows = mutableListOf<EquationRow>()
        val zeroVariables =
            offloadingLinearProgram.impossibleStateActions.map { stateActionToIndexMapper.stateActionToIndex(it) }
                .toSet()

        rows.add(
            EquationRow(
                coefficients = coefficientsToList(offloadingLinearProgram.cObjective),
                rhs = offloadingLinearProgram.rhsObjective,
                type = EquationRow.Type.Objective
            )
        )

        rows.add(
            EquationRow(
                coefficients = coefficientsToList(offloadingLinearProgram.cEquation2),
                rhs = offloadingLinearProgram.rhsEquation2,
                type = EquationRow.Type.LessThan
            )
        )

        rows.add(
            EquationRow(
                coefficients = coefficientsToList(offloadingLinearProgram.cEquation3),
                rhs = offloadingLinearProgram.rhsEquation3,
                type = EquationRow.Type.Equality
            )
        )

        stateConfig.allStates().forEach { state ->
            rows.add(
                EquationRow(
                    coefficients = coefficientsToList(offloadingLinearProgram.cEquation4[state]!!),
                    rhs = offloadingLinearProgram.rhsEquation4,
                    type = EquationRow.Type.Equality
                )
            )
        }

        rows.add(
            EquationRow(
                coefficients = coefficientsToList(offloadingLinearProgram.cEquation5),
                rhs = offloadingLinearProgram.rhsEquation5,
                type = EquationRow.Type.Equality
            )
        )

        return StandardLinearProgram(rows, zeroVariables)
    }

    interface StateActionToIndexMapper {

        fun stateActionToIndex(stateAction: StateAction): Int
    }
}