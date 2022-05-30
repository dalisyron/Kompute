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

fun List<Int>.decrementedAt(pos: Int): List<Int> {
    val temp = toMutableList()
    temp[pos]--
    return temp
}

fun List<Int>.incrementedAt(pos: Int): List<Int> {
    val temp = toMutableList()
    temp[pos]--
    return temp
}

fun getAllSubsets(n: Int): List<List<Boolean>> {
    val result = mutableListOf<List<Boolean>>()

    for (i in 0 until (1 shl n)) {
        val subset = mutableListOf<Boolean>()
        for (j in 0 until n) {
            subset.add((i and (1 shl j)) > 0)
        }
        result.add(subset)
    }

    return result
}

class TupleGenerator(
    val sizes: List<Int>
) {
    init {
        check(sizes.size > 1)
    }
    var tuples: List<List<Int>> = mutableListOf()

    private fun generateHelper(index: Int) {
        if (index == sizes.size) return

        tuples = tuples.map { tuple ->
            (0 until sizes[index]).map { tuple + it }
        }.flatten()

        generateHelper(index + 1)
    }

    private fun generate(): List<List<Int>> {
        tuples = (0 until sizes[0]).map { listOf(it) }
        generateHelper(1)

        return tuples
    }

    companion object {

        fun generateTuples(sizes: List<Int>): List<List<Int>> {
            val generator = TupleGenerator(sizes)

            generator.generate()

            return generator.tuples
        }
    }
}
