package stochastic.policy

import com.google.common.truth.Truth
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withEtaConfig
import core.ue.OffloadingSystemConfig.Companion.withEtaConfigSingleQueue
import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder

class FixedEtaEstimateSimulationCompareTest(
    private val etaStart: Double,
    private val etaEnd: Double,
    private val sampleCount: Int,
    private val baseSystemConfig: OffloadingSystemConfig,
    private val simulationTicks: Int,
    private val tolerance: Double
) {

    fun runTest() {
        check(sampleCount >= 1)
        check(baseSystemConfig.numberOfQueues == 1)

        val etas = if (sampleCount == 1) {
            check(etaEnd == etaStart)
            listOf(etaStart)
        } else {
            (0 until sampleCount).map { etaStart + it * ((etaEnd - etaStart) / (sampleCount - 1)) }
        }

        etas.forEach {
            println("testing for eta = $it")
            val config = baseSystemConfig.withEtaConfigSingleQueue(it)
            val optimalPolicy = OptimalPolicyFinder.findOptimalPolicy(config)

            val averageDelayEstimate = optimalPolicy.averageDelay
            val simulator = Simulator(config)

            val simulationReport = simulator.simulatePolicy(optimalPolicy, simulationTicks)
            val averageDelaySimulation = simulationReport.averageDelay
            val averagePowerConsumption = simulationReport.averagePowerConsumption

            println("delay estimate = $averageDelayEstimate | delay simulation = $averageDelaySimulation")
            println("pMax = ${config.pMax} | average power simulation = $averagePowerConsumption")


            Truth.assertThat(averageDelayEstimate)
                .isWithin(tolerance)
                .of(averageDelaySimulation)
        }
    }
}