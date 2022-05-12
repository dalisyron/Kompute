package dtmc.transition

interface Symbol

sealed class ParameterSymbol : Symbol {

    object Alpha : ParameterSymbol()

    object Beta : ParameterSymbol()

    object AlphaC : ParameterSymbol()

    object BetaC : ParameterSymbol()

    override fun toString(): String {
        return javaClass.simpleName
    }
}