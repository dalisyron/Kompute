package logger

class Logger(
    val events: MutableList<Event> = mutableListOf()
) {

    fun log(event: Event) {
        events.add(event)
    }

    fun reset() {
        events.clear()
    }
}