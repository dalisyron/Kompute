package stochastic.dtmc

import core.UserEquipmentStateManager
import core.ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import core.StateManagerConfig
import core.policy.Action
import core.symbol.Symbol
import stochastic.lp.OffloadingLPCreator

data class IndexStateStateAction(
    val source: UserEquipmentState,
    val dest: UserEquipmentState,
    val action: Action
)

data class DiscreteTimeMarkovChain(
    val stateConfig: UserEquipmentStateConfig,
    val startState: UserEquipmentState,
    val transitionSymbolsByIndex: Map<IndexStateStateAction, Double>
)

class DTMCCreator(
    private val stateManagerConfig: StateManagerConfig,
    private val symbolMapping: Map<Symbol, Double>
) {
    private val stateManager: UserEquipmentStateManager = UserEquipmentStateManager(stateManagerConfig)

    fun create(): DiscreteTimeMarkovChain {
        val allStates = stateManager.allStates()
        val transitionSymbolsByIndexTemp: MutableMap<IndexStateStateAction, Double> = mutableMapOf()

        for (source in allStates) {
            for (action in stateManager.getPossibleActions(source)) {
                val transitions = stateManager.getTransitionsForAction(source, action)
                for (transition in transitions) {
                    require(transition.transitionSymbols.size == 1) {
                        """I couldn't find a scenario where transitionSymbols has size greater than 1.
                            | If you have encountered one, revert to previous commit :)""".trimMargin()
                    }
                    val itValue = getIndependentTransitionFraction(transition.transitionSymbols[0], action)
                    transitionSymbolsByIndexTemp[IndexStateStateAction(source, transition.dest, action)] = itValue
                }
            }
        }

        return DiscreteTimeMarkovChain(
            stateConfig = stateManagerConfig.userEquipmentStateConfig,
            startState = stateManager.getInitialState(),
            transitionSymbolsByIndex = transitionSymbolsByIndexTemp
        )
    }

    private fun getIndependentTransitionFraction(symbolList: List<Symbol>, action: Action): Double {
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
}
