package ue

import kotlin.random.Random

data class UserEquipmentState(
    val taskQueueLength: Int,
    val tuState: Int,
    val cpuState: Int
)
