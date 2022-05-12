package app

import dtmc.DTMCCreator
import ue.UserEquipmentStateConfig

fun main() {
    val chainCreator = DTMCCreator(
        stateConfig = UserEquipmentStateConfig(
            taskQueueCapacity = 5,
            tuNumberOfPackets = 4,
            cpuNumberOfSections = 3
        )
    )

    val markovChain = chainCreator.create()
    println(markovChain)
}