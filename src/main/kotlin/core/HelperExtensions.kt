package core

import com.github.sh0nk.matplotlib4j.Plot
import java.io.File
import java.lang.Integer.max
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

fun mutableListOfInt(size: Int, value: Int): MutableList<Int> {
    val result: MutableList<Int> = mutableListOf()

    for (i in 0 until size) {
        result.add(value)
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

fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
    require(lists.isNotEmpty())
    val first = lists.first()
    val other = lists.drop(1)

    if (other.isEmpty()) {
        return listOf(first)
    }

    if (other.size == 1) {
        val result = mutableListOf<List<T>>()
        for (a in first) {
            for (b in other[0]) {
                val temp = listOf(a, b)
                result.add(temp)
            }
        }
        return result
    }

    val productOther: List<List<T>> = cartesianProduct(other)

    val result = mutableListOf<List<T>>()
    for (a in first) {
        for (list in productOther) {
            result.add(listOf(a) + list)
        }
    }

    return result
}

fun <T : Comparable<T>> List<T>.maxNotEmpty(): T {
    return requireNotNull(maxOrNull())
}

fun <T> List<T>.splitEqual(k: Int): List<List<T>> {
    require(k > 0)
    require(this.isNotEmpty())
    require(k * 1 <= size)

    val result: MutableList<List<T>> = mutableListOf()
    val rem = size % k

    var ptr = 0
    repeat(rem) {
        val temp = mutableListOf<T>()
        for (i in 0 until size / k + 1) {
            temp.add(this[ptr])
            ptr++
        }
        result.add(temp)
    }

    repeat(k - rem) {
        val temp = mutableListOf<T>()
        for (i in 0 until (size / k)) {
            temp.add(this[ptr])
            ptr++
        }
        result.add(temp)
    }
    return result
}

fun List<Int>.toCumulative(): List<Int> {
    val result: MutableList<Int> = mutableListOf()
    var acc = 0
    for (element in this) {
        acc += element
        result.add(acc)
    }

    return result
}

fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}

fun writeToFile(plt: Plot) {
    plt.savefig("figure.png")
    plt.executeSilently()
}
