package stochastic.lp

import core.mutableListOfZeros
import core.symbol.ParameterSymbol
import core.symbol.Symbol
import core.policy.Action
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentState
import core.UserEquipmentStateManager
import stochastic.dtmc.DTMCCreator
import stochastic.dtmc.DiscreteTimeMarkovChain
import stochastic.dtmc.IndexStateStateAction

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
    private val symbolMapping: Map<Symbol, Double> = createSymbolMapping()
    private val dtmcCreator: DTMCCreator = DTMCCreator(systemConfig.getStateManagerConfig(), symbolMapping)
    private val allStates: List<UserEquipmentState> = userEquipmentStateManager.allStates()

    private val possibleActionsByState: Map<UserEquipmentState, List<Action>> = populatePossibleActions()
    private val indexMapping: IndexMapping = createIndexMapping()
    private val discreteTimeMarkovChain: DiscreteTimeMarkovChain by lazy {
        dtmcCreator.create()
    }

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

    @Deprecated("Use V2")
    fun getEquations4(): MutableList<EquationRow> {
        val equations: MutableList<EquationRow> = mutableListOf()

        for (destState in allStates) {
            equations.add(getEquation4(destState))
        }

        return equations
    }

    private fun getIndependentTransitionFraction(
        symbolList: List<Symbol>,
        action: Action
    ): Double {
        var result = 0.0

        var product = 1.0
        var count = 0
        for (symbol in symbolList) {
            if (symbol != action) {
                product *= symbolMapping[symbol]!!
                count++
            }
        }
        check(count == symbolList.size - 1)
        result += product

        return result
    }

    fun getEquations4V2(): MutableList<EquationRow> {
        val equations: MutableList<EquationRow> = mutableListOf()
        val stateToIndex = allStates.mapIndexed { index, userEquipmentState -> userEquipmentState to index }.toMap()
        val coefficients = (1..allStates.size).map { mutableListOfZeros(indexMapping.variableCount) }

        for (source in allStates) {
            for (action in possibleActionsByState[source]!!) {
                val transitions = userEquipmentStateManager.getTransitionsForAction(source, action)
                for (transition in transitions) {
                    val stateAction = StateAction(source, action)
                    val coefficientIndex = indexMapping.coefficientIndexByStateAction[stateAction]!!
                    val idx = stateToIndex[transition.dest]!!
                    require(transition.transitionSymbols.size == 1)
                    coefficients[idx][coefficientIndex] =
                        getIndependentTransitionFraction(transition.transitionSymbols.first(), action)
                }
            }
        }

        indexMapping.coefficientIndexByStateAction.forEach { (stateAction, index) ->
            coefficients[stateToIndex[stateAction.state]!!][index] -= 1.0
        }

        for (i in allStates.indices) {
            equations.add(
                EquationRow(
                    coefficients = coefficients[i],
                    rhs = 0.0,
                    type = EquationRow.Type.Equality
                )
            )
        }

        return equations
    }

    @Deprecated("Use V2")
    private fun getEquation4(destState: UserEquipmentState): EquationRow {
        val rhsEquation4 = 0.0
        val coefficients = mutableListOf<Double>()
        for (i in 0 until indexMapping.variableCount) coefficients.add(0.0)

        for ((stateAction, index) in indexMapping.coefficientIndexByStateAction) {
            val (sourceState, action) = stateAction
            val independentTransitionValue =
                discreteTimeMarkovChain.transitionSymbolsByIndex[IndexStateStateAction(sourceState, destState, action)]
                    ?: 0.0

            coefficients[index] = if (stateAction.state == destState) {
                independentTransitionValue - 1
            } else {
                independentTransitionValue
            }
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

    private fun populatePossibleActions(): Map<UserEquipmentState, List<Action>> {
        val result: MutableMap<UserEquipmentState, List<Action>> = mutableMapOf()

        for (state in allStates) {
            val actions = userEquipmentStateManager.getPossibleActions(state)
            result[state] = actions
        }

        return result
    }

    fun equationMapping(equationRow: EquationRow): Map<StateAction, Double> {
        return equationRow.coefficients.mapIndexed { index, d -> indexMapping.stateActionByCoefficientIndex[index]!! to d }
            .toMap()
    }

    fun createOffloadingLinearProgram(): OffloadingLinearProgram {
        val equations: MutableList<EquationRow> = mutableListOf()

        with(equations) {
            add(getObjectiveEquation())
            add(getEquation2())
            addAll(getEquations3())
            addAll(getEquations4V2())
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
            for (action in possibleActionsByState[state]!!) {
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
        // discreteTimeMarkovChain = dtmcCreator.create()
        // itCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)
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