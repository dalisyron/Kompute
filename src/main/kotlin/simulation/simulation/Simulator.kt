package simulation.simulation

import simulation.logger.Logger
import core.policy.Policy
import simulation.ue.UserEquipment
import ue.OffloadingSystemConfig
import ue.UserEquipmentTimingInfoProvider

class Simulator(
    systemConfig: OffloadingSystemConfig
) : UserEquipmentTimingInfoProvider {
    private val userEquipment: UserEquipment = UserEquipment(this, systemConfig.userEquipmentConfig)
    private val simulationReportCreator: SimulationReportCreator = SimulationReportCreator(systemConfig.environmentParameters)
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
            userEquipment.tick(action)
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