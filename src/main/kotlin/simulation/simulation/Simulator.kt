package simulation.simulation

import simulation.logger.Logger
import core.policy.Policy
import simulation.ue.UserEquipment
import core.ue.OffloadingSystemConfig
import ue.UserEquipmentTimingInfoProvider
import kotlin.system.exitProcess

class Simulator(
    systemConfig: OffloadingSystemConfig
) : UserEquipmentTimingInfoProvider {
    private val userEquipment: UserEquipment = UserEquipment(this, systemConfig)
    private val simulationReportCreator: SimulationReportCreator = SimulationReportCreator(systemConfig)
    private var clock: Int = 0
    private val logger = Logger()

    fun simulatePolicy(policy: Policy, numberOfTimeSlots: Int): SimulationReport {
        userEquipment.reset()
        logger.reset()
        userEquipment.logger = logger
        clock = 0

        var lastPercent: Double = 0.0
        runFor(numberOfTimeSlots) {
            val action = policy.getActionForState(userEquipment.getUserEquipmentExecutionState())
            val oldState = userEquipment.state
            try {
                userEquipment.tick(action)
            } catch (e: Exception) {
                exitProcess(1)
            }
            val progress = clock.toDouble() / numberOfTimeSlots.toDouble()
        }

        return simulationReportCreator.createReport(logger.events)
    }

    private fun runFor(numberOfTimeSlots: Int, block: () -> Unit) {
        repeat(numberOfTimeSlots) {
            block()
            clock++
        }
    }

    override fun getCurrentTimeslot(): Int {
        return clock
    }
}