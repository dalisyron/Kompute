package stochastic

import com.google.common.truth.Truth.assertThat
import core.policy.GreedyOffloadFirstPolicy
import core.environment.EnvironmentParameters
import core.policy.GreedyLocalFirstPolicy
import core.policy.LocalOnlyPolicy
import core.policy.TransmitOnlyPolicy
import org.junit.Test
import simulation.simulation.Simulator
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlphaSingleQueue
import core.ue.OffloadingSystemConfig.Companion.withBeta
import core.ue.OffloadingSystemConfig.Companion.withEtaConfigSingleQueue
import core.ue.OffloadingSystemConfig.Companion.withNumberOfPacketsSingleQueue
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSectionsSingleQueue
import core.ue.OffloadingSystemConfig.Companion.withPLocal
import core.ue.OffloadingSystemConfig.Companion.withPMax
import core.ue.OffloadingSystemConfig.Companion.withPTx
import core.ue.OffloadingSystemConfig.Companion.withTRx
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.Assertions
import simulation.app.Mock
import stochastic.lp.IneffectivePolicyException
import stochastic.lp.RangedOptimalPolicyFinder
import stochastic.policy.FixedEtaEstimateSimulationCompareTest
import stochastic.policy.RangedAlphaStochasticPolicyTester
import stochastic.policy.StochasticOffloadingPolicy

interface SlowTests

class StochasticPolicyTest {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig.singleQueue(
                taskQueueCapacity = 15, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 17
            ),
            componentsConfig = UserEquipmentComponentsConfig.singleQueue(
                alpha = 0.2,
                beta = 0.4,
                etaConfig = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.5,
                pLocal = 1.5,
                pMax = 500.0
            )
        )
        val systemCofig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters
        )

        return systemCofig
    }

    @Test
    @Category(SlowTests::class)
    fun testPolicyAverageBetterThanGreedyOffloadFirst() {
        val simpleConfig = getSimpleConfig().withTaskQueueCapacity(10).withEtaConfigSingleQueue(1e-9)
        val stochasticPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(
            simpleConfig,
            50
        ) // 25 was 7.53 // 100 was 7.92 // 25 was 7.50 // 100 was 7.95

        val greedyOffloadFirstPolicy = GreedyOffloadFirstPolicy

        val simulator = Simulator(simpleConfig)
        val averageDelayStochastic = simulator.simulatePolicy(stochasticPolicy, 2000_000).averageDelay
        val averageDelayGof = simulator.simulatePolicy(greedyOffloadFirstPolicy, 2000_000).averageDelay

        println("Average delay for stochastic = $averageDelayStochastic with eta=${stochasticPolicy.stochasticPolicyConfig.etaConfig} | Average delay for greedy = $averageDelayGof")

        assertThat(averageDelayStochastic)
            .isLessThan(averageDelayGof)
    }

    @Test
    @Category(SlowTests::class)
    fun testCompareSimulationWithLPForEta() {
        val baseConfig = getSimpleConfig().withTaskQueueCapacity(20)

        val tester = FixedEtaEstimateSimulationCompareTest(
            etaStart = 0.1,
            etaEnd = 0.28,
            sampleCount = 28,
            baseSystemConfig = baseConfig,
            simulationTicks = 10_000_000,
            tolerance = 2e-2
        )

        tester.runTest()
    }

    @Test
    @Category(SlowTests::class)
    fun testCompareSimulationWithEtaAnomaly() {
        // Found in previous test runs
        val baseConfig = getSimpleConfig()
                .withEtaConfigSingleQueue(0.6)
                .withAlphaSingleQueue(0.2)
                .withBeta(0.6)
                .withNumberOfSectionsSingleQueue(3)
                .withTaskQueueCapacity(100)
                .withPMax(200.0)

        val tester = FixedEtaEstimateSimulationCompareTest(
            etaStart = 0.6,
            etaEnd = 0.6,
            sampleCount = 1,
            baseSystemConfig = baseConfig,
            simulationTicks = 2_000_000,
            tolerance = 2e-2
        )

        tester.runTest()
    }

    @Test
    fun testCompareWithBaselines() {
        val alphas = (1..40).map { it / 100.0 }
        val baseConfig = Mock.configFromLiyu()
        val simulationTicks = 1_000_000

        for (alpha in alphas) {
            val config = baseConfig.withAlphaSingleQueue(alpha)
            val simulator = Simulator(config)
            val stochastic = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(config, 100)

            val localOnlyDelay = simulator.simulatePolicy(LocalOnlyPolicy, simulationTicks).averageDelay
            val offloadOnlyDelay = simulator.simulatePolicy(TransmitOnlyPolicy, simulationTicks).averageDelay
            val greedyOffloadFirstDelay =
                simulator.simulatePolicy(GreedyOffloadFirstPolicy, simulationTicks).averageDelay
            val greedyLocalFirstDelay = simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks).averageDelay
            val stochasticDelay = simulator.simulatePolicy(stochastic, simulationTicks).averageDelay

            val errorWindowMultiplier = 0.99

            with(assertThat(stochasticDelay * errorWindowMultiplier)) {
                isLessThan(localOnlyDelay)
                isLessThan(offloadOnlyDelay)
                isLessThan(greedyOffloadFirstDelay)
                isLessThan(greedyLocalFirstDelay)
            }
        }
    }

    @Test
    fun offloadOnlyStochastic() {
        val config = Mock.configFromLiyu().withEtaConfigSingleQueue(0.0).withAlphaSingleQueue(0.01)
        val simulator = Simulator(config)
        val simulationTicks = 10_000_000
        val stochasticPolicy: StochasticOffloadingPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(config)

        val offloadOnlyDelay = simulator.simulatePolicy(TransmitOnlyPolicy, simulationTicks).averageDelay
        val stochasticDelay = simulator.simulatePolicy(stochasticPolicy, simulationTicks).averageDelay
        // println(stochasticPolicy)

        assertThat(stochasticDelay)
            .isLessThan(offloadOnlyDelay)
    }

    @Test
    fun rangedTest1() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu().withBeta(0.2).withNumberOfSectionsSingleQueue(5).withPMax(1.4),
            alphaStart = 0.01,
            alphaEnd = 1.0,
            alphaSampleCount = 50,
            precision = 100,
            simulationTicks = 2_000_000,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    @Test
    fun testRanged1() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu()
                .withBeta(0.2)
                .withNumberOfSectionsSingleQueue(5)
                .withPMax(1.4)
                .withTaskQueueCapacity(30),
            alphaStart = 0.01,
            alphaEnd = 0.35,
            alphaSampleCount = 50,
            precision = 200,
            simulationTicks = 2_000_000,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    @Test
    fun testRanged2() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu()
                .withBeta(0.2)
                .withNumberOfSectionsSingleQueue(5)
                .withPMax(1.4)
                .withTaskQueueCapacity(50),
            alphaStart = 0.35,
            alphaEnd = 0.38,
            alphaSampleCount = 8,
            precision = 1000,
            simulationTicks = 5_000_000,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    fun testRangedPlot1() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu()
                .withBeta(0.2)
                .withNumberOfSectionsSingleQueue(5)
                .withPMax(1.4)
                .withTaskQueueCapacity(20),
            alphaStart = 0.01,
            alphaEnd = 0.38,
            alphaSampleCount = 50,
            precision = 100,
            simulationTicks = 1_000_000,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    @Test
    fun testRangedPlot2() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu()
                .withBeta(0.8)
                .withNumberOfPacketsSingleQueue(2)
                .withNumberOfSectionsSingleQueue(5)
                .withPMax(1.4)
                .withTaskQueueCapacity(50),
            alphaStart = 0.57,
            alphaEnd = 0.595,
            alphaSampleCount = 4,
            precision = 300,
            simulationTicks = 1_000_000,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    @Test
    fun testRangedPlot3() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu()
                .withBeta(0.8)
                .withNumberOfPacketsSingleQueue(2)
                .withNumberOfSectionsSingleQueue(8)
                .withPMax(0.8)
                .withTRx(3.0)
                .withTaskQueueCapacity(25),
            alphaStart = 0.01,
            alphaEnd = 0.30,
            alphaSampleCount = 20,
            precision = 200,
            simulationTicks = 1_000_000,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    @Test
    fun testFeasible1() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu()
                .withPMax(2.5)
                .withBeta(0.99)
                .withNumberOfPacketsSingleQueue(3)
                .withNumberOfSectionsSingleQueue(9)
                .withPLocal(4.0 / 3.0)
                .withPTx(2.0)
                .withTaskQueueCapacity(25),
            alphaStart = 0.01,
            alphaEnd = 0.37,
            alphaSampleCount = 37,
            precision = 100,
            simulationTicks = 2_000_000,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    @Test
    fun testDelayOffloadOnly() {
        val systemConfig = Mock.configFromLiyu()
            .withPMax(0.75)
            .withAlphaSingleQueue(0.6)
            .withBeta(0.99)
            .withNumberOfPacketsSingleQueue(1)
            .withNumberOfSectionsSingleQueue(6)
            .withPLocal(0.5 / 6.0)
            .withPTx(1.5)
            .withTaskQueueCapacity(30)

        val stochasticPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(systemConfig, 100)
        val simulator = Simulator(systemConfig)
        val stochasticPolicyDelayActual = simulator.simulatePolicy(stochasticPolicy, 1_000_000).averageDelay
        val offloadOnlyDelay = simulator.simulatePolicy(TransmitOnlyPolicy, 1_000_000).averageDelay

        assertThat(stochasticPolicyDelayActual * 0.99)
            .isLessThan(offloadOnlyDelay)
    }

    @Test
    fun testEdgeCaseGreedyLocalBetter() {
        val systemConfig = Mock.configFromLiyu().withBeta(0.2)
            .withNumberOfSectionsSingleQueue(5)
            .withTaskQueueCapacity(30)
            .withPMax(1.8)
            .withAlphaSingleQueue(0.2)

        val simulationTicks = 2_000_000
        val simulator = Simulator(systemConfig)
        val optimalStochasticPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(
            baseSystemConfig = systemConfig,
            precision = 100
        )

        val stochasticDelay = simulator.simulatePolicy(optimalStochasticPolicy, simulationTicks).averageDelay
        val greedyLocalDelay = simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks).averageDelay

        assertThat(stochasticDelay * 0.99)
            .isLessThan(greedyLocalDelay)
    }

    @Test
    fun testIneffectivePolicyException() {
        val systemConfig = Mock.configFromLiyu()
            .withPMax(0.75)
            .withAlphaSingleQueue(0.999)
            .withBeta(0.99)
            .withEtaConfigSingleQueue(0.0)
            .withNumberOfPacketsSingleQueue(1)
            .withNumberOfSectionsSingleQueue(6)
            .withPLocal(0.5 / 6.0)
            .withPTx(1.5)
            .withTaskQueueCapacity(40)

        Assertions.assertThrows(IneffectivePolicyException::class.java) {
            val stochasticOffloadOnly = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(systemConfig)
        }
    }

    @Test
    fun testFeasible2() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu()
                .withPMax(0.75)
                .withBeta(0.99)
                .withNumberOfPacketsSingleQueue(1)
                .withNumberOfSectionsSingleQueue(6)
                .withPLocal(0.5 / 6.0)
                .withPTx(1.5)
                .withTaskQueueCapacity(25),
            alphaStart = 0.41,
            alphaEnd = 0.70,
            alphaSampleCount = 50,
            precision = 100,
            simulationTicks = 3_000_000,
            plotEnabled = true,
            assertionsEnabled = false
        )

        tester.run()
    }

    @Test
    fun testSampleFromLiyuUnlimitedPower() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu().withTaskQueueCapacity(20).withPMax(30.0),
            alphaStart = 0.02,
            alphaEnd = 0.40,
            alphaSampleCount = 40,
            precision = 100,
            simulationTicks = 1000_000,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    @Test
    fun testSampleFromLiyuLimitedPower() {
        val tester = RangedAlphaStochasticPolicyTester(
            baseSystemConfig = Mock.configFromLiyu().withTaskQueueCapacity(20).withPMax(1.2),
            alphaStart = 0.01,
            alphaEnd = 0.40,
            alphaSampleCount = 80,
            precision = 200,
            simulationTicks = 4_000_000 ,
            plotEnabled = true,
            assertionsEnabled = true
        )

        tester.run()
    }

    @Test
    fun testSingleEta() {
        val stochasticPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(
            Mock.configFromLiyu().withAlphaSingleQueue(0.10)
                .withEtaConfigSingleQueue(0.05)
        )

        println(stochasticPolicy)
    }
}