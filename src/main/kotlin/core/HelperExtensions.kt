package core

import java.lang.Integer.min
import kotlin.random.Random
import kotlin.random.nextInt
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
    temp[pos]++
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
        check(sizes.isNotEmpty())
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

object EtaGenerator {
    fun generate(queueCount: Int, precision: Int): List<List<Double>> {
        val tuples = TupleGenerator.generateTuples((1..queueCount).map { precision + 1 })
        return tuples.map { it ->
            it.map { it.toDouble() / precision }
        }
    }
}

fun <T : Comparable<T>> List<T>.compareTo(other: List<T>): Int {
    require(this.isNotEmpty())
    require(other.isNotEmpty())

    for (i in 0 until min(this.size, other.size)) {
        if (this[i] != other[i]) {
            return this[i].compareTo(other[i])
        }
    }

    return 0
}

fun Int.pow(n: Int): Int {
    val x = this
    require(n >= 0)

    if (n == 0) {
        return 1
    } else {
        var pw = (x * x).pow(n / 2)
        if (n % 2 > 0) {
            pw *= x
        }
        return pw
    }
}