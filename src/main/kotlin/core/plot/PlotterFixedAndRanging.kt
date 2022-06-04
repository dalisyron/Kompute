package core.plot

import com.github.sh0nk.matplotlib4j.Plot
import core.cartesianProduct
import core.maxNotEmpty
import core.policy.AlphaRange
import core.policy.AlphaDelayResults
import java.io.File
import java.lang.Integer.min

object PlotterFixedAndRanging {

    fun plot(alphaDelayResults: AlphaDelayResults) {
        require(alphaDelayResults.alphaRanges.size == 2)
        require(alphaDelayResults.alphaRanges.any { it is AlphaRange.Constant })
        require(alphaDelayResults.alphaRanges.any { it is AlphaRange.Variable })

        val maxValue: Double = listOf(
            alphaDelayResults.localOnlyDelays.maxNotEmpty(),
            alphaDelayResults.offloadOnlyDelays.maxNotEmpty(),
            alphaDelayResults.greedyOffloadFirstDelays.maxNotEmpty(),
            alphaDelayResults.greedyLocalFirstDelays.maxNotEmpty(),
            alphaDelayResults.stochasticDelays.maxNotEmpty()
        ).maxNotEmpty()

        val constantIndex = alphaDelayResults.alphaRanges.indexOfFirst { it is AlphaRange.Constant }
        val variableIndex = alphaDelayResults.alphaRanges.indexOfFirst { it is AlphaRange.Variable }

        val constantAlpha = alphaDelayResults.alphaRanges[constantIndex] as AlphaRange.Constant
        val variableAlpha = alphaDelayResults.alphaRanges[variableIndex] as AlphaRange.Variable

        val alphas = cartesianProduct(alphaDelayResults.alphaRanges.map { it.toList() }).map { it[variableIndex] }

        val plot = Plot.create()
        plot.plot().add(alphas, alphaDelayResults.localOnlyDelays).color("tab:olive").label("Local Only")
        plot.plot().add(alphas, alphaDelayResults.offloadOnlyDelays).color("blue").label("Offload Only")
        plot.plot().add(alphas, alphaDelayResults.greedyOffloadFirstDelays).color("green").label("Greedy (Offload First)")
        plot.plot().add(alphas, alphaDelayResults.greedyLocalFirstDelays).color("cyan").label("Greedy (Local First)")
        plot.plot().add(alphas, alphaDelayResults.stochasticDelays).color("red").label("Optimal Stochastic")

        plot.xlabel("The average arrival rate (alpha[$variableIndex])")
        plot.ylabel("The average delay")
        plot.title("Average delay for policies with alpha[$constantIndex] = ${constantAlpha.value}")
        plot.ylim(0, min(50, maxValue.toInt()))
        plot.xlim(variableAlpha.start, variableAlpha.end)
        plot.legend()
        writeToFile(plot)
    }

    fun writeToFile(plt: Plot) {
        var fileIndex = 1
        while (File("figure-output/fig$fileIndex.png").exists()) {
            fileIndex++
        }

        plt.savefig("figure-output/fig$fileIndex.png")
    }
}