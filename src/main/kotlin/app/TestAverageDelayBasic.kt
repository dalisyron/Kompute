package app

import com.github.sh0nk.matplotlib4j.Plot
import environment.EnvironmentParameters
import policy.*
import simulation.Simulator
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
                userEquipmentConfig = baseUserEquipmentConfig.copy(componentsConfig = baseUserEquipmentConfig.componentsConfig.copy(alpha = alpha))
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

fun main() {
    val alphas: List<Double> = (1..400).map { it / 1000.0 }

    val environmentParameters = EnvironmentParameters(
        nCloud = 1,
        tRx = 0.0,
        eta = 0.0, // Not used in the baseline policies, set to whatever
        pTx = 1.0,
        pLoc = 0.8,
        nLocal = 17
    )
    val userEquipmentConfig = UserEquipmentConfig(
        stateConfig = UserEquipmentStateConfig(
            taskQueueCapacity = 10000000, // set to some big number,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        ),
        componentsConfig = UserEquipmentComponentsConfig(
            alpha = 0.0,
            beta = 0.4
        )
    )

    val tester = AverageDelayTester(
        environmentParameters,
        userEquipmentConfig,
        100_000,
        alphas
    )

    val localOnlyDelays = tester.getAverageDelaysForPolicy(LocalOnlyPolicy)
    val transmitOnlyDelays = tester.getAverageDelaysForPolicy(TransmitOnlyPolicy)
    val greedyOffloadFirstDelays = tester.getAverageDelaysForPolicy(GreedyOffloadFirstPolicy)
    val greedyLocalFirstDelays = tester.getAverageDelaysForPolicy(GreedyLocalFirstPolicy)

    val plot = Plot.create()
    plot.plot().add(alphas, localOnlyDelays).color("red").label("Local Only")
    plot.plot().add(alphas, transmitOnlyDelays).color("blue").label("Offload Only")
    plot.plot().add(alphas, greedyOffloadFirstDelays).color("green").label("Greedy (Offload First)")
    plot.plot().add(alphas, greedyLocalFirstDelays).color("cyan").label("Greedy (Local First)")

    plot.xlabel("The average arrival rate (alpha)")
    plot.ylabel("The average delay")
    plot.title("Average delay for policies")
    plot.ylim(0, 25)
    plot.xlim(0, 0.4)
    plot.legend()
    plot.show()
}
