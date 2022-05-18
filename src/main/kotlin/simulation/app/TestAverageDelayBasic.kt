package simulation.app

import com.github.sh0nk.matplotlib4j.Plot
import core.policy.*
import environment.EnvironmentParameters
import policy.Action
import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import stochastic.policy.StochasticOffloadingPolicy
import ue.OffloadingSystemConfig
import ue.UserEquipmentComponentsConfig
import ue.UserEquipmentConfig
import ue.UserEquipmentStateConfig
import java.math.RoundingMode

class AverageDelayTester(
    val environmentParameters: EnvironmentParameters,
    val baseUserEquipmentConfig: UserEquipmentConfig,
    val ticks: Int,
    val alphas: List<Double>
) {

    fun getAverageDelaysForPolicy(policy: Policy): List<Double> {
        var lastPercent = 0.0
        return alphas.mapIndexed { i: Int, alpha: Double ->
            val simulator = Simulator(
                environmentParameters = environmentParameters,
                userEquipmentConfig = baseUserEquipmentConfig.copy(
                    componentsConfig = baseUserEquipmentConfig.componentsConfig.copy(
                        alpha = alpha
                    )
                )
            )
            val progress = (i.toDouble() * 100.0 / alphas.size).toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
            if (progress > lastPercent) {
                println("$progress%")
                lastPercent += 10.0
            }
            simulator.simulatePolicy(policy, ticks).averageDelay
        }.also {
            println("Simulation finished for ${policy::class.java.simpleName}")
        }
    }
}

interface PolicyCreator {

    fun createPolicy(systemConfig: OffloadingSystemConfig): Policy
}

class RangedAlphaAverageDelayTest(
    val alphaStart: Double,
    val alphaEnd: Double,
    val sampleCount: Int,
    val baseSystemConfig: OffloadingSystemConfig,
    val ticks: Int,
    val label: String,
    val policyCreator: PolicyCreator
) {

    fun getAverageDelays(): List<Double> {
        var lastPercent = 0.0
        val alphas = (1..sampleCount).map { alphaStart + ((alphaEnd - alphaStart) / sampleCount) * it }

        return alphas.mapIndexed { i: Int, alpha: Double ->
            val policy = policyCreator.createPolicy(baseSystemConfig.copy(userEquipmentConfig = baseSystemConfig.userEquipmentConfig.copy(
                componentsConfig = baseSystemConfig.userEquipmentConfig.componentsConfig.copy(
                    alpha = alpha
                )
            )))
            val simulator = Simulator(
                environmentParameters = baseSystemConfig.environmentParameters,
                userEquipmentConfig = baseSystemConfig.userEquipmentConfig.copy(
                    componentsConfig = baseSystemConfig.userEquipmentConfig.componentsConfig.copy(
                        alpha = alpha
                    )
                )
            )
            val progress = (i.toDouble() * 100.0 / alphas.size).toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
            if (progress > lastPercent) {
                println("$progress%")
                lastPercent += 10.0
            }
            simulator.simulatePolicy(policy, ticks).averageDelay
        }.also {
            println("Simulation finished for policy $label")
        }
    }
}

fun main() {
    val alphas: List<Double> = (1..100).map { (it * 8.0) / 1000.0 }

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
            alpha = 0.0,
            beta = 0.4,
            eta = 0.0, // Not used in the baseline policies, set to whatever
            pTx = 1.5,
            pLoc = 1.5,
            nLocal = 17,
            pMax = 1.1
        )
    )

    val systemConfig = OffloadingSystemConfig(
        userEquipmentConfig = userEquipmentConfig,
        environmentParameters = environmentParameters,
        allActions = setOf(
            Action.NoOperation,
            Action.AddToCPU,
            Action.AddToTransmissionUnit,
            Action.AddToBothUnits
        )
    )

    val tester = AverageDelayTester(
        environmentParameters,
        userEquipmentConfig,
        100_000,
        alphas
    )
    // val optimalPolicyConfig = OptimalPolicyFinder(systemConfig).findOptimalPolicy(100)
    // val stochasticPolicy = StochasticOffloadingPolicy(optimalPolicyConfig, systemConfig)

    val localOnlyDelays = tester.getAverageDelaysForPolicy(LocalOnlyPolicy(systemConfig))
    val transmitOnlyDelays = tester.getAverageDelaysForPolicy(TransmitOnlyPolicy(systemConfig))
    val greedyOffloadFirstDelays = tester.getAverageDelaysForPolicy(GreedyOffloadFirstPolicy(systemConfig))
    val greedyLocalFirstDelays = tester.getAverageDelaysForPolicy(GreedyLocalFirstPolicy(systemConfig))

    val stochasticTester = RangedAlphaAverageDelayTest(
        alphaStart = 0.0,
        alphaEnd = 0.4,
        sampleCount = 50,
        baseSystemConfig = systemConfig,
        ticks = 100_000,
        label = "Stochastic",
        policyCreator = object : PolicyCreator {
            override fun createPolicy(systemConfig: OffloadingSystemConfig): Policy {
                val stochConfig = OptimalPolicyFinder(systemConfig).findOptimalPolicy(80)
                return StochasticOffloadingPolicy(stochConfig, systemConfig)
            }

        }
    )
    val stochasticDelays = listOf<Double>(17.0792824437057, 16.416448303078138, 15.652369025255688, 14.834542200452148, 13.85676894176581, 13.193114364859516, 12.414933609550937, 11.93797939436675, 11.405338385667717, 10.787133637573211, 10.496883894535312, 10.172623483004966, 9.809254992319508, 9.42690783837807, 9.373484908335655, 8.933815683922772, 8.837257431893455, 8.7269338659288, 8.591785202989348, 8.482601947932313, 8.32251132639722, 8.154518801640004, 7.997405568944904, 7.824701651689577, 7.6819463028499255, 7.8274260771749, 7.655665704024584, 7.82183491112505, 7.658905634009368, 7.847530997933471, 7.638409478531746, 7.827024846850498, 7.653139033412187, 7.813505178365938, 7.7079609870254036, 7.861439036544562, 8.097251268028796, 7.9651182105998934, 8.16282793921792, 8.45970615367026, 8.366812193721053, 8.643834758650302, 8.893465146624346, 8.947630621067281, 9.213299375113078, 9.647675777547766, 9.718611310522986, 10.161856906133753, 10.613419257597894, 11.111180277155214) + (1..50).map { 11.1111 }

    val plot = Plot.create()
    plot.plot().add(alphas, localOnlyDelays).color("red").label("Local Only")
    plot.plot().add(alphas, transmitOnlyDelays).color("blue").label("Offload Only")
    plot.plot().add(alphas, greedyOffloadFirstDelays).color("green").label("Greedy (Offload First)")
    plot.plot().add(alphas, greedyLocalFirstDelays).color("cyan").label("Greedy (Local First)")
    plot.plot().add(alphas, stochasticDelays).color("purple").label("Optimal Stochastic")

    plot.xlabel("The average arrival rate (alpha)")
    plot.ylabel("The average delay")
    plot.title("Average delay for policies")
    plot.ylim(0, 25)
    plot.xlim(0, 0.8)
    plot.legend()
    plot.show()
}
