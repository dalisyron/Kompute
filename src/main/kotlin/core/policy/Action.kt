package core.policy

import dtmc.symbol.Symbol

sealed class Action(val order: Int) : Symbol {
    object NoOperation : Action(0)

    object AddToCPU : Action(1)

    object AddToTransmissionUnit : Action(2)

    object AddToBothUnits : Action(3)

    override fun toString(): String {
        return javaClass.simpleName
    }
}