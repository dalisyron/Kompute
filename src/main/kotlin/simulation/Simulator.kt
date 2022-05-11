package simulation

import environment.EnvironmentParameters
import logger.Logger
import policy.Policy
import ue.UserEquipment
import ue.UserEquipmentConfig
import ue.UserEquipmentTimingInfoProvider

class Simulator(
    private val environmentParameters: EnvironmentParameters,
    private val userEquipmentConfig: UserEquipmentConfig
) : UserEquipmentTimingInfoProvider {
    private val userEquipment: UserEquipment = UserEquipment(this, userEquipmentConfig)
    private val simulationReportCreator: SimulationReportCreator = SimulationReportCreator(environmentParameters)
    private var clock: Int = 0
    private val logger = Logger()

    fun simulatePolicy(policy: Policy, numberOfTimeSlots: Int): SimulationReport {
        userEquipment.reset()
        logger.reset()
        userEquipment.logger = logger
        clock = 0

        var lastPercent: Double = 0.0
        runFor(numberOfTimeSlots) {
            val action = policy.getActionForState(userEquipment.state)
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