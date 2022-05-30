package simulation.ue

import core.TaskQueueFullException
import core.policy.UserEquipmentExecutionState
import core.UserEquipmentStateManager
import core.ue.OffloadingSystemConfig
import simulation.logger.Event
import simulation.logger.Logger
import core.policy.Action
import core.ue.UserEquipmentState
import core.ue.UserEquipmentState.Companion.validate
import core.withProbability
import ue.UserEquipmentTimingInfoProvider
import kotlin.random.Random

class UserEquipment(
    private val timingInfoProvider: UserEquipmentTimingInfoProvider,
    private val config: OffloadingSystemConfig
) {
    private val stateManager = UserEquipmentStateManager(config.getStateManagerConfig())


    var state: UserEquipmentState = UserEquipmentState(0, 0, 0)
        set(value) {
            value.validate()
            field = value
        }
    var logger: Logger? = null

    var timeSlot: Int = 0
    var consumedPower: Double = 0.0

    fun tick(action: Action) {
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
        state = stateManager.getNextStateRunningAction(state, action)
        logger?.addLogsFromStateAction(startState, action)

        // 2. Advance UE components
        advanceComponents()

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

    fun addTasks() {
        try {
            state = stateManager.addTaskNextState(state)
            arrivedTaskCount++
            val id = arrivedTaskCount
            logger?.log(Event.TaskArrival(id, timingInfoProvider.getCurrentTimeslot()))
        } catch (e: UserEquipmentStateManager.TaskQueueFullException) {
            // System.err.println("Warning! Max queue capacity was reached. Dropping task.")
            // logger?.log(Event.TaskDropped(-1, timingInfoProvider.getCurrentTimeslot()))
            droppedTasks += 1
        }
    }

    private fun advanceCPU() {
        state = stateManager.getNextStateAdvancingCPU(state)
        consumedPower += config.pLoc
    }

    private fun advanceTU() {
        state = stateManager.getNextStateAdvancingTU(state)
        if (state.isTUActive()) {
            logger?.logTaskTransmittedByTU()
        }
        consumedPower += config.pTx
    }

    fun reset() {
        state = UserEquipmentState(0, 0, 0)
        cpuTaskId = -1
        tuTaskId = -1
        arrivedTaskCount = 0
        lastUsedId = 0
        consumedPower = 0.0
        timeSlot = 0
    }

}
