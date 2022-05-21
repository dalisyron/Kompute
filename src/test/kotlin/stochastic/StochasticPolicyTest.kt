package stochastic

import com.google.common.truth.Truth.assertThat
import core.policy.GreedyOffloadFirstPolicy
import core.environment.EnvironmentParameters
import org.junit.Ignore
import org.junit.jupiter.api.Test
import policy.Action
import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import stochastic.lp.StochasticPolicyConfig
import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import core.ue.OffloadingSystemConfig.Companion.withBeta
import core.ue.OffloadingSystemConfig.Companion.withEta
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withPMax
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig

class StochasticPolicyTest {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 15, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 17
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = 0.2,
                beta = 0.4,
                eta = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.5,
                pLoc = 1.5,
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
    @Ignore("Time-consuming test. Ignored to improve speed of running the entire test suit.")
    fun testPolicyAverageBetterThanGreedyOffloadFirst() {
        val simpleConfig = getSimpleConfig()
        val optimalPolicyFinder = OptimalPolicyFinder(simpleConfig)
        val stochasticPolicy =
            optimalPolicyFinder.findOptimalPolicy(50) // 25 was 7.53 // 100 was 7.92 // 25 was 7.50 // 100 was 7.95

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
        val etas = (1..40).map { it * 2.0 / 100.0 }

        etas.forEach {
            println("testing for eta = $it")
            val baseConfig = getSimpleConfig().withTaskQueueCapacity(70)
            val config = baseConfig.withEta(it)
            val optimalPolicyFinder = OptimalPolicyFinder(config)
            val stochConfig: StochasticPolicyConfig = optimalPolicyFinder.findOptimalWithGivenEta(it)
            val stochasticPolicy = StochasticOffloadingPolicy(stochConfig, config)
            val averageDelayEstimate = stochConfig.averageDelay
            val simulator = Simulator(config)

            val averageDelaySimulation = simulator.simulatePolicy(stochasticPolicy, 20_000_000).averageDelay

            println("estimate = $averageDelayEstimate | simulation = $averageDelaySimulation")

            assertThat(averageDelayEstimate)
                .isWithin(1e-2)
                .of(averageDelaySimulation)
        }
    }

    @Test
    fun testCompareSimulationWithEtaAnomaly() {
        // Found in previous test runs
        val eta = 0.3

        assertSimulationEqualsEstimate(
            getSimpleConfig()
                .withEta(1.0)
                .withAlpha(0.2)
                .withBeta(0.6)
                .withNumberOfSections(17)
                .withTaskQueueCapacity(50)
                .withPMax(200.0)
        )
    }

    fun assertSimulationEqualsEstimate(config: OffloadingSystemConfig) {
        val optimalPolicyFinder = OptimalPolicyFinder(config)
        val stochConfig: StochasticPolicyConfig = optimalPolicyFinder.findOptimalWithGivenEta(config.eta)
        val stochasticPolicy = StochasticOffloadingPolicy(stochConfig, config)
        println("Policy : $stochasticPolicy")
        val averageDelayEstimate = stochConfig.averageDelay
        val simulator = Simulator(config)

        val averageDelaySimulation = simulator.simulatePolicy(stochasticPolicy, 4_000_000).averageDelay

        println("estimate = $averageDelayEstimate | simulation = $averageDelaySimulation")

        assertThat(averageDelayEstimate)
            .isWithin(1e-2)
            .of(averageDelaySimulation)
    }

}