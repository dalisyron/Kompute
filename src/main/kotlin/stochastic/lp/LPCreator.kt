package stochastic.lp

import dtmc.symbol.ParameterSymbol
import dtmc.symbol.Symbol
import policy.Action
import stochastic.dtmc.DTMCCreator
import stochastic.dtmc.DiscreteTimeMarkovChain
import stochastic.dtmc.IndependentTransitionCalculator
import stochastic.dtmc.SymbolFraction
import ue.OffloadingSystemConfig
import ue.UserEquipmentState
import ue.UserEquipmentStateConfig.Companion.allStates
import kotlin.math.pow

data class Index(
    val state: UserEquipmentState,
    val action: Action
)

class LPCreator(
    val config: OffloadingSystemConfig
) {
    private val symbolMapping: Map<Symbol, Double> = run {
        mapOf(
            ParameterSymbol.Beta to config.beta,
            ParameterSymbol.BetaC to 1.0 - config.beta,
            ParameterSymbol.Alpha to config.alpha,
            ParameterSymbol.AlphaC to 1.0 - config.alpha
        )
    }

    val chain: DiscreteTimeMarkovChain? = null
    val itCalculator = IndependentTransitionCalculator(config.stateConfig)

    val tTx: Double by lazy {
        var expectedSingleDelay = 0.0
        val beta = config.beta
        val numberOfPackets = config.tuNumberOfPackets
        for (j in 1..1000) { // in theory, infinity is used instead of 1000. But 1000 is precise enough for practice
            expectedSingleDelay += j * (1.0 - beta).pow(j - 1) * beta
        }
        return@lazy numberOfPackets * expectedSingleDelay
    }

    val tCloud: Double by lazy {
        val tRx = config.tRx
        val nCloud = config.nCloud
        return@lazy tRx + tTx + nCloud
    }

    val allActions = listOf(
        Action.NoOperation,
        Action.AddToCPU,
        Action.AddToTransmissionUnit,
        Action.AddToBothUnits
    )

    val actionMapping = mapOf(
        Action.NoOperation to 0,
        Action.AddToCPU to 1,
        Action.AddToTransmissionUnit to 2,
        Action.AddToBothUnits to 3
    )

    val cObjective = mutableMapOf<Index, Double>()
    val rhsObjective: Double = rhsObjective()

    val cEquation2 = mutableMapOf<Index, Double>()
    val rhsEquation2: Double = config.pMax

    val cEquation3 = mutableMapOf<Index, Double>()
    val rhsEquation3: Double = 0.0

    val cEquation4 = mutableMapOf<UserEquipmentState, MutableMap<Index, Double>>()
    val rhsEquation4: Double = 0.0

    val cEquation5 = mutableMapOf<Index, Double>()
    val rhsEquation5 = 1.0

    fun setCoefficientsForObjective() {
        config.stateConfig.allStates().forEach { state ->
            allActions.forEach { action ->
                cObjective[Index(state, action)] =
                    state.taskQueueLength / config.alpha
            }
        }
    }

    private fun rhsObjective(): Double {
        val eta: Double = config.eta
        val nLocal: Int = config.nLocal
        return -(eta * nLocal + (1 - eta) * tCloud)
    }

    fun setCoefficientsForEquation2() {
        val pLoc = config.pLoc
        val pTx = config.pTx
        val beta = config.beta
        config.stateConfig.allStates().forEach { state ->
            allActions.forEach { action ->
                val index = Index(state, action)
                cEquation2[index] = 0.0
                val isActionPossible = DTMCCreator.getPossibleActions(state).contains(action)

                if (state.tuState > 0 || (action in listOf(
                        Action.AddToTransmissionUnit,
                        Action.AddToBothUnits
                    ) && isActionPossible)
                ) {
                    cEquation2[index] = cEquation2[index]!! + beta * pTx
                }

                if (state.cpuState > 0 || (action in listOf(
                        Action.AddToCPU,
                        Action.AddToBothUnits
                    ) && isActionPossible)
                ) {
                    cEquation2[index] = cEquation2[index]!! + pLoc
                }
            }
        }
    }

    fun setCoefficientsForEquation3() {
        config.stateConfig.allStates().forEach { state ->
            allActions.forEach { action ->
                val index = Index(state, action)
                cEquation3[index] = 0.0
                val possibleActions = DTMCCreator.getPossibleActions(state)

                if (possibleActions.contains(Action.AddToCPU)) {
                    cEquation3[index] = cEquation3[index]!! + (1.0 - config.eta)
                }

                if (possibleActions.contains(Action.AddToTransmissionUnit)) {
                    cEquation3[index] = cEquation3[index]!! - config.eta
                }

                if (possibleActions.contains(Action.AddToBothUnits)) {
                    cEquation3[index] = cEquation3[index]!! + (1.0 - 2 * config.eta)
                }
            }
        }
    }

    fun setCoefficientsForEquation4() {
        config.stateConfig.allStates().forEach { source ->
            cEquation4[source] = mutableMapOf()
            config.stateConfig.allStates().forEach { dest ->
                allActions.forEach { action ->
                    val independentTransitionValue: Double =
                        itCalculator.getIndependentTransitionFraction(source, dest, action)
                            .resolveByMapping(symbolMapping)

                    if (source == dest) {
                        cEquation4[source]!![Index(dest, action)] = independentTransitionValue - 1
                    } else {
                        cEquation4[source]!![Index(dest, action)] = independentTransitionValue
                    }
                }
            }
        }
    }

    fun setCoefficientsForEquation5() {
        config.stateConfig.allStates().forEach { state ->
            allActions.forEach { action ->
                val index = Index(state, action)
                cEquation5[index] = 1.0
            }
        }
    }
}

fun SymbolFraction.resolveByMapping(symbolMapping: Map<Symbol, Double>): Double {
    if (top == null || bottom == null) {
        return 0.0
    }

    return top.map { it.resolveByMapping(symbolMapping) }.reduce { acc, d -> acc + d } /
            bottom.map { it.resolveByMapping(symbolMapping) }.reduce { acc, d -> acc + d }
}

fun List<Symbol>.resolveByMapping(symbolMapping: Map<Symbol, Double>): Double {
    return this.map { symbolMapping[it]!! }.reduce { acc, d -> acc * d }
}