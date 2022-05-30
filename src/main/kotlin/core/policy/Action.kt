package core.policy

import core.symbol.Symbol

sealed class Action : Symbol, Comparable<Action> {

    object NoOperation : Action() {
        override fun compareTo(other: Action): Int {
            return if (other == NoOperation) 0 else -1
        }
    }

    data class AddToCPU(
        val queueIndex: Int
    ) : Action() {

        override fun compareTo(other: Action): Int {
            return when (other) {
                is NoOperation -> 1
                is AddToCPU -> queueIndex.compareTo(other.queueIndex)
                else -> -1
            }
        }
    }

    data class AddToTransmissionUnit(
        val queueIndex: Int
    ) : Action() {

        override fun compareTo(other: Action): Int {
            return when (other) {
                is NoOperation -> 1
                is AddToCPU -> 1
                is AddToTransmissionUnit -> queueIndex.compareTo(other.queueIndex)
                is AddToBothUnits -> -1
            }
        }
    }

    data class AddToBothUnits(
        val cpuTaskQueueIndex: Int,
        val transmissionUnitTaskQueueIndex: Int,
    ) : Action() {

        override fun compareTo(other: Action): Int {
            return when (other) {
                is NoOperation -> 1
                is AddToCPU -> 1
                is AddToTransmissionUnit -> 1
                is AddToBothUnits -> cpuTaskQueueIndex.compareTo(other.cpuTaskQueueIndex).let {
                    if (it == 0) transmissionUnitTaskQueueIndex.compareTo(other.transmissionUnitTaskQueueIndex) else it
                }
            }
        }
    }

}