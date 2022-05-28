package core

import kotlin.random.Random
import kotlin.system.measureTimeMillis

fun <T> runRecordingMillis(label: String, block: () -> T): T {
    var result: T
    val millis = measureTimeMillis {
        result = block()
    }
    println("$label : $millis ms")
    return result
}

fun mutableListOfZeros(size: Int): MutableList<Double> {
    val result: MutableList<Double> = mutableListOf()

    for (i in 0 until size) {
        result.add(0.0)
    }
    return result
}

fun withProbability(p: Double, block: () -> Unit) {
    val rand = Random.nextDouble()

    if (rand < p) {
        block()
    }
}