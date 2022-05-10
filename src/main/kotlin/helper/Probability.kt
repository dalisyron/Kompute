package helper

import kotlin.random.Random

fun runWithProbability(probability: Double, codeBlock: () -> Unit) {
    val rand = Random.nextDouble()

    if (rand < probability) {
        codeBlock()
    }
}
