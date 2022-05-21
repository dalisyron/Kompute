package simulation.simulation

import core.ue.OffloadingSystemConfig
import simulation.logger.Event

class SimulationReportCreator(
    val systemConfig: OffloadingSystemConfig
) {

    fun createReport(events: List<Event>): SimulationReport {
        val eventsById = events.groupBy { event -> event.id }.filterValues {
                list -> (list.any { it is Event.TaskProcessedByCPU } || list.any { it is Event.TaskTransmittedByTU })
        }

        eventsById.values.forEach { list ->
            check(list.any {it is Event.TaskProcessedByCPU} || list.any { it is Event.TaskTransmittedByTU })
        }

        // val transmissionTimes = mutableListOf<Int>()

        val taskDelays: Map<Int, Int> = eventsById.mapValues { (_, list) ->
            val startTime: Int = list.find { it is Event.TaskArrival }!!.timeSlot
            val finishTime: Int = if (list.any { it is Event.TaskTransmittedByTU }) {
                val transmittedTime = list.find { it is Event.TaskTransmittedByTU }!!.timeSlot

                // val sentForTransmissionTime = list.find { it is Event.TaskSentToTU }!!.timeSlot
                // transmissionTimes.add(transmittedTime - sentForTransmissionTime)

                transmittedTime + systemConfig.tRx + systemConfig.nCloud
            } else {
                list.find { it is Event.TaskProcessedByCPU }!!.timeSlot
            }

            return@mapValues finishTime - startTime
        }
        check(taskDelays.isNotEmpty())
        val averageDelay = taskDelays.values.average()
        // val averageTransmissionTime = transmissionTimes.average()
        // println("averageTransmissionTime = $averageTransmissionTime")

        return SimulationReport(averageDelay)
    }
}