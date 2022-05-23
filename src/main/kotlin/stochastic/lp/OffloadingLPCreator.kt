package stochastic.lp

import core.mutableListOfZeros
import dtmc.symbol.ParameterSymbol
import dtmc.symbol.Symbol
import policy.Action
import stochastic.dtmc.IndependentTransitionCalculator
import core.ue.OffloadingSystemConfig
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig.Companion.allStates
import core.UserEquipmentStateManager
import stochastic.dtmc.DTMCCreator
import stochastic.dtmc.DiscreteTimeMarkovChain

class OffloadingLinearProgram(
    val indexMapping: OffloadingLPCreator.IndexMapping,
    val standardLinearProgram: StandardLinearProgram
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
    val systemConfig: OffloadingSystemConfig
) {
    private val userEquipmentStateManager = UserEquipmentStateManager(systemConfig.stateConfig)
    private val dtmcCreator: DTMCCreator = DTMCCreator(systemConfig.stateConfig)

    private lateinit var allStates: List<UserEquipmentState>
    private lateinit var indexMapping: IndexMapping
    private lateinit var discreteTimeMarkovChain: DiscreteTimeMarkovChain

    private val possibleActionsByState: MutableMap<UserEquipmentState, List<Action>> = mutableMapOf()

    private val symbolMapping: Map<Symbol, Double> = run {
        mapOf(
            ParameterSymbol.Beta to systemConfig.beta,
            ParameterSymbol.BetaC to 1.0 - systemConfig.beta,
            ParameterSymbol.Alpha to systemConfig.alpha,
            ParameterSymbol.AlphaC to 1.0 - systemConfig.alpha
        )
    }

    private lateinit var itCalculator: IndependentTransitionCalculator

    private fun getObjectiveEquation(): EquationRow {
        val rhsObjective = -expectedTaskTime()
        val coefficients = mutableListOfZeros(indexMapping.variableCount)

        for (state in allStates) {
            val possibleActions = possibleActionsByState[state]!!
            val coefficientValue = state.taskQueueLength / systemConfig.alpha

            for (action in possibleActions) {
                val index = variableIndex(state, action)
                coefficients[index] = coefficientValue
            }
        }

        return EquationRow(
            coefficients = coefficients,
            rhs = rhsObjective,
            type = EquationRow.Type.Objective
        )
    }

    private fun expectedTaskTime(): Double {
        val eta: Double = systemConfig.eta
        val numberOfSections: Int = systemConfig.cpuNumberOfSections
        return (eta * numberOfSections + (1 - eta) * systemConfig.expectedTCloud())
    }

    private fun getEquation2(): EquationRow {
        val pLoc = systemConfig.pLoc
        val pTx = systemConfig.pTx
        val beta = systemConfig.beta
        val rhsEquation2 = systemConfig.pMax
        val coefficients = mutableListOfZeros(indexMapping.variableCount)

        for (state in allStates) {
            val possibleActions = possibleActionsByState[state]!!
            for (action in possibleActions) {
                var coefficientValue = 0.0

                if (state.tuState > 0 || (action in listOf(
                        Action.AddToTransmissionUnit,
                        Action.AddToBothUnits
                    ))
                ) {
                    coefficientValue += beta * pTx
                }

                if (state.cpuState > 0 || (action in listOf(
                        Action.AddToCPU,
                        Action.AddToBothUnits
                    ))
                ) {
                    coefficientValue += pLoc
                }

                val index = variableIndex(state, action)
                coefficients[index] = coefficientValue
            }
        }

        return EquationRow(
            coefficients = coefficients,
            rhs = rhsEquation2,
            type = EquationRow.Type.LessThan
        )
    }

    private fun getEquation3(): EquationRow {
        val coefficients = mutableListOfZeros(indexMapping.variableCount)
        val rhsEquation3 = 0.0

        for (state in allStates) {
            val possibleActions = userEquipmentStateManager.getPossibleActions(state)

            for (action in possibleActions) {
                val coefficientValue = when (action) {
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

                val index = variableIndex(state, action)
                coefficients[index] = coefficientValue
            }
        }

        return EquationRow(
            coefficients = coefficients,
            rhs = rhsEquation3
        )
    }

    private fun getEquations4(): MutableList<EquationRow> {
        val equations: MutableList<EquationRow> = mutableListOf()

        for (destState in allStates) {
            equations.add(getEquation4(destState))
        }

        return equations
    }

    private fun getEquation4(destState: UserEquipmentState): EquationRow {
        val rhsEquation4 = 0.0
        val coefficients = mutableListOf<Double>()
        for (i in 0 until indexMapping.variableCount) coefficients.add(0.0)

        for (sourceState in allStates) {
            val possibleActions = possibleActionsByState[sourceState]!!
            for (action in possibleActions) {
                val independentTransitionValue = itCalculator.getIndependentTransitionFraction(sourceState, destState, action)

                val coefficientValue: Double = if (sourceState == destState) {
                    independentTransitionValue - 1
                } else {
                    independentTransitionValue
                }

                val index = indexMapping.coefficientIndexByStateAction[StateAction(sourceState, action)]!!
                coefficients[index] = coefficientValue
            }
        }

        return EquationRow(coefficients = coefficients, rhs = rhsEquation4)
    }

    private fun getEquation5(): EquationRow {
        val rhsEquation5 = 1.0
        val coefficients = mutableListOfZeros(indexMapping.variableCount)

        for (state in allStates) {
            val possibleActions = possibleActionsByState[state]!!
            for (action in possibleActions) {
                val index: Int = variableIndex(state, action)
                coefficients[index] = 1.0
            }
        }

        return EquationRow(
            coefficients = coefficients,
            rhs = rhsEquation5
        )
    }

    private fun variableIndex(state: UserEquipmentState, action: Action): Int {
        return indexMapping.coefficientIndexByStateAction[StateAction(state, action)]!!
    }

    private fun populatePossibleActions() {
        check(possibleActionsByState.isEmpty())

        for (state in allStates) {
            val actions = userEquipmentStateManager.getPossibleActions(state)
            possibleActionsByState[state] = actions
        }
    }

    fun createOffloadingLinearProgram(): OffloadingLinearProgram {
        allStates = systemConfig.stateConfig.allStates()
        indexMapping = createIndexMapping()
        discreteTimeMarkovChain = dtmcCreator.create()
        itCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)
        populatePossibleActions()
        val equations: MutableList<EquationRow> = mutableListOf()

        with(equations) {
            add(getObjectiveEquation())
            add(getEquation2())
            add(getEquation3())
            addAll(getEquations4())
            add(getEquation5())
        }
        val standardLinearProgram = StandardLinearProgram(equations)

        return OffloadingLinearProgram(
            standardLinearProgram = standardLinearProgram,
            indexMapping = indexMapping
        )
    }

    private fun createIndexMapping(): IndexMapping {
        val stateActionByCoefficientIndex: MutableMap<Int, StateAction> = mutableMapOf()
        val coefficientIndexByStateAction: MutableMap<StateAction, Int> = mutableMapOf()

        var indexPtr = 0
        for (state in allStates) {
            val possibleActions = userEquipmentStateManager.getPossibleActions(state)
            for (action in possibleActions) {
                val stateAction = StateAction(state, action)
                stateActionByCoefficientIndex[indexPtr] = stateAction
                coefficientIndexByStateAction[stateAction] = indexPtr
                indexPtr++
            }
        }
        val variableCount = indexPtr

        return IndexMapping(
            stateActionByCoefficientIndex = stateActionByCoefficientIndex,
            coefficientIndexByStateAction = coefficientIndexByStateAction,
            variableCount = variableCount
        )
    }

    fun createOffloadingLinearProgramExcludingEquation4(): OffloadingLinearProgram {
        allStates = systemConfig.stateConfig.allStates()
        indexMapping = createIndexMapping()
        // discreteTimeMarkovChain = dtmcCreator.create()
        // itCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)
        populatePossibleActions()
        val equations: MutableList<EquationRow?> = mutableListOf()

        with(equations) {
            add(getObjectiveEquation())
            add(getEquation2())
            add(getEquation3())
            for (i in 1..systemConfig.stateCount()) {
                add(null)
            }
            add(getEquation5())
        }
        val standardLinearProgram = StandardLinearProgram(equations)

        return OffloadingLinearProgram(
            standardLinearProgram = standardLinearProgram,
            indexMapping = indexMapping
        )
    }

    data class IndexMapping(
        val stateActionByCoefficientIndex: Map<Int, StateAction>,
        val coefficientIndexByStateAction: Map<StateAction, Int>,
        val variableCount: Int
    )
}