package policy

import dtmc.transition.Symbol

sealed class Action : Symbol {
    object NoOperation : Action()

    object AddToCPU : Action()

    object AddToTransmissionUnit : Action()

    object AddToBothUnits : Action()

    override fun toString(): String {
        return javaClass.simpleName
    }
}