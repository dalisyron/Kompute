package simulation.simulation

import core.ue.OffloadingSystemConfig
import simulation.logger.Event

class SimulationReportCreator(
    val systemConfig: OffloadingSystemConfig
) {

    fun createReport(reportInfo: ReportInfo): SimulationReport {
        val events = reportInfo.events
        val totalConsumedPower = reportInfo.totalConsumedPower
        val timeSlots = reportInfo.numberOfTimeSlots

        val eventsById = events.groupBy { event -> event.id }.filterValues {
                list -> (list.any { it is Event.TaskProcessedByCPU } || list.any { it is Event.TaskTransmittedByTU })
        }

        eventsById.values.forEach { list ->
            check(list.any {it is Event.TaskProcessedByCPU} || list.any { it is Event.TaskTransmittedByTU })
        }

        // val transmissionTimes = mutableListOf<Int>()

//        println("events = $events")
//        println("eventsById = $eventsById")

        val taskDelays: Map<Int, Double> = eventsById.mapValues { (_, list) ->
            val arrivalEvent: Event.TaskArrival = list.find { it is Event.TaskArrival }!! as Event.TaskArrival
            val startTime: Int = arrivalEvent.timeSlot
            val finishTime: Double = if (list.any { it is Event.TaskTransmittedByTU }) {
                val transmittedTime = list.find { it is Event.TaskTransmittedByTU }!!.timeSlot

                // val sentForTransmissionTime = list.find { it is Event.TaskSentToTU }!!.timeSlot
                // transmissionTimes.add(transmittedTime - sentForTransmissionTime)

                transmittedTime + systemConfig.tRx + systemConfig.nCloud[arrivalEvent.queueIndex]
            } else {
                list.find { it is Event.TaskProcessedByCPU }!!.timeSlot.toDouble()
            }

            return@mapValues finishTime - startTime
        }
        check(taskDelays.isNotEmpty())
        val averageDelay = taskDelays.values.average()
        // val averageTransmissionTime = transmissionTimes.average()
        // println("averageTransmissionTime = $averageTransmissionTime")

        val isEffective =  (reportInfo.numberOfQueueFullTimeSlots.toDouble() / reportInfo.numberOfTimeSlots.toDouble()) < (1.0 / reportInfo.stateCount.toDouble())

        return SimulationReport(
            averageDelay = averageDelay,
            averagePowerConsumption = totalConsumedPower / timeSlots,
            isEffective = isEffective
        )
    }

    data class ReportInfo(
        val events: List<Event>,
        val totalConsumedPower: Double,
        val numberOfTimeSlots: Int,
        val numberOfQueueFullTimeSlots: Int,
        val stateCount: Int
    )
}