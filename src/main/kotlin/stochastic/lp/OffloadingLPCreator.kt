package stochastic.lp

import core.mutableListOfZeros
import core.symbol.ParameterSymbol
import core.symbol.Symbol
import core.policy.Action
import stochastic.dtmc.IndependentTransitionCalculator
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentState
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
        return "($state\t|\t$action)"
    }
}

class OffloadingLPCreator(
    val systemConfig: OffloadingSystemConfig
) {
    private val userEquipmentStateManager = UserEquipmentStateManager(systemConfig.getStateManagerConfig())
    private val dtmcCreator: DTMCCreator = DTMCCreator(systemConfig.getStateManagerConfig())

    private lateinit var allStates: List<UserEquipmentState>
    private lateinit var indexMapping: IndexMapping
    private lateinit var discreteTimeMarkovChain: DiscreteTimeMarkovChain

    private val possibleActionsByState: MutableMap<UserEquipmentState, List<Action>> = mutableMapOf()

    private val symbolMapping: Map<Symbol, Double> by lazy { createSymbolMapping() }

    private fun createSymbolMapping(): Map<Symbol, Double> {
        val result: MutableMap<Symbol, Double> = mutableMapOf()

        for (queueIndex in 0 until systemConfig.numberOfQueues) {
            result[ParameterSymbol.Alpha(queueIndex)] = systemConfig.alpha[queueIndex]
            result[ParameterSymbol.AlphaC(queueIndex)] = 1.0 - systemConfig.alpha[queueIndex]
        }

        result[ParameterSymbol.Beta] = systemConfig.beta
        result[ParameterSymbol.BetaC] = 1.0 - systemConfig.beta

        return result
    }

    private lateinit var itCalculator: IndependentTransitionCalculator

    private fun getObjectiveEquation(): EquationRow {
        var rhsObjective = 0.0

        val coefficients = mutableListOfZeros(indexMapping.variableCount)

        for (queueIndex in 0 until systemConfig.numberOfQueues) {
            val queuePrescaler = (systemConfig.alpha[queueIndex] / systemConfig.totalAlpha)
            rhsObjective -= queuePrescaler * systemConfig.expectedTaskTime(queueIndex)
            indexMapping.coefficientIndexByStateAction.forEach { (stateAction: StateAction, index: Int) ->
                val taskQueueLength = stateAction.state.taskQueueLengths[queueIndex]
                coefficients[index] += queuePrescaler * (taskQueueLength / systemConfig.alpha[queueIndex])
            }
        }

        return EquationRow(
            coefficients = coefficients,
            rhs = rhsObjective,
            type = EquationRow.Type.Objective
        )
    }

    private fun getEquation2(): EquationRow {
        val pLoc = systemConfig.pLoc
        val pTx = systemConfig.pTx
        val beta = systemConfig.beta
        val rhsEquation2 = systemConfig.pMax
        val coefficients = mutableListOfZeros(indexMapping.variableCount)

        indexMapping.coefficientIndexByStateAction.forEach { (stateAction, index) ->
            val (state, action) = stateAction
            var coefficientValue = 0.0

            if (state.isTUActive() || (action is Action.AddToTransmissionUnit || action is Action.AddToBothUnits)) {
                coefficientValue += beta * pTx
            }

            if (state.isCPUActive() || (action is Action.AddToCPU) || (action is Action.AddToBothUnits)) {
                coefficientValue += pLoc
            }

            coefficients[index] = coefficientValue

        }

        return EquationRow(
            coefficients = coefficients,
            rhs = rhsEquation2,
            type = EquationRow.Type.LessThan
        )
    }

    private fun getEquations3(): List<EquationRow> {
        val result: MutableList<EquationRow> = mutableListOf()

        for (queueIndex in 0 until systemConfig.numberOfQueues) {
            result.add(getEquation3(queueIndex))
        }

        return result
    }

    private fun getEquation3(queueIndex: Int): EquationRow {
        val coefficients = mutableListOfZeros(indexMapping.variableCount)
        val rhsEquation3 = 0.0

        indexMapping.coefficientIndexByStateAction.forEach { (stateAction, index) ->
            val (state, action) = stateAction
            if (stateAction.action is Action.NoOperation) {
                return@forEach
            }
            val coefficientValue = when (action) {
                is Action.AddToCPU -> {
                    if (action.queueIndex == queueIndex) {
                        1.0 - systemConfig.eta!![queueIndex]
                    } else {
                        0.0
                    }
                }
                is Action.AddToBothUnits -> {
                    var temp = 0.0
                    if (action.cpuTaskQueueIndex == queueIndex) {
                        temp += (1.0 - systemConfig.eta!![queueIndex])
                    }
                    if (action.transmissionUnitTaskQueueIndex == queueIndex) {
                        temp += -systemConfig.eta!![queueIndex]
                    }
                    temp
                }
                is Action.AddToTransmissionUnit -> {
                    if (action.queueIndex == queueIndex) {
                        -systemConfig.eta!![queueIndex]
                    } else {
                        0.0
                    }
                }
                Action.NoOperation -> {
                    0.0
                }
            }

            coefficients[index] = coefficientValue
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

        indexMapping.coefficientIndexByStateAction.forEach { (stateAction, index) ->
            val (sourceState, action) = stateAction
            val independentTransitionValue =
                itCalculator.getIndependentTransitionFraction(sourceState, destState, action)

            val coefficientValue: Double = if (sourceState == destState) {
                independentTransitionValue - 1
            } else {
                independentTransitionValue
            }

            coefficients[index] = coefficientValue
        }

        return EquationRow(coefficients = coefficients, rhs = rhsEquation4, label = destState.toString())
    }

    private fun getEquation5(): EquationRow {
        val rhsEquation5 = 1.0
        val coefficients = mutableListOfZeros(indexMapping.variableCount)

        indexMapping.coefficientIndexByStateAction.forEach { (stateAction, index) ->
            coefficients[index] = 1.0
        }

        return EquationRow(
            coefficients = coefficients,
            rhs = rhsEquation5
        )
    }

    private fun populatePossibleActions() {
        check(possibleActionsByState.isEmpty())

        for (state in allStates) {
            val actions = userEquipmentStateManager.getPossibleActions(state)
            possibleActionsByState[state] = actions
        }
    }

    fun equationMapping(equationRow: EquationRow): Map<StateAction, Double> {
        return equationRow.coefficients.mapIndexed { index, d -> indexMapping.stateActionByCoefficientIndex[index]!! to d }
            .toMap()
    }

    fun createOffloadingLinearProgram(): OffloadingLinearProgram {
        allStates = userEquipmentStateManager.allStates()
        indexMapping = createIndexMapping()
        discreteTimeMarkovChain = dtmcCreator.create()
        itCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)
        populatePossibleActions()
        val equations: MutableList<EquationRow> = mutableListOf()

        with(equations) {
            add(getObjectiveEquation())
            add(getEquation2())
            addAll(getEquations3())
            addAll(getEquations4())
            add(getEquation5())
            // addAll(getEquations6())
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
            if (!userEquipmentStateManager.isStatePossible(state)) {
                continue
            }
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
        allStates = userEquipmentStateManager.allStates()
        indexMapping = createIndexMapping()
        // discreteTimeMarkovChain = dtmcCreator.create()
        // itCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)
        populatePossibleActions()
        val equations: MutableList<EquationRow?> = mutableListOf()

        with(equations) {
            add(getObjectiveEquation())
            add(getEquation2())
            addAll(getEquations3())
            repeat(allStates.size) {
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