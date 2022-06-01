package core.symbol

interface Symbol

sealed class ParameterSymbol : Symbol {

    data class Alpha(val queueIndex: Int) : ParameterSymbol() {

        companion object {
            fun singleQueue(): Alpha {
                return Alpha(0)
            }
        }
    }

    object Beta : ParameterSymbol()

    data class AlphaC(val queueIndex: Int) : ParameterSymbol() {

        companion object {
            fun singleQueue(): AlphaC {
                return AlphaC(0)
            }
        }
    }

    object BetaC : ParameterSymbol()

    override fun toString(): String {
        return javaClass.simpleName
    }
}