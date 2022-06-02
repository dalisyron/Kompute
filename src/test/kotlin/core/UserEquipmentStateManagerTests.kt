package core

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import core.policy.Action
import core.symbol.ParameterSymbol
import core.symbol.Symbol
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withUserEquipmentStateConfig
import core.ue.UserEquipmentStateConfig
import org.junit.Test
import simulation.app.Mock
import core.ue.UserEquipmentState
import stochastic.dtmc.transition.Transition

class UserEquipmentStateManagerTests {

    fun getSystemConfig(userEquipmentStateConfig: UserEquipmentStateConfig): OffloadingSystemConfig {
        val systemConfig = Mock.configFromLiyu().withUserEquipmentStateConfig(
            UserEquipmentStateConfig.singleQueue(
                taskQueueCapacity = 1000, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 17
            )
        )
        return systemConfig
    }

    @Test
    fun testGetEdges() {
        val creator = UserEquipmentStateManager(
            StateManagerConfig(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 1000, // set to some big number,
                    tuNumberOfPackets = 1,
                    cpuNumberOfSections = 17
                ),
                listOf(StateManagerConfig.Limitation.None)
            )
        )

        val edges = creator.getEdgesForState(UserEquipmentState.singleQueue(1, 0, 0))

        Truth.assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState.singleQueue(0, 1, 0),
                    UserEquipmentState.singleQueue(0, 0, 0),
                    UserEquipmentState.singleQueue(0, 0, 1),
                    UserEquipmentState.singleQueue(1, 0, 0), // No-Op
                    UserEquipmentState.singleQueue(1, 0, 0), // No-Op
                    UserEquipmentState.singleQueue(1, 1, 0),
                    UserEquipmentState.singleQueue(1, 0, 1),
                    UserEquipmentState.singleQueue(2, 0, 0), // No-Op
                )
            )
    }

    @Test
    fun testGetEdges2() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig.singleQueue(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 5, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                ),
                limitation = StateManagerConfig.Limitation.None
            )
        )

        val edges = stateManager.getUniqueEdgesForState(UserEquipmentState.singleQueue(4, 2, 0))

        Truth.assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState.singleQueue(4, 2, 0),
                    UserEquipmentState.singleQueue(3, 2, 1),
                    UserEquipmentState.singleQueue(4, 3, 0),
                    UserEquipmentState.singleQueue(3, 3, 1),
                    UserEquipmentState.singleQueue(5, 2, 0),
                    UserEquipmentState.singleQueue(4, 2, 1),
                    UserEquipmentState.singleQueue(5, 3, 0),
                    UserEquipmentState.singleQueue(4, 3, 1),
                )
            )
    }

    @Test
    fun testGetEdges3() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig.singleQueue(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 4, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                ),
                StateManagerConfig.Limitation.None
            )
        )

        val edges = stateManager.getUniqueEdgesForState(UserEquipmentState.singleQueue(4, 2, 0))

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState.singleQueue(4, 2, 0),
                    UserEquipmentState.singleQueue(3, 2, 1),
                    UserEquipmentState.singleQueue(4, 3, 0),
                    UserEquipmentState.singleQueue(3, 3, 1),
                    UserEquipmentState.singleQueue(4, 2, 1),
                    UserEquipmentState.singleQueue(4, 3, 1),
                )
            )
    }

    @Test
    fun testGetEdges4() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig.singleQueue(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                ),
                limitation = StateManagerConfig.Limitation.None
            )
        )

        val edges = stateManager.getUniqueEdgesForState(UserEquipmentState.singleQueue(4, 0, 0))

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState.singleQueue(4, 0, 0),
                    UserEquipmentState.singleQueue(3, 0, 1),
                    UserEquipmentState.singleQueue(3, 1, 0),
                    UserEquipmentState.singleQueue(3, 2, 0),
                    UserEquipmentState.singleQueue(2, 1, 1),
                    UserEquipmentState.singleQueue(2, 2, 1),
                    UserEquipmentState.singleQueue(5, 0, 0),
                    UserEquipmentState.singleQueue(4, 0, 1),
                    UserEquipmentState.singleQueue(4, 1, 0),
                    UserEquipmentState.singleQueue(4, 2, 0),
                    UserEquipmentState.singleQueue(3, 1, 1),
                    UserEquipmentState.singleQueue(3, 2, 1),
                )
            )
    }

    @Test
    fun testPossibleActions1() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig.singleQueue(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                ),
                limitation = StateManagerConfig.Limitation.None
            )
        )

        val possibleActions = stateManager.getPossibleActions(
            UserEquipmentState.singleQueue(4, 3, 2)
        )

        assertThat(possibleActions)
            .containsExactly(Action.NoOperation)
    }

    @Test
    fun testGetEdges6() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig.singleQueue(
                UserEquipmentStateConfig.singleQueue(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                ),
                limitation = StateManagerConfig.Limitation.None
            )
        )

        val edges = stateManager.getUniqueEdgesForState(UserEquipmentState.singleQueue(4, 3, 2))

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState.singleQueue(4, 3, 3),
                    UserEquipmentState.singleQueue(4, 4, 3),
                    UserEquipmentState.singleQueue(5, 3, 3),
                    UserEquipmentState.singleQueue(5, 4, 3),
                )
            )
    }

    @Test
    fun testTransitionsSimple() {
        val tester = TransitionTester(
            systemConfig = Mock.simpleConfig(),
            sourceState = UserEquipmentState.singleQueue(2, 0, 0),
            action = Action.AddToCPU(0),
            expectedTransitions = mapOf(
                Pair(
                    UserEquipmentState.singleQueue(2, 0, 0),
                    UserEquipmentState.singleQueue(1, 0, 1)
                ) to listOf(
                    listOf(
                        ParameterSymbol.AlphaC(0), Action.AddToCPU(0))
                ),
                Pair(
                    UserEquipmentState.singleQueue(2, 0, 0),
                    UserEquipmentState.singleQueue(2, 0, 1)
                ) to listOf(
                    listOf(ParameterSymbol.Alpha(0), Action.AddToCPU(0))
                )
            )
        )

        tester.runTest()
    }

    class TransitionTester(
        private val systemConfig: OffloadingSystemConfig,
        private val sourceState: UserEquipmentState,
        private val action: Action,
        private val expectedTransitions: Map<Pair<UserEquipmentState, UserEquipmentState>, List<List<Symbol>>>,
    ) {

        fun runTest() {
            val stateManager = UserEquipmentStateManager.fromSystemConfig(systemConfig)

            val actualTransitions = stateManager.getTransitionsForAction(
                state = sourceState,
                action = action
            )

            assertThat(actualTransitions.map { it.source to it.dest })
                .containsExactlyElementsIn(expectedTransitions.keys)

            for (transition in actualTransitions) {
                val expectedTransitionSymbols = expectedTransitions[transition.source to transition.dest]!!
                assertListOfSymbolsEqual(expectedTransitionSymbols, transition.transitionSymbols)
            }
        }

        private fun assertListOfSymbolsEqual(expectedList: List<List<Symbol>>, actualList: List<List<Symbol>>) {
            assertThat(actualList)
                .hasSize(expectedList.size)

            actualList.forEachIndexed { index, symbols ->
                assertThat(symbols)
                    .containsExactlyElementsIn(expectedList[index])
            }
        }
    }
}