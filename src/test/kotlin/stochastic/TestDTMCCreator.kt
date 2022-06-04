package stochastic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import core.StateManagerConfig
import stochastic.dtmc.DTMCCreator
import stochastic.dtmc.transition.Edge
import core.symbol.ParameterSymbol
import core.symbol.Symbol
import org.junit.jupiter.api.Test
import core.policy.Action
import core.ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig

internal class TestDTMCCreator {
    private val sampleStateConfig1 = UserEquipmentStateConfig.singleQueue(
        taskQueueCapacity = 5,
        tuNumberOfPackets = 4,
        cpuNumberOfSections = 3
    )
    private var creator: DTMCCreator = DTMCCreator(StateManagerConfig.singleQueue(sampleStateConfig1, StateManagerConfig.Limitation.None))


    @Test
    fun testSingleTaskIdleComponentsDestinations() {
        val chain = creator.create()

        val edges = chain.adjacencyList[UserEquipmentState.singleQueue(1, 0, 0)]!!

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState.singleQueue(0, 1, 0),
                    UserEquipmentState.singleQueue(0, 0, 1),
                    UserEquipmentState.singleQueue(0, 2, 0),
                    UserEquipmentState.singleQueue(1, 0, 0), // No-Op
                    UserEquipmentState.singleQueue(1, 1, 0),
                    UserEquipmentState.singleQueue(1, 0, 1),
                    UserEquipmentState.singleQueue(1, 2, 0),
                    UserEquipmentState.singleQueue(2, 0, 0), // No-Op
                )
            )
    }

    @Test
    fun testSingleTaskForSingleTU() {
        val config = UserEquipmentStateConfig.singleQueue(
            taskQueueCapacity = 1000, // set to some big number,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        )
        val creatorTemp = DTMCCreator(StateManagerConfig.singleQueue(config))
        val chain = creatorTemp.create()

        val edges = chain.adjacencyList[UserEquipmentState.singleQueue(1, 0, 0)]!!

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState.singleQueue(0, 1, 0),
                    UserEquipmentState.singleQueue(0, 0, 0),
                    UserEquipmentState.singleQueue(0, 0, 1),
                    UserEquipmentState.singleQueue(1, 0, 0),
                    UserEquipmentState.singleQueue(1, 1, 0),
                    UserEquipmentState.singleQueue(1, 0, 1),
                    UserEquipmentState.singleQueue(2, 0, 0),
                )
            )
    }

    @Test
    fun testSingleTaskIdleComponentsSymbols() {
        val chain = creator.create()

        val edges = chain.adjacencyList[UserEquipmentState.singleQueue(1, 0, 0)]!!

        fun assertSymbolsEqualForState(state: UserEquipmentState, list: List<Symbol>) {

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.size)
                .isEqualTo(1)

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.flatten())
                .containsExactlyElementsIn(list)
        }

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(0, 1, 0),
            listOf(Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.AlphaC.singleQueue(), ParameterSymbol.BetaC)
        )

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(0, 0, 1),
            listOf(Action.AddToCPU.singleQueue(), ParameterSymbol.AlphaC.singleQueue())
        )

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(0, 2, 0),
            listOf(Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.AlphaC.singleQueue(), ParameterSymbol.Beta)
        )

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(1, 0, 0),
            listOf(Action.NoOperation, ParameterSymbol.AlphaC.singleQueue())
        )


        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(1, 1, 0),
            listOf(Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.Alpha.singleQueue(), ParameterSymbol.BetaC)
        )

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(1, 0, 1),
            listOf(Action.AddToCPU.singleQueue(), ParameterSymbol.Alpha.singleQueue())
        )

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(1, 2, 0),
            listOf(Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.Alpha.singleQueue(), ParameterSymbol.Beta)
        )

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(2, 0, 0),
            listOf(Action.NoOperation, ParameterSymbol.Alpha.singleQueue())
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
            StateManagerConfig.singleQueue(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val chain = creatorTemp.create()

        val edges = chain.adjacencyList[UserEquipmentState.singleQueue(4, 0, 0)]!!

        fun assertSymbolsEqualForState(state: UserEquipmentState, list: List<Symbol>) {

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.size)
                .isEqualTo(1)

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.flatten())
                .containsExactlyElementsIn(list)
        }


        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(4, 0, 0),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.NoOperation)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(3, 0, 1),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.AddToCPU.singleQueue())
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(3, 1, 0),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(3, 2, 0),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(2, 1, 1),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.AddToBothUnits.singleQueue(), ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(2, 2, 1),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.AddToBothUnits.singleQueue(), ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(5, 0, 0),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.NoOperation)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(4, 0, 1),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.AddToCPU.singleQueue())
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(4, 1, 0),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(4, 2, 0),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(3, 1, 1),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.AddToBothUnits.singleQueue(), ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(3, 2, 1),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.AddToBothUnits.singleQueue(), ParameterSymbol.Beta)
        )
    }

    @Test
    fun testSymbols5() {
        val creatorTemp = DTMCCreator(
            StateManagerConfig.singleQueue(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val chain = creatorTemp.create()

        val edges = chain.adjacencyList[UserEquipmentState.singleQueue(1, 0, 0)]!!

        fun assertSymbolsEqualForState(state: UserEquipmentState, list: List<Symbol>) {

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.size)
                .isEqualTo(1)

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.flatten())
                .containsExactlyElementsIn(list)
        }

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(0, 1, 0),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(0, 0, 1),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.AddToCPU.singleQueue())
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(0, 2, 0),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(1, 0, 0), // No-Op
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.NoOperation)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(1, 1, 0),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(1, 0, 1),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.AddToCPU.singleQueue())
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(1, 2, 0),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.AddToTransmissionUnit.singleQueue(), ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(2, 0, 0), // No-Op
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.NoOperation)
        )
    }


    @Test
    fun testSymbols() {
        val creatorTemp = DTMCCreator(
            StateManagerConfig.singleQueue(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val chain = creatorTemp.create()

        val edges = chain.adjacencyList[UserEquipmentState.singleQueue(4, 3, 2)]!!

        fun assertSymbolsEqualForState(state: UserEquipmentState, list: List<Symbol>) {

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.size)
                .isEqualTo(1)

            assertThat(edges.find { it.dest == state }!!.edgeSymbols.flatten())
                .containsExactlyElementsIn(list)
        }

        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(4, 3, 3),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.NoOperation, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(4, 4, 3),
            listOf(ParameterSymbol.AlphaC.singleQueue(), Action.NoOperation, ParameterSymbol.Beta)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(5, 3, 3),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.NoOperation, ParameterSymbol.BetaC)
        )
        assertSymbolsEqualForState(
            UserEquipmentState.singleQueue(5, 4, 3),
            listOf(ParameterSymbol.Alpha.singleQueue(), Action.NoOperation, ParameterSymbol.Beta)
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