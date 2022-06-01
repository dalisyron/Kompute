package core.policy

import core.symbol.Symbol

sealed class Action : Symbol, Comparable<Action> {

    object NoOperation : Action() {
        override fun compareTo(other: Action): Int {
            return if (other == NoOperation) 0 else -1
        }

        override fun toString(): String {
            return "NoOperation"
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

        override fun toString(): String {
            return "AddToCPU($queueIndex)"
        }

        companion object {

            fun singleQueue(): AddToCPU {
                return AddToCPU(0)
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

        override fun toString(): String {
            return "AddToTransmissionUnit($queueIndex)"
        }

        companion object {

            fun singleQueue(): AddToTransmissionUnit {
                return AddToTransmissionUnit(0)
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

        override fun toString(): String {
            return "AddToBothUnits(cpu = $cpuTaskQueueIndex, tu = $transmissionUnitTaskQueueIndex)"
        }

        companion object {

            fun singleQueue(): AddToBothUnits {
                return AddToBothUnits(0, 0)
            }
        }
    }

}