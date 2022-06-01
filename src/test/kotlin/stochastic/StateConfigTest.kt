import com.google.common.truth.Truth.assertThat
import core.StateManagerConfig
import core.UserEquipmentStateManager
import org.junit.Test
import core.ue.UserEquipmentStateConfig

class StateConfigTest {
    private val sampleStateConfig1 = UserEquipmentStateConfig.singleQueue(
        taskQueueCapacity = 5,
        tuNumberOfPackets = 4,
        cpuNumberOfSections = 3
    )
    val stateManager = UserEquipmentStateManager(
        StateManagerConfig(
            userEquipmentStateConfig = sampleStateConfig1,
            limitation = listOf(StateManagerConfig.Limitation.None)
        )
    )

    @Test
    fun stateUniquenessTest() {
        val states = stateManager.allStates()

        assertThat(states)
            .containsNoDuplicates()
    }
}