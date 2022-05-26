package stochastic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import core.StateManagerConfig
import stochastic.dtmc.DTMCCreator
import stochastic.dtmc.transition.Edge
import dtmc.symbol.ParameterSymbol
import dtmc.symbol.Symbol
import org.junit.Test
import policy.Action
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig

internal class DTMCCreatorTests {
    private val sampleStateConfig1 = UserEquipmentStateConfig(
        taskQueueCapacity = 5,
        tuNumberOfPackets = 4,
        cpuNumberOfSections = 3
    )
    private var creator: DTMCCreator = DTMCCreator(StateManagerConfig(sampleStateConfig1))


    @Test
    fun testSingleTaskIdleComponentsDestinations() {
        val chain = creator.create()

        val edges = chain.adjacencyList[UserEquipmentState(1, 0, 0)]!!

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState(0, 1, 0),
                    UserEquipmentState(0, 0, 1),
                    UserEquipmentState(0, 2, 0),
                    UserEquipmentState(1, 0, 0), // No-Op
                    UserEquipmentState(1, 1, 0),
                    UserEquipmentState(1, 0, 1),
                    UserEquipmentState(1, 2, 0),
                    UserEquipmentState(2, 0, 0), // No-Op
                )
            )
    }

    @Test
    fun testSingleTaskForSingleTU() {
        val config = UserEquipmentStateConfig(
            taskQueueCapacity = 1000, // set to some big number,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        )
        val creatorTemp = DTMCCreator(StateManagerConfig(config))
        val chain = creatorTemp.create()

        val edges = chain.adjacencyList[UserEquipmentState(1, 0, 0)]!!

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState(0, 1, 0),
                    UserEquipmentState(0, 0, 0),
                    UserEquipmentState(0, 0, 1),
                    UserEquipmentState(1, 0, 0),
                    UserEquipmentState(1, 1, 0),
                    UserEquipmentState(1, 0, 1),
                    UserEquipmentState(2, 0, 0),
                )
            )
    }

    @Test
    fun testSingleTaskIdleComponentsSymbols() {
        val chain = creator.create()

        val edges = chain.adjacencyList[UserEquipmentState(1, 0, 0)]!!

        fun assertSymbolsEqualForState(state: UserEquipmentState, list: List<Symbol>) {

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.size)
                .isEqualTo(1)

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.flatten())
                .containsExactlyElementsIn(list)
        }

        assertSymbolsEqualForState(
            UserEquipmentState(0, 1, 0),
            listOf(Action.AddToTransmissionUnit, ParameterSymbol.AlphaC, ParameterSymbol.BetaC)
        )

        assertSymbolsEqualForState(
            UserEquipmentState(0, 0, 1),
            listOf(Action.AddToCPU, ParameterSymbol.AlphaC)
        )

        assertSymbolsEqualForState(
            UserEquipmentState(0, 2, 0),
            listOf(Action.AddToTransmissionUnit, ParameterSymbol.AlphaC, ParameterSymbol.Beta)
        )

        assertSymbolsEqualForState(
            UserEquipmentState(1, 0, 0),
            listOf(Action.NoOperation, ParameterSymbol.AlphaC)
        )


        assertSymbolsEqualForState(
            UserEquipmentState(1, 1, 0),
            listOf(Action.AddToTransmissionUnit, ParameterSymbol.Alpha, ParameterSymbol.BetaC)
        )

        assertSymbolsEqualForState(
            UserEquipmentState(1, 0, 1),
            listOf(Action.AddToCPU, ParameterSymbol.Alpha)
        )

        assertSymbolsEqualForState(
            UserEquipmentState(1, 2, 0),
            listOf(Action.AddToTransmissionUnit, ParameterSymbol.Alpha, ParameterSymbol.Beta)
        )

        assertSymbolsEqualForState(
            UserEquipmentState(2, 0, 0),
            listOf(Action.NoOperation, ParameterSymbol.Alpha)
        )
    }

    @Test
    fun findDoubleEdge() {
        val chain = creator.create()

        chain.adjacencyList.forEach { state: UserEquipmentState, edges: List<Edge> ->
            edges.forEach { edge ->
                assertWithMessage("edge = $edge | actual symbols = ${edge} | source state = $state | dest state = ${edge.dest}\ntransitions=${chain.adjacencyList[state]!!}")
                    .that(edge.edgeSymbols)
                    .hasSize(1)
            }
        }
    }


    @Test
    fun testSymbols4() {
        val creatorTemp = DTMCCreator(
            StateManagerConfig(
                UserEquipmentStateConfig(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val chain = creatorTemp.create()

        val edges = chain.adjacencyList[UserEquipmentState(4, 0, 0)]!!

        fun assertSymbolsEqualForState(state: UserEquipmentState, list: List<Symbol>) {

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.size)
                .isEqualTo(1)

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.flatten())
                .containsExactlyElementsIn(list)
        }


        assertSymbolsEqualForState(
            UserEquipmentState(4, 0, 0),
            listOf(ParameterSymbol.AlphaC, Action.NoOperation)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(3, 0, 1),
            listOf(ParameterSymbol.AlphaC, Action.AddToCPU)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(3, 1, 0),
            listOf(ParameterSymbol.AlphaC, Action.AddToTransmissionUnit, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(3, 2, 0),
            listOf(ParameterSymbol.AlphaC, Action.AddToTransmissionUnit, ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(2, 1, 1),
            listOf(ParameterSymbol.AlphaC, Action.AddToBothUnits, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(2, 2, 1),
            listOf(ParameterSymbol.AlphaC, Action.AddToBothUnits, ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(5, 0, 0),
            listOf(ParameterSymbol.Alpha, Action.NoOperation)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(4, 0, 1),
            listOf(ParameterSymbol.Alpha, Action.AddToCPU)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(4, 1, 0),
            listOf(ParameterSymbol.Alpha, Action.AddToTransmissionUnit, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(4, 2, 0),
            listOf(ParameterSymbol.Alpha, Action.AddToTransmissionUnit, ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(3, 1, 1),
            listOf(ParameterSymbol.Alpha, Action.AddToBothUnits, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(3, 2, 1),
            listOf(ParameterSymbol.Alpha, Action.AddToBothUnits, ParameterSymbol.Beta)
        )
    }

    @Test
    fun testSymbols5() {
        val creatorTemp = DTMCCreator(
            StateManagerConfig(
                UserEquipmentStateConfig(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val chain = creatorTemp.create()

        val edges = chain.adjacencyList[UserEquipmentState(1, 0, 0)]!!

        fun assertSymbolsEqualForState(state: UserEquipmentState, list: List<Symbol>) {

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.size)
                .isEqualTo(1)

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.flatten())
                .containsExactlyElementsIn(list)
        }

        assertSymbolsEqualForState(
            UserEquipmentState(0, 1, 0),
            listOf(ParameterSymbol.AlphaC, Action.AddToTransmissionUnit, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(0, 0, 1),
            listOf(ParameterSymbol.AlphaC, Action.AddToCPU)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(0, 2, 0),
            listOf(ParameterSymbol.AlphaC, Action.AddToTransmissionUnit, ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(1, 0, 0), // No-Op
            listOf(ParameterSymbol.AlphaC, Action.NoOperation)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(1, 1, 0),
            listOf(ParameterSymbol.Alpha, Action.AddToTransmissionUnit, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(1, 0, 1),
            listOf(ParameterSymbol.Alpha, Action.AddToCPU)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(1, 2, 0),
            listOf(ParameterSymbol.Alpha, Action.AddToTransmissionUnit, ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(2, 0, 0), // No-Op
            listOf(ParameterSymbol.Alpha, Action.NoOperation)
        )
    }


    @Test
    fun testSymbols() {
        val creatorTemp = DTMCCreator(
            StateManagerConfig(
                UserEquipmentStateConfig(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val chain = creatorTemp.create()

        val edges = chain.adjacencyList[UserEquipmentState(4, 3, 2)]!!

        fun assertSymbolsEqualForState(state: UserEquipmentState, list: List<Symbol>) {

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.size)
                .isEqualTo(1)

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.flatten())
                .containsExactlyElementsIn(list)
        }

        assertSymbolsEqualForState(
            UserEquipmentState(4, 3, 3),
            listOf(ParameterSymbol.AlphaC, Action.NoOperation, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(4, 4, 3),
            listOf(ParameterSymbol.AlphaC, Action.NoOperation, ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(5, 3, 3),
            listOf(ParameterSymbol.Alpha, Action.NoOperation, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState(5, 4, 3),
            listOf(ParameterSymbol.Alpha, Action.NoOperation, ParameterSymbol.Beta)
        )
    }

    @Test
    fun findDoubleLabel() {
        val chain = creator.create()

        chain.adjacencyList.forEach { state: UserEquipmentState, edgeList: List<Edge> ->
            edgeList.map { it.edgeSymbols }.forEach {
                assertThat(it)
                    .hasSize(1)
            }
        }
    }
}