package ue

import helper.runWithProbability
import logger.Event
import logger.Logger
import policy.Action
import simulation.Simulator
import java.lang.Exception
import kotlin.random.Random
import kotlin.system.exitProcess

class UserEquipment(
    val timingInfoProvider: UserEquipmentTimingInfoProvider,
    private val config: UserEquipmentConfig
) {
    var state: UserEquipmentState = UserEquipmentState(0, 0, 0)
    var logger: Logger? = null

    var cpuTaskId: Int = -1
    var tuTaskId: Int = -1
    var arrivedTaskCount: Int = 0
    var lastUsedId: Int = 0
    var droppedTasks: Int = 0

    fun tick(action: Action) {
        state = getNextState(action)
    }

    fun addTask(state: UserEquipmentState): UserEquipmentState {
        check(state.taskQueueLength <= config.taskQueueCapacity)

        if (state.taskQueueLength == config.taskQueueCapacity) {
            System.err.println("Warning! Max queue capacity was reached. Task was dropped.")
            droppedTasks += 1
            return state
        } else {
            arrivedTaskCount++
            logger?.log(Event.TaskArrival(arrivedTaskCount, timingInfoProvider.getCurrentTimeslot()))
            return state.copy(
                taskQueueLength = state.taskQueueLength + 1
            )
        }
    }

    private fun getNextState(action: Action): UserEquipmentState {
        return when (action) {
            Action.NoOperation -> {
                state.let {
                    if (it.cpuState > 0) advanceCPUInState(it) else it
                }
            }
            Action.AddToCPU -> {
                addToCPU(state)
            }
            Action.AddToTransmissionUnit -> {
                addToTransmissionUnit(state).let {
                    if (it.cpuState > 0) advanceCPUInState(it) else it
                }
            }
            Action.AddToBothUnits -> {
                check(state.taskQueueLength > 1)
                addToTransmissionUnit(addToCPU(state))
            }
        }.let {
            val rand = Random.nextDouble()
            if (it.tuState > 0 && rand < config.beta) advanceTUInState(it) else it
        }.let {
            val rand = Random.nextDouble()
            if (rand < config.alpha) addTask(it) else it
        }
    }

    private fun advanceCPUInState(inputState: UserEquipmentState): UserEquipmentState {
        check(inputState.cpuState > 0)
        val nextCpuState: Int
        if (inputState.cpuState == config.cpuNumberOfSections - 1) {
            logger?.log(Event.TaskProcessedByCPU(cpuTaskId, timingInfoProvider.getCurrentTimeslot()))
            cpuTaskId = -1
            nextCpuState = 0
        } else {
            nextCpuState = inputState.cpuState + 1
        }
        return inputState.copy(
            cpuState = nextCpuState
        )
    }

    private fun advanceTUInState(inputState: UserEquipmentState): UserEquipmentState {
        check(inputState.tuState > 0)
        val nextTuState: Int
        if (inputState.tuState == config.tuNumberOfPackets) {
            logger?.log(Event.TaskTransmittedByTU(tuTaskId, timingInfoProvider.getCurrentTimeslot()))
            tuTaskId = -1
            nextTuState = 0
        } else {
            nextTuState = inputState.tuState + 1
        }
        return inputState.copy(
            tuState = nextTuState
        )
    }

    private fun addToTransmissionUnit(state: UserEquipmentState): UserEquipmentState {
        check(state.tuState == 0)
        check(tuTaskId == -1)

        tuTaskId = makeNewId()

        logger?.log(Event.TaskSentToTU(tuTaskId, timingInfoProvider.getCurrentTimeslot()))
        return state.copy(
            taskQueueLength = state.taskQueueLength - 1,
            tuState = 1
        )
    }

    private fun addToCPU(state: UserEquipmentState): UserEquipmentState {
        check(state.cpuState == 0)
        check(cpuTaskId == -1)
        cpuTaskId = makeNewId()

        logger?.log(Event.TaskSentToCPU(cpuTaskId, timingInfoProvider.getCurrentTimeslot()))
        return state.copy(
            taskQueueLength = state.taskQueueLength - 1,
            cpuState = 1
        )
    }

    fun makeNewId(): Int {
        check(lastUsedId < arrivedTaskCount) {
            println("Check failed $lastUsedId $arrivedTaskCount")
        }
        lastUsedId++
        return lastUsedId
    }

    fun reset() {
        state = UserEquipmentState(0, 0, 0)
        cpuTaskId = -1
        tuTaskId = -1
        arrivedTaskCount = 0
        lastUsedId = 0
    }
}