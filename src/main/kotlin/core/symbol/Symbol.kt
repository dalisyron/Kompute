package core.symbol

interface Symbol

sealed class ParameterSymbol : Symbol {

    data class Alpha(val queueIndex: Int) : ParameterSymbol()

    object Beta : ParameterSymbol()

    data class AlphaC(val queueIndex: Int) : ParameterSymbol()

    object BetaC : ParameterSymbol()

    override fun toString(): String {
        return javaClass.simpleName
    }
}