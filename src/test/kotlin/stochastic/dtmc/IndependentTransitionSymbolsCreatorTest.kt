package stochastic.dtmc

import com.google.common.truth.Truth.assertThat
import core.UserEquipmentStateManager
import core.environment.EnvironmentParameters
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withUserEquipmentStateConfig
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.symbol.ParameterSymbol
import core.symbol.Symbol
import org.junit.Test
import core.policy.Action
import core.ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig

class IndependentTransitionCalculatorTest {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 5,
                tuNumberOfPackets = 4,
                cpuNumberOfSections = 3
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = 0.1,
                beta = 0.9,
                eta = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.5,
                pLocal = 1.5,
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

    fun getSymbolMapping(systemConfig: OffloadingSystemConfig): Map<Symbol, Double> {
        val symbolMapping: Map<Symbol, Double> = run {
            mapOf(
                ParameterSymbol.Beta to systemConfig.beta,
                ParameterSymbol.BetaC to 1.0 - systemConfig.beta,
                ParameterSymbol.Alpha to systemConfig.alpha,
                ParameterSymbol.AlphaC to 1.0 - systemConfig.alpha
            )
        }
        return symbolMapping
    }

    @Test
    fun testcase1() {
        val systemConfig = getSimpleConfig()
        val userEquipmentStateManager = UserEquipmentStateManager(systemConfig.getStateManagerConfig())
        val symbolMapping = getSymbolMapping(systemConfig)
        val discreteTimeMarkovChain: DiscreteTimeMarkovChain = DTMCCreator(systemConfig.getStateManagerConfig()).create()
        val transitionCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)

        val itValue = transitionCalculator.getIndependentTransitionFraction(
            UserEquipmentState(2, 0, 0),
            UserEquipmentState(1, 1, 0),
            Action.AddToTransmissionUnit
        )

        val expectedResult = (1.0 - systemConfig.beta) * (1.0 - systemConfig.alpha)

        assertThat(itValue)
            .isWithin(1e-6)
            .of(expectedResult)
    }

    @Test
    fun testDoubleLabel() {
        val stateConfig = UserEquipmentStateConfig(
            taskQueueCapacity = 10, // set to some big number,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        )
        val systemConfig = getSimpleConfig().withUserEquipmentStateConfig(stateConfig)
        val symbolMapping = getSymbolMapping(systemConfig)

        val userEquipmentStateManager = UserEquipmentStateManager(systemConfig.getStateManagerConfig())
        val discreteTimeMarkovChain: DiscreteTimeMarkovChain = DTMCCreator(systemConfig.getStateManagerConfig()).create()
        val itCalculator = IndependentTransitionCalculator(symbolMapping, discreteTimeMarkovChain)

        val itValue = itCalculator.getIndependentTransitionFraction(
            source = UserEquipmentState(1, 0, 0),
            dest = UserEquipmentState(1, 0, 0),
            action = Action.AddToTransmissionUnit
        )
        val expectedValue = systemConfig.alpha * systemConfig.beta

        assertThat(itValue)
            .isWithin(1e-6)
            .of(expectedValue)
    }

    private fun List<Symbol>.makeUnique() = sortedBy { it.toString() }
}