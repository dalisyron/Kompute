import com.google.common.truth.MultimapSubject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dtmc.DTMCCreator
import dtmc.transition.Edge
import dtmc.transition.ParameterSymbol
import dtmc.transition.Symbol
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import policy.Action
import ue.UserEquipmentState
import ue.UserEquipmentStateConfig

internal class DTMCCreatorTests {
    private val sampleStateConfig1 = UserEquipmentStateConfig(
        taskQueueCapacity = 5,
        tuNumberOfPackets = 4,
        cpuNumberOfSections = 3
    )
    private var creator: DTMCCreator = DTMCCreator(sampleStateConfig1)


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
}