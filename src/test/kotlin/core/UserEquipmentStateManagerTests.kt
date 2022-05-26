package core

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withUserEquipmentStateConfig
import core.ue.UserEquipmentStateConfig
import org.junit.Test
import simulation.app.Mock
import ue.UserEquipmentState

class UserEquipmentStateManagerTests {

    fun getSystemConfig(userEquipmentStateConfig: UserEquipmentStateConfig): OffloadingSystemConfig {
        val systemConfig = Mock.configFromLiyu().withUserEquipmentStateConfig(
            UserEquipmentStateConfig(
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
                UserEquipmentStateConfig(
                    taskQueueCapacity = 1000, // set to some big number,
                    tuNumberOfPackets = 1,
                    cpuNumberOfSections = 17
                ),
                StateManagerConfig.Limitation.None
            )
        )

        val edges = creator.getEdgesForState(UserEquipmentState(1, 0, 0))

        Truth.assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState(0, 1, 0),
                    UserEquipmentState(0, 0, 0),
                    UserEquipmentState(0, 0, 1),
                    UserEquipmentState(1, 0, 0), // No-Op
                    UserEquipmentState(1, 0, 0), // No-Op
                    UserEquipmentState(1, 1, 0),
                    UserEquipmentState(1, 0, 1),
                    UserEquipmentState(2, 0, 0), // No-Op
                )
            )
    }

    @Test
    fun testGetEdges2() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig(
                UserEquipmentStateConfig(
                    taskQueueCapacity = 5, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val edges = stateManager.getUniqueEdgesForState(UserEquipmentState(4, 2, 0))

        Truth.assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState(4, 2, 0),
                    UserEquipmentState(3, 2, 1),
                    UserEquipmentState(4, 3, 0),
                    UserEquipmentState(3, 3, 1),
                    UserEquipmentState(5, 2, 0),
                    UserEquipmentState(4, 2, 1),
                    UserEquipmentState(5, 3, 0),
                    UserEquipmentState(4, 3, 1),
                )
            )
    }

    @Test
    fun testGetEdges3() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig(
                UserEquipmentStateConfig(
                    taskQueueCapacity = 4, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val edges = stateManager.getUniqueEdgesForState(UserEquipmentState(4, 2, 0))

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState(4, 2, 0),
                    UserEquipmentState(3, 2, 1),
                    UserEquipmentState(4, 3, 0),
                    UserEquipmentState(3, 3, 1),
                    UserEquipmentState(4, 2, 1),
                    UserEquipmentState(4, 3, 1),
                )
            )
    }

    @Test
    fun testGetEdges4() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig(
                UserEquipmentStateConfig(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val edges = stateManager.getUniqueEdgesForState(UserEquipmentState(4, 0, 0))

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState(4, 0, 0),
                    UserEquipmentState(3, 0, 1),
                    UserEquipmentState(3, 1, 0),
                    UserEquipmentState(3, 2, 0),
                    UserEquipmentState(2, 1, 1),
                    UserEquipmentState(2, 2, 1),
                    UserEquipmentState(5, 0, 0),
                    UserEquipmentState(4, 0, 1),
                    UserEquipmentState(4, 1, 0),
                    UserEquipmentState(4, 2, 0),
                    UserEquipmentState(3, 1, 1),
                    UserEquipmentState(3, 2, 1),
                )
            )
    }

    @Test
    fun testGetEdges6() {
        val stateManager = UserEquipmentStateManager(
            StateManagerConfig(
                UserEquipmentStateConfig(
                    taskQueueCapacity = 10, // set to some big number,
                    tuNumberOfPackets = 5,
                    cpuNumberOfSections = 8
                )
            )
        )

        val edges = stateManager.getUniqueEdgesForState(UserEquipmentState(4, 3, 2))

        assertThat(edges.map { it.dest })
            .containsExactlyElementsIn(
                listOf(
                    UserEquipmentState(4, 3, 3),
                    UserEquipmentState(4, 4, 3),
                    UserEquipmentState(5, 3, 3),
                    UserEquipmentState(5, 4, 3),
                )
            )
    }

}