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
import kotlin.system.exitProcess

data class Index(
    val state: UserEquipmentState,
    val action: Action
)

data class OffloadingLinearProgram(
    val cObjective: Map<Index, Double>,
    val rhsObjective: Double,
    val cEquation2: Map<Index, Double>,
    val rhsEquation2: Double,
    val cEquation3: Map<Index, Double>,
    val rhsEquation3: Double,
    val cEquation4: Map<UserEquipmentState, Map<Index, Double>>,
    val rhsEquation4: Double,
    val cEquation5: Map<Index, Double>,
    val rhsEquation5: Double,
    val config: OffloadingSystemConfig
) : StandardLinearProgram {

    val variableCount =
        (config.taskQueueCapacity + 1) * (config.tuNumberOfPackets + 1) * (config.cpuNumberOfSections) * config.actionCount

    override val rows: List<EquationRow> by lazy {
        getStandardForm()
    }

    private fun index(state: UserEquipmentState, action: Action): Int {
        val (x, y, z) = state
        var idx = 0
        idx += x * (config.tuNumberOfPackets + 1) * (config.cpuNumberOfSections) * config.actionCount
        idx += y * (config.cpuNumberOfSections) * config.actionCount
        idx += z * config.actionCount
        idx += action.order

        return idx
    }

    private fun coefficientsToList(map: Map<Index, Double>): List<Double> {
        check(map.size == variableCount)

        return map.mapKeys { (key: Index, _) -> index(key.state, key.action) }.toList().sortedBy { pair ->
            pair.first
        }.map { it.second }
    }

    private fun getStandardForm(): List<EquationRow> {
        val rows = mutableListOf<EquationRow>()

        rows.add(
            EquationRow(
                coefficients = coefficientsToList(cObjective),
                rhs = rhsObjective,
                type = EquationRow.Type.Objective
            )
        )

        rows.add(
            EquationRow(
                coefficients = coefficientsToList(cEquation2),
                rhs = rhsEquation2,
                type = EquationRow.Type.LessThan
            )
        )

        rows.add(
            EquationRow(
                coefficients = coefficientsToList(cEquation3),
                rhs = rhsEquation3,
                type = EquationRow.Type.Equality
            )
        )

        config.stateConfig.allStates().forEach { state ->
            rows.add(
                EquationRow(
                    coefficients = coefficientsToList(cEquation4[state]!!),
                    rhs = rhsEquation4,
                    type = EquationRow.Type.Equality
                )
            )
        }

        rows.add(
            EquationRow(
                coefficients = coefficientsToList(cEquation5),
                rhs = rhsEquation5,
                type = EquationRow.Type.Equality
            )
        )

        return rows
    }
}

class OffloadingLPCreator(
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

    private fun getCoefficientsForObjective(): Map<Index, Double> {
        val cObjective: MutableMap<Index, Double> = mutableMapOf()
        config.stateConfig.allStates().forEach { state ->
            config.allActions.forEach { action ->
                cObjective[Index(state, action)] =
                    state.taskQueueLength / config.alpha
            }
        }
        return cObjective
    }

    private fun rhsObjective(): Double {
        val eta: Double = config.eta
        val nLocal: Int = config.nLocal
        return -(eta * nLocal + (1 - eta) * tCloud)
    }

    private fun getCoefficientsForEquation2(): Map<Index, Double> {
        val cEquation2: MutableMap<Index, Double> = mutableMapOf()
        val pLoc = config.pLoc
        val pTx = config.pTx
        val beta = config.beta
        config.stateConfig.allStates().forEach { state ->
            config.allActions.map { action ->
                val index = Index(state, action)
                cEquation2[index] = 0.0
                val isActionPossible = DTMCCreator.getPossibleActions(state).contains(action)
                if (!isActionPossible) return@map

                if (state.tuState > 0 || (action in listOf(
                        Action.AddToTransmissionUnit,
                        Action.AddToBothUnits
                    ))
                ) {
                    cEquation2[index] = cEquation2[index]!! + beta * pTx
                }

                if (state.cpuState > 0 || (action in listOf(
                        Action.AddToCPU,
                        Action.AddToBothUnits
                    ))
                ) {
                    cEquation2[index] = cEquation2[index]!! + pLoc
                }
            }
        }
        return cEquation2
    }

    private fun getCoefficientsForEquation3(): Map<Index, Double> {
        val cEquation3: MutableMap<Index, Double> = mutableMapOf()
        config.stateConfig.allStates().forEach { state ->
            config.allActions.forEach { action ->
                val index = Index(state, action)
                val possibleActions = DTMCCreator.getPossibleActions(state)

                if (possibleActions.contains(action)) {
                    cEquation3[index] = when (action) {
                        Action.AddToCPU -> {
                             (1.0 - config.eta)
                        }
                        Action.AddToBothUnits -> {
                            (1.0 - 2 * config.eta)
                        }
                        Action.AddToTransmissionUnit -> {
                            (-config.eta)
                        }
                        Action.NoOperation -> {
                            0.0
                        }
                    }
                } else {
                    cEquation3[index] = 0.0
                }
            }
        }
        return cEquation3
    }

    private fun getCoefficientsForEquation4(): Map<UserEquipmentState, Map<Index, Double>> {
        val cEquation4: MutableMap<UserEquipmentState, MutableMap<Index, Double>> = mutableMapOf()
        config.stateConfig.allStates().forEach { source ->
            cEquation4[source] = mutableMapOf()
            config.stateConfig.allStates().forEach { dest ->
                config.allActions.forEach { action ->
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
        return cEquation4
    }

    private fun getCoefficientsForEquation5(): Map<Index, Double> {
        val cEquation5: MutableMap<Index, Double> = mutableMapOf()
        config.stateConfig.allStates().forEach { state ->
            config.allActions.forEach { action ->
                val index = Index(state, action)
                cEquation5[index] = 1.0
            }
        }
        return cEquation5
    }

    fun createLP(): OffloadingLinearProgram {
        val cObjective = getCoefficientsForObjective()
        val cEquation2 = getCoefficientsForEquation2()
        val cEquation3 = getCoefficientsForEquation3()
        val cEquation4 = getCoefficientsForEquation4()
        val cEquation5 = getCoefficientsForEquation5()
        val rhsObjective: Double = rhsObjective()
        val rhsEquation2: Double = config.pMax
        val rhsEquation3: Double = 0.0
        val rhsEquation4: Double = 0.0
        val rhsEquation5 = 1.0

        return OffloadingLinearProgram(
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
            config = config
        )
    }
}

fun SymbolFraction.resolveByMapping(symbolMapping: Map<Symbol, Double>): Double {
    if (top == null || bottom == null) {
        return 0.0
    }
    if (top.size == 1 && bottom.size == 1 && top[0].isEmpty() && bottom[0].isEmpty()) { // Going from (Q, 0, 0) to (Q, 0, 0) with No Operation
        return 1.0
    }
    check(top.all { it.isNotEmpty() })
    check(bottom.all { it.isNotEmpty() })

    return top.map { it.resolveByMapping(symbolMapping) }.reduce { acc, d -> acc + d } /
            bottom.map { it.resolveByMapping(symbolMapping) }.reduce { acc, d -> acc + d }
}

fun List<Symbol>.resolveByMapping(symbolMapping: Map<Symbol, Double>): Double {
    return this.map { symbolMapping[it]!! }.reduce { acc, d -> acc * d }
}