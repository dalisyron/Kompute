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
        val stochConfig = optimalPolicyFinder.findOptimalPolicy(100) // 25 was 7.53 // 100 was 7.92 // 25 was 7.50 // 100 was 7.95

        val stochasticPolicy = StochasticOffloadingPolicy(stochConfig, simpleConfig)
        val greedyOffloadFirstPolicy = GreedyOffloadFirstPolicy(simpleConfig)

        val simulator = Simulator(
            environmentParameters = simpleConfig.environmentParameters,
            userEquipmentConfig = simpleConfig.userEquipmentConfig
        )
        val averageDelayStochastic = simulator.simulatePolicy(stochasticPolicy, 2000_000).averageDelay
        val averageDelayGof = simulator.simulatePolicy(greedyOffloadFirstPolicy, 2000_000).averageDelay

        println("Average delay for stochastic = $averageDelayStochastic | Average delay for greedy = $averageDelayGof")

        assertThat(averageDelayStochastic)
            .isLessThan(averageDelayGof)
    }
}