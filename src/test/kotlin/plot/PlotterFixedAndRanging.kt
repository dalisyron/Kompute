package plot

import com.github.sh0nk.matplotlib4j.Plot
import core.cartesianProduct
import core.maxNotEmpty
import stochastic.policy.AlphaRange
import stochastic.policy.MultiQueueRangedAlphaTester
import java.lang.Integer.min

object PlotterFixedAndRanging {

    fun plot(result: MultiQueueRangedAlphaTester.Result) {
        require(result.alphaRanges.size == 2)
        require(result.alphaRanges.any { it is AlphaRange.Constant })
        require(result.alphaRanges.any { it is AlphaRange.Variable })

        val maxValue: Double = listOf(
            result.localOnlyDelays.maxNotEmpty(),
            result.offloadOnlyDelays.maxNotEmpty(),
            result.greedyOffloadFirstDelays.maxNotEmpty(),
            result.greedyLocalFirstDelays.maxNotEmpty(),
            result.stochasticDelays.maxNotEmpty()
        ).maxNotEmpty()

        val constantIndex = result.alphaRanges.indexOfFirst { it is AlphaRange.Constant }
        val variableIndex = result.alphaRanges.indexOfFirst { it is AlphaRange.Variable }

        val constantAlpha = result.alphaRanges[constantIndex] as AlphaRange.Constant
        val variableAlpha = result.alphaRanges[variableIndex] as AlphaRange.Variable

        val alphas = cartesianProduct(result.alphaRanges.map { it.toList() }).map { it[variableIndex] }

        val plot = Plot.create()
        plot.plot().add(alphas, result.localOnlyDelays).color("tab:olive").label("Local Only")
        plot.plot().add(alphas, result.offloadOnlyDelays).color("blue").label("Offload Only")
        plot.plot().add(alphas, result.greedyOffloadFirstDelays).color("green").label("Greedy (Offload First)")
        plot.plot().add(alphas, result.greedyLocalFirstDelays).color("cyan").label("Greedy (Local First)")
        plot.plot().add(alphas, result.stochasticDelays).color("red").label("Optimal Stochastic")

        plot.xlabel("The average arrival rate (alpha[$variableIndex])")
        plot.ylabel("The average delay")
        plot.title("Average delay for policies with alpha[$constantIndex] = ${constantAlpha.value}")
        plot.ylim(0, min(50, maxValue.toInt()))
        plot.xlim(variableAlpha.start, variableAlpha.end)
        plot.legend()
        plot.show()
    }
}