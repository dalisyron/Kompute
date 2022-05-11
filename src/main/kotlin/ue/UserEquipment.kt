package ue

import dtmc.UserEquipmentStateManager
import logger.Event
import logger.Logger
import policy.Action
import kotlin.random.Random

class UserEquipment(
    val timingInfoProvider: UserEquipmentTimingInfoProvider,
    private val config: UserEquipmentConfig
) {
    val stateManager = UserEquipmentStateManager(config.stateConfig)
    var state: UserEquipmentState = UserEquipmentState(0, 0, 0)
    var logger: Logger? = null

    var cpuTaskId: Int = -1
    var tuTaskId: Int = -1
    var arrivedTaskCount: Int = 0
    var lastUsedId: Int = 0
    var droppedTasks: Int = 0
    var isCpuActive = false

    fun tick(action: Action) {
        executeAction(action)
    }

    private fun executeAction(action: Action) {
        isCpuActive = state.cpuState > 0

        // 1. Apply action
        when (action) {
            Action.NoOperation -> {
                // NOP
            }
            Action.AddToCPU -> {
                addToCPU()
            }
            Action.AddToTransmissionUnit -> {
                addToTransmissionUnit()
            }
            Action.AddToBothUnits -> {
                check(state.taskQueueLength > 1)
                addToTransmissionUnit()
                addToCPU()
            }
        }

        // 2. Advance UE components
        if (isCpuActive)
            advanceCPU()

        val pTransmit = Random.nextDouble()
        if (state.tuState > 0 && pTransmit < config.componentsConfig.beta)
            advanceTU()

        // 3. Add new task with probability alpha
        val rand = Random.nextDouble()
        if (rand < config.componentsConfig.alpha)
            addTask()
    }

    fun addTask() {
        arrivedTaskCount++
        val id = arrivedTaskCount
        logger?.log(Event.TaskArrival(id, timingInfoProvider.getCurrentTimeslot()))

        try {
            state = stateManager.addTaskNextState(state)
        } catch (e: UserEquipmentStateManager.TaskQueueFullException) {
            System.err.println("Warning! Max queue capacity was reached. Dropping task.")
            logger?.log(Event.TaskDropped(id, timingInfoProvider.getCurrentTimeslot()))
            droppedTasks += 1
        }
    }

    private fun advanceCPU() {
        check(state.cpuState >= 0 && isCpuActive)
        state = stateManager.advanceCPUNextState(state)

        if (state.cpuState == 0) {
            logger?.log(Event.TaskProcessedByCPU(cpuTaskId, timingInfoProvider.getCurrentTimeslot()))
            cpuTaskId = -1
        }
    }

    private fun advanceTU() {
        check(state.tuState > 0)
        state = stateManager.advanceTUNextState(state)

        if (state.tuState == 0) {
            logger?.log(Event.TaskTransmittedByTU(tuTaskId, timingInfoProvider.getCurrentTimeslot()))
            tuTaskId = -1
        }
    }

    private fun addToTransmissionUnit() {
        check(tuTaskId == -1)
        tuTaskId = makeNewId()
        state = stateManager.addToTransmissionUnitNextState(state)

        logger?.log(Event.TaskSentToTU(tuTaskId, timingInfoProvider.getCurrentTimeslot()))
    }

    private fun addToCPU() {
        check(cpuTaskId == -1)
        cpuTaskId = makeNewId()
        state = stateManager.addToCPUNextState(state)

        logger?.log(Event.TaskSentToCPU(cpuTaskId, timingInfoProvider.getCurrentTimeslot()))
        isCpuActive = true
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