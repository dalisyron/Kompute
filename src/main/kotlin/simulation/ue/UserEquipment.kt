package simulation.ue

import core.TaskQueueFullException
import core.policy.UserEquipmentExecutionState
import core.UserEquipmentStateManager
import core.ue.OffloadingSystemConfig
import simulation.logger.Event
import simulation.logger.Logger
import core.policy.Action
import core.ue.UserEquipmentState
import core.withProbability
import ue.UserEquipmentTimingInfoProvider
import kotlin.random.Random

class UserEquipment(
    private val timingInfoProvider: UserEquipmentTimingInfoProvider,
    private val config: OffloadingSystemConfig
) {
    private val stateManager = UserEquipmentStateManager(config.getStateManagerConfig())
    val allStates = stateManager.allStates()
    var queueFullTimeSlotCounter = 0

    var state: UserEquipmentState = stateManager.getInitialState()
    var logger: Logger? = null

    var timeSlot: Int = 0
    var consumedPower: Double = 0.0

    fun tick(action: Action) {
        if (state.taskQueueLengths.any { it == config.taskQueueCapacity } && action != Action.NoOperation) {
            queueFullTimeSlotCounter++
        }
        executeAction(action)
        timeSlot += 1
    }

    fun getUserEquipmentExecutionState(): UserEquipmentExecutionState {
        return UserEquipmentExecutionState(
            ueState = state,
            timeSlot = timeSlot,
            totalConsumedPower = consumedPower,
            pMax = config.pMax
        )
    }

    private fun getAverageConsumedPower(): Double {
        return consumedPower / timeSlot
    }

    private fun executeAction(action: Action) {
        val startState = state
        if (action is Action.AddToTransmissionUnit) {
            val hello = 2
        }
        state = stateManager.getNextStateRunningAction(state, action)

        // 2. Advance UE components
        advanceComponents()

        logger?.addLogsFromStateAction(startState, state, action)
        // 3. Add new task with probability alpha
        handleTaskArrival()
    }

    private fun handleTaskArrival() {
        for (i in 0 until config.numberOfQueues) {
            withProbability(config.alpha[i]) {
                addTasksToQueue(i)
            }
        }
    }

    private fun addTasksToQueue(queueIndex: Int) {
        try {
            state = stateManager.getNextStateAddingTaskToQueue(state, queueIndex)
            logger?.logAddNewTask(queueIndex)
        } catch (e: TaskQueueFullException) {
            logger?.logTaskDropped(queueIndex)
        }
    }

    private fun advanceComponents() {
        if (state.isCPUActive()) {
            advanceCPU()
        }

        if (state.isTUActive()) {
            withProbability(config.beta) {
                advanceTU()
            }
        }
    }

    private fun advanceCPU() {
        state = stateManager.getNextStateAdvancingCPU(state)
        consumedPower += config.pLoc
    }

    private fun advanceTU() {
        state = stateManager.getNextStateAdvancingTU(state)
        consumedPower += config.pTx
    }

    fun reset() {
        state = stateManager.getInitialState()
        logger?.reset()
        consumedPower = 0.0
        timeSlot = 0
        queueFullTimeSlotCounter = 0
    }

}
