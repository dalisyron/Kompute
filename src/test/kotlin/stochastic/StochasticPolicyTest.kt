package stochastic

import com.google.common.truth.Truth.assertThat
import core.policy.GreedyOffloadFirstPolicy
import environment.EnvironmentParameters
import org.junit.jupiter.api.Test
import policy.Action
import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import stochastic.policy.StochasticOffloadingPolicy
import ue.OffloadingSystemConfig
import ue.OffloadingSystemConfig.Companion.withEta
import ue.UserEquipmentComponentsConfig
import ue.UserEquipmentConfig
import ue.UserEquipmentStateConfig

class StochasticPolicyTest {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 30, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 17
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = 0.2,
                beta = 0.4,
                eta = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.5,
                pLoc = 1.5,
                nLocal = 17,
                pMax = 500.0
            )
        )
        val systemCofig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters,
            allActions = setOf(
                Action.NoOperation,
                Action.AddToCPU,
                Action.AddToTransmissionUnit,
                Action.AddToBothUnits
            )
        )

        return systemCofig
    }

    @Test
    fun testPolicyAverageBetterThanGreedyOffloadFirst() {
        val simpleConfig = getSimpleConfig()
        val optimalPolicyFinder = OptimalPolicyFinder(simpleConfig)
        val stochasticPolicy = optimalPolicyFinder.findOptimalPolicy(100) // 25 was 7.53 // 100 was 7.92 // 25 was 7.50 // 100 was 7.95

        val greedyOffloadFirstPolicy = GreedyOffloadFirstPolicy

        val simulator = Simulator(simpleConfig)
        val averageDelayStochastic = simulator.simulatePolicy(stochasticPolicy, 2000_000).averageDelay
        val averageDelayGof = simulator.simulatePolicy(greedyOffloadFirstPolicy, 2000_000).averageDelay

        println("Average delay for stochastic = $averageDelayStochastic with eta=${stochasticPolicy.stochasticPolicyConfig.eta} | Average delay for greedy = $averageDelayGof")

        assertThat(averageDelayStochastic)
            .isLessThan(averageDelayGof)
    }

    @Test
    fun testCompareSimulationWithLPForEta() {
        val etas = (1..40).map { it * 2.0 / 100.0}

        etas.forEach {
            val baseConfig = getSimpleConfig()
            val config = baseConfig.withEta(it)
            val optimalPolicyFinder = OptimalPolicyFinder(config)
            val stochConfig = optimalPolicyFinder.getOptimalWithEta(it)
            val stochasticPolicy = StochasticOffloadingPolicy(stochConfig, config)
            val averageDelayEstimate = stochConfig.averageDelay
            val simulator = Simulator(config)

            val averageDelayStochastic = simulator.simulatePolicy(stochasticPolicy, 2000_000).averageDelay

            println("For eta = $it | Stochastic delay estimate = $averageDelayEstimate | Simulation delay = $averageDelayStochastic")
        }
    }
}