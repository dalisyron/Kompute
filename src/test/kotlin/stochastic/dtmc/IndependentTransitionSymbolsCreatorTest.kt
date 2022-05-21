package stochastic.dtmc

import com.google.common.truth.Truth.assertThat
import dtmc.symbol.ParameterSymbol
import dtmc.symbol.Symbol
import stochastic.lp.resolveByMapping
import org.junit.jupiter.api.Test
import policy.Action
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import kotlin.math.abs

class IndependentTransitionCalculatorTest {

    val sampleStateConfig1 = UserEquipmentStateConfig(
        taskQueueCapacity = 5,
        tuNumberOfPackets = 4,
        cpuNumberOfSections = 3
    )

    @Test
    fun testcase1() {
        val transitionCalculator = IndependentTransitionCalculator(sampleStateConfig1)

        val fraction = transitionCalculator.getIndependentTransitionFraction(
            UserEquipmentState(2, 0, 0),
            UserEquipmentState(1, 1, 0),
            Action.AddToTransmissionUnit
        )

        assertThat(fraction.top)
            .isNotNull()

        assertThat(fraction.bottom)
            .isNotNull()

        assertThat(fraction.top!!)
            .hasSize(1)

        assertThat(fraction.top!![0])
            .containsExactly(ParameterSymbol.BetaC, ParameterSymbol.AlphaC)

        assertThat(fraction.bottom!!)
            .hasSize(4)

        print(fraction)
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.Beta, ParameterSymbol.Alpha).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.Beta, ParameterSymbol.AlphaC).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.BetaC, ParameterSymbol.Alpha).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.BetaC, ParameterSymbol.AlphaC).makeUnique())
    }

    @Test
    fun testDoubleLabel() {
        val config = UserEquipmentStateConfig(
            taskQueueCapacity = 10, // set to some big number,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        )
        val itCalculator = IndependentTransitionCalculator(config)

        val fraction = itCalculator.getIndependentTransitionFraction(
            source = UserEquipmentState(1, 0, 0),
            dest = UserEquipmentState(1, 0, 0),
            action = Action.AddToTransmissionUnit
        )

        requireNotNull(fraction)
        requireNotNull(fraction.top)
        requireNotNull(fraction.bottom)

        assertThat(fraction.top!!)
            .hasSize(1)
        assertThat(fraction.top!!.map { it.makeUnique() })
            .containsExactly(listOf(ParameterSymbol.Beta, ParameterSymbol.Alpha).makeUnique())

        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.Beta, ParameterSymbol.Alpha).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.Beta, ParameterSymbol.AlphaC).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.BetaC, ParameterSymbol.Alpha).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.BetaC, ParameterSymbol.AlphaC).makeUnique())
    }

    @Test
    fun testDoubleLabelFraction() {
        val config = UserEquipmentStateConfig(
            taskQueueCapacity = 10, // set to some big number,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        )
        val itCalculator = IndependentTransitionCalculator(config)

        val fraction = itCalculator.getIndependentTransitionFraction(
            source = UserEquipmentState(1, 0, 0),
            dest = UserEquipmentState(1, 0, 0),
            action = Action.AddToTransmissionUnit
        )

        requireNotNull(fraction)
        requireNotNull(fraction.top)
        requireNotNull(fraction.bottom)

        assertThat(fraction.top!!)
            .hasSize(1)
        assertThat(fraction.top!!.map { it.makeUnique() })
            .containsExactly(listOf(ParameterSymbol.Beta, ParameterSymbol.Alpha).makeUnique())

        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.Beta, ParameterSymbol.Alpha).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.Beta, ParameterSymbol.AlphaC).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.BetaC, ParameterSymbol.Alpha).makeUnique())
        assertThat(fraction.bottom!!.map { it.makeUnique() })
            .contains(listOf(ParameterSymbol.BetaC, ParameterSymbol.AlphaC).makeUnique())

        val mapping = mapOf<Symbol, Double>(
            ParameterSymbol.Beta to 0.4,
            ParameterSymbol.BetaC to 0.6,
            ParameterSymbol.Alpha to 0.3,
            ParameterSymbol.AlphaC to 0.7
        )

        val fractionValue = fraction.resolveByMapping(mapping)

        assertThat(abs(fractionValue - 0.12))
            .isWithin(1e4)
    }

    private fun List<Symbol>.makeUnique() = sortedBy { it.toString() }
}