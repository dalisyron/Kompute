package simulation.ue

import core.policy.UserEquipmentExecutionState
import core.UserEquipmentStateManager
import simulation.logger.Event
import simulation.logger.Logger
import policy.Action
import core.ue.UserEquipmentConfig
import ue.UserEquipmentState
import ue.UserEquipmentState.Companion.validate
import ue.UserEquipmentTimingInfoProvider
import kotlin.random.Random

class UserEquipment(
    private val timingInfoProvider: UserEquipmentTimingInfoProvider,
    private val config: UserEquipmentConfig
) {
    private val stateManager = UserEquipmentStateManager(config.stateConfig)


    var state: UserEquipmentState = UserEquipmentState(0, 0, 0)
        set(value) {
            value.validate()
            field = value
        }
    var logger: Logger? = null

    var cpuTaskId: Int = -1
    var tuTaskId: Int = -1
    var arrivedTaskCount: Int = 0
    var lastUsedId: Int = 0
    var droppedTasks: Int = 0
    var isCpuActive = false
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
            totalConsumedPower = consumedPower
        )
    }

    private fun getAverageConsumedPower(): Double {
        return consumedPower / timeSlot
    }

    private fun executeAction(action: Action) {
        if (getAverageConsumedPower() > config.componentsConfig.pMax) return // Exceeding UE's power limits (System not responding)

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
        advanceCPUIfActive()

        val pTransmit = Random.nextDouble()
        if (state.tuState > 0 && pTransmit < config.componentsConfig.beta)
            advanceTU()

        // 3. Add new task with probability alpha
        val rand = Random.nextDouble()
        if (rand < config.componentsConfig.alpha)
            addTask()
    }

    fun addTask() {
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

    private fun advanceCPUIfActive() {
        if (state.cpuState == 0) {
            return
        }
        if (state.cpuState == -1) {
            state = state.copy(cpuState = 1)
            consumedPower += config.pLoc
            return
        }

        consumedPower += config.pLoc
        state = stateManager.advanceCPUNextState(state)
        if (state.cpuState == 0) {
            logger?.log(Event.TaskProcessedByCPU(cpuTaskId, timingInfoProvider.getCurrentTimeslot() + 1))
            cpuTaskId = -1
        }
    }

    private fun advanceTU() {
        state = stateManager.advanceTUNextState(state)
        consumedPower += config.pTx

        if (state.tuState == 0) {
            logger?.log(Event.TaskTransmittedByTU(tuTaskId, timingInfoProvider.getCurrentTimeslot() + 1))
            tuTaskId = -1
        }
    }

    private fun addToTransmissionUnit() {
        check(tuTaskId == -1) {
            state
        }
        tuTaskId = makeNewId()
        state = stateManager.addToTransmissionUnitNextState(state)

        logger?.log(Event.TaskSentToTU(tuTaskId, timingInfoProvider.getCurrentTimeslot()))
    }

    private fun addToCPU() {
        check(cpuTaskId == -1) {
            state
        }
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
        consumedPower = 0.0
        timeSlot = 0
    }

}
