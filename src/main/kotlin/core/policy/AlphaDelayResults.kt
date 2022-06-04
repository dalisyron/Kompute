package core.policy

import core.policy.AlphaRange

data class AlphaDelayResults(
    val alphaRanges: List<AlphaRange>,
    val localOnlyDelays: List<Double>,
    val offloadOnlyDelays: List<Double>,
    val greedyOffloadFirstDelays: List<Double>,
    val greedyLocalFirstDelays: List<Double>,
    val stochasticDelays: List<Double>,
)