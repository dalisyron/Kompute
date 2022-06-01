package simulation.logger

sealed class Event(val id: Int, val timeSlot: Int) {

    class TaskArrival(id: Int, timeSlot: Int, val queueIndex: Int) : Event(id, timeSlot)

    class TaskProcessedByCPU(id: Int, timeSlot: Int) : Event(id, timeSlot)

    class TaskTransmittedByTU(id: Int, timeSlot: Int) : Event(id, timeSlot)

    class TaskSentToCPU(id: Int, timeSlot: Int, val queueIndex: Int) : Event(id, timeSlot)

    class TaskSentToTU(id: Int, timeSlot: Int, val queueIndex: Int) : Event(id, timeSlot)

    class TaskDropped(id: Int, timeSlot: Int) : Event(id, timeSlot)
}