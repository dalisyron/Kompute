package simulation.logger

import core.UserEquipmentStateManager
import core.policy.Action
import core.ue.UserEquipmentState
import ue.UserEquipmentTimingInfoProvider

class Logger(
    private val timingInfoProvider: UserEquipmentTimingInfoProvider
) {
    val events: MutableList<Event> = mutableListOf()
    var cpuTaskId: Int = -1
    var tuTaskId: Int = -1
    var arrivedTaskCount: Int = 0
    var lastUsedId: Int = 0
    var droppedTasks: Int = 0

    fun makeNewId(): Int {
        check(lastUsedId < arrivedTaskCount) {
            println("Check failed $lastUsedId $arrivedTaskCount")
        }
        lastUsedId++
        return lastUsedId
    }

    private fun log(event: Event) {
        events.add(event)
    }

    fun addLogsFromStateAction(sourceState: UserEquipmentState, endState: UserEquipmentState, action: Action) {
        // println("$sourceState | $endState | $action")
        when (action) {
            is Action.NoOperation -> {
            }
            is Action.AddToCPU -> {
                logAddToCPU(action.queueIndex)
            }
            is Action.AddToTransmissionUnit -> {
                logAddToTransmissionUnit(action.queueIndex)
            }
            is Action.AddToBothUnits -> {
                logAddToTransmissionUnit(action.transmissionUnitTaskQueueIndex)
                logAddToCPU(action.cpuTaskQueueIndex)
            }
        }

        if (endState.cpuState < sourceState.cpuState) {
            logTaskProcessedByCPU()
        }
        if ((sourceState.tuState > 0 || action is Action.AddToTransmissionUnit || action is Action.AddToBothUnits) && endState.tuState == 0) {
            logTaskTransmittedByTU()
        }
    }

    private fun logAddToCPU(taskTypeQueueIndex: Int) {
        check(cpuTaskId == -1)
        cpuTaskId = makeNewId()

        log(Event.TaskSentToCPU(cpuTaskId, timingInfoProvider.getCurrentTimeslot(), taskTypeQueueIndex))
    }

    private fun logAddToTransmissionUnit(taskTypeQueueIndex: Int) {
        check(tuTaskId == -1) {
            tuTaskId
        }
        tuTaskId = makeNewId()
        log(Event.TaskSentToTU(tuTaskId, timingInfoProvider.getCurrentTimeslot(), taskTypeQueueIndex))
    }

    fun reset() {
        cpuTaskId = -1
        tuTaskId = -1
        arrivedTaskCount = 0
        lastUsedId = 0
        events.clear()
    }

    fun logTaskTransmittedByTU() {
        log(Event.TaskTransmittedByTU(tuTaskId, timingInfoProvider.getCurrentTimeslot() + 1))
        tuTaskId = -1
    }

    fun logTaskProcessedByCPU() {
        log(Event.TaskProcessedByCPU(cpuTaskId, timingInfoProvider.getCurrentTimeslot() + 1))
        cpuTaskId = -1
    }

    fun logAddNewTask(queueIndex: Int) {
        arrivedTaskCount++
        val id = arrivedTaskCount
        log(Event.TaskArrival(id, timingInfoProvider.getCurrentTimeslot(), queueIndex))
    }

    fun logTaskDropped(queueIndex: Int) {
        droppedTasks++
    }
}