package simulation.simulation

import environment.EnvironmentParameters
import simulation.logger.Event

class SimulationReportCreator(
    val environmentParameters: EnvironmentParameters
) {

    fun createReport(events: List<Event>): SimulationReport {
        val eventsById = events.groupBy { event -> event.id }.filterValues {
                list -> (list.any { it is Event.TaskProcessedByCPU } || list.any { it is Event.TaskTransmittedByTU })
        }

        eventsById.values.forEach { list ->
            check(list.any {it is Event.TaskProcessedByCPU} || list.any { it is Event.TaskTransmittedByTU })
        }
        val taskDelays: Map<Int, Double> = eventsById.mapValues { (_, list) ->
            val startTime = list.find { it is Event.TaskArrival }!!.timeSlot

            val finishTime: Double = if (list.any { it is Event.TaskTransmittedByTU }) {
                val transmittedTime = list.find { it is Event.TaskTransmittedByTU }!!.timeSlot

                transmittedTime + environmentParameters.nCloud + environmentParameters.tRx
            } else {
                list.find { it is Event.TaskProcessedByCPU }!!.timeSlot.toDouble()
            }

            return@mapValues finishTime - startTime
        }
        check(taskDelays.isNotEmpty())
        val averageDelay = taskDelays.values.average()

        return SimulationReport(averageDelay)
    }
}