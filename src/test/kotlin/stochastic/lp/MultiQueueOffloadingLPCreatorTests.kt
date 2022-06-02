package stochastic.lp

import com.google.common.truth.DoubleSubject
import com.google.common.truth.Truth.assertThat
import core.UserEquipmentStateManager
import core.environment.EnvironmentParameters
import core.mutableListOfZeros
import core.pow
import core.ue.*
import org.junit.Test

class MultiQueueOffloadingLPCreatorTests {

    companion object {
        fun DoubleSubject.isApproxEqualTo(value: Double) {
            return this.isWithin(eps).of(value)
        }

        const val eps = 1e-6
    }

    private fun Map<Int, Double>.orderedValues(): List<Double> {
        return entries.sortedBy { it.key }.map { it.value }
    }

    val systemCofig = getSimpleConfig()
    val creator = OffloadingLPCreator(systemConfig = systemCofig)
    val offloadingLinearProgram = creator.createOffloadingLinearProgram()
    val lp = offloadingLinearProgram.standardLinearProgram

    fun validateCoefficients(rowIndex: Int, expectedCoefficients: List<Double>) {
        val actualCoefficients = lp.rows[rowIndex]!!.coefficients

        actualCoefficients.forEachIndexed { index, d ->
            println("$index | ${offloadingLinearProgram.indexMapping.stateActionByCoefficientIndex[index]}")
            assertThat(d)
                .isApproxEqualTo(expectedCoefficients[index])
        }
    }

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig.singleQueue(
                taskQueueCapacity = 2,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 2
            ),
            componentsConfig = UserEquipmentComponentsConfig.singleQueue(
                alpha = 0.30,
                beta = 0.4,
                etaConfig = 0.2,
                pTx = 1.0,
                pLocal = 0.8,
                pMax = 200.0
            )
        )
        val systemCofig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters
        )

        return systemCofig
    }

    @Test
    fun testAllStatesUnchecked() {
        val systemConfig = getSimpleConfig()
        val stateManager = UserEquipmentStateManager.fromSystemConfig(systemConfig)
        val allStates = stateManager.allStatesUnchecked()
        val numberOfPackets = systemConfig.tuNumberOfPackets[0]
        val cpuNumberOfSections = systemConfig.cpuNumberOfSections[0]
        val numberOfQueues = systemConfig.numberOfQueues

        var expectedSize =
            (systemConfig.taskQueueCapacity + 1).pow(systemConfig.numberOfQueues) * (numberOfPackets + 1) * (cpuNumberOfSections) * (numberOfQueues + 1) * (numberOfQueues + 1)

        assertThat(allStates.size)
            .isEqualTo(expectedSize)
    }

    @Test
    fun testAllStatesChecked() {
        val systemConfig = getSimpleConfig()
        val stateManager = UserEquipmentStateManager.fromSystemConfig(systemConfig)
        val allStates = stateManager.allStates()
        val expectedStates = mutableListOf<UserEquipmentState>()

        expectedStates.run {
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(0),
                    tuState = 0,
                    cpuState = 0,
                    tuTaskTypeQueueIndex = -1,
                    cpuTaskTypeQueueIndex = -1
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(0),
                    tuState = 0,
                    cpuState = 1,
                    tuTaskTypeQueueIndex = -1,
                    cpuTaskTypeQueueIndex = 0
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(0),
                    tuState = 1,
                    cpuState = 0,
                    tuTaskTypeQueueIndex = 0,
                    cpuTaskTypeQueueIndex = -1
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(0),
                    tuState = 1,
                    cpuState = 1,
                    tuTaskTypeQueueIndex = 0,
                    cpuTaskTypeQueueIndex = 0
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(1),
                    tuState = 0,
                    cpuState = 0,
                    tuTaskTypeQueueIndex = -1,
                    cpuTaskTypeQueueIndex = -1
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(1),
                    tuState = 0,
                    cpuState = 1,
                    tuTaskTypeQueueIndex = -1,
                    cpuTaskTypeQueueIndex = 0
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(1),
                    tuState = 1,
                    cpuState = 0,
                    tuTaskTypeQueueIndex = 0,
                    cpuTaskTypeQueueIndex = -1
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(1),
                    tuState = 1,
                    cpuState = 1,
                    tuTaskTypeQueueIndex = 0,
                    cpuTaskTypeQueueIndex = 0
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(2),
                    tuState = 0,
                    cpuState = 0,
                    tuTaskTypeQueueIndex = -1,
                    cpuTaskTypeQueueIndex = -1
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(2),
                    tuState = 0,
                    cpuState = 1,
                    tuTaskTypeQueueIndex = -1,
                    cpuTaskTypeQueueIndex = 0
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(2),
                    tuState = 1,
                    cpuState = 0,
                    tuTaskTypeQueueIndex = 0,
                    cpuTaskTypeQueueIndex = -1
                )
            )
            add(
                UserEquipmentState(
                    taskQueueLengths = listOf(2),
                    tuState = 1,
                    cpuState = 1,
                    tuTaskTypeQueueIndex = 0,
                    cpuTaskTypeQueueIndex = 0
                )
            )
        }

        assertThat(allStates)
            .containsExactlyElementsIn(expectedStates)
    }

    @Test
    fun testObjectiveCoefficients() {
        val systemCofig = getSimpleConfig()

        val creator = OffloadingLPCreator(systemConfig = systemCofig)

        /*
            Variable Table:

            Index  | State                  |   Action                            | Coefficient
            --------------------------------------------------------------------------------------------------------------------
            0      | ({ [0], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            1      | ({ [0], 0, 1, -1, 0 }	|	NoOperation)                      | 0.0
            2      | ({ [0], 1, 0, 0, -1 }	|	NoOperation)                      | 0.0
            3      | ({ [0], 1, 1, 0, 0 }	|	NoOperation)                      | 0.0
            4      | ({ [1], 0, 0, -1, -1 }	|	NoOperation)                      | 1.0
            5      | ({ [1], 0, 0, -1, -1 }	|	AddToCPU(0))                      | 1.0
            6      | ({ [1], 0, 0, -1, -1 }	|	AddToTransmissionUnit(0))         | 1.0
            7      | ({ [1], 0, 1, -1, 0 }	|	NoOperation)                      | 1.0
            8      | ({ [1], 0, 1, -1, 0 }	|	AddToTransmissionUnit(0))         | 1.0
            9      | ({ [1], 1, 0, 0, -1 }	|	NoOperation)                      | 1.0
            10     | ({ [1], 1, 0, 0, -1 }	|	AddToCPU(0))                      | 1.0
            11     | ({ [1], 1, 1, 0, 0 }	|	NoOperation)                      | 1.0
            12     | ({ [2], 0, 0, -1, -1 }	|	NoOperation)                      | 2.0
            13     | ({ [2], 0, 0, -1, -1 }	|	AddToCPU(0))                      | 2.0
            14     | ({ [2], 0, 0, -1, -1 }	|	AddToTransmissionUnit(0))         | 2.0
            15     | ({ [2], 0, 0, -1, -1 }	|	AddToBothUnits(cpu = 0, tu = 0))  | 2.0
            16     | ({ [2], 0, 1, -1, 0 }	|	NoOperation)                      | 2.0
            17     | ({ [2], 0, 1, -1, 0 }	|	AddToTransmissionUnit(0))         | 2.0
            18     | ({ [2], 1, 0, 0, -1 }	|	NoOperation)                      | 2.0
            19     | ({ [2], 1, 0, 0, -1 }	|	AddToCPU(0))                      | 2.0
            20     | ({ [2], 1, 1, 0, 0 }	|	NoOperation)                      | 2.0
         */
        val expectedCoefficients = mapOf(
            0 to 0.0,
            1 to 0.0,
            2 to 0.0,
            3 to 0.0,
            4 to 1.0,
            5 to 1.0,
            6 to 1.0,
            7 to 1.0,
            8 to 1.0,
            9 to 1.0,
            10 to 1.0,
            11 to 1.0,
            12 to 2.0,
            13 to 2.0,
            14 to 2.0,
            15 to 2.0,
            16 to 2.0,
            17 to 2.0,
            18 to 2.0,
            19 to 2.0,
            20 to 2.0
        ).orderedValues().map { it / systemCofig.alpha[0] }

        val offloadingLinearProgram = creator.createOffloadingLinearProgram()
        val lp = offloadingLinearProgram.standardLinearProgram

        assertThat(lp.rows[0])
            .isNotNull()

        assertThat(lp.rows[0]!!.coefficients)
            .hasSize(21)

        lp.rows[0]!!.coefficients.forEachIndexed { index, d ->
            println("index = $index")
            assertThat(d)
                .isWithin(eps)
                .of(expectedCoefficients[index])
        }
    }


    @Test
    fun testObjectiveRhs() {
        val systemCofig = getSimpleConfig()

        val creator = OffloadingLPCreator(systemConfig = systemCofig)

        val offloadingLinearProgram = creator.createOffloadingLinearProgram()
        val lp = offloadingLinearProgram.standardLinearProgram

        assertThat(lp.rows[0])
            .isNotNull()

        assertThat(lp.rows[0]!!.type)
            .isEqualTo(EquationRow.Type.Objective)

        val expectedRhs = -systemCofig.expectedTaskTime(0)
        assertThat(lp.rows[0]!!.rhs)
            .isWithin(eps)
            .of(expectedRhs)
    }

    @Test
    fun testEquation2Coefficients() {

        /*
            Variable Table:

            Index  | State                  |   Action                            | Coefficient
            --------------------------------------------------------------------------------------------------------------------
            0      | ({ [0], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            1      | ({ [0], 0, 1, -1, 0 }	|	NoOperation)                      | TL
            2      | ({ [0], 1, 0, 0, -1 }	|	NoOperation)                      | TT
            3      | ({ [0], 1, 1, 0, 0 }	|	NoOperation)                      | TB
            4      | ({ [1], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            5      | ({ [1], 0, 0, -1, -1 }	|	AddToCPU(0))                      | TL
            6      | ({ [1], 0, 0, -1, -1 }	|	AddToTransmissionUnit(0))         | TT
            7      | ({ [1], 0, 1, -1, 0 }	|	NoOperation)                      | TL
            8      | ({ [1], 0, 1, -1, 0 }	|	AddToTransmissionUnit(0))         | TB
            9      | ({ [1], 1, 0, 0, -1 }	|	NoOperation)                      | TT
            10     | ({ [1], 1, 0, 0, -1 }	|	AddToCPU(0))                      | TB
            11     | ({ [1], 1, 1, 0, 0 }	|	NoOperation)                      | TB
            12     | ({ [2], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            13     | ({ [2], 0, 0, -1, -1 }	|	AddToCPU(0))                      | TL
            14     | ({ [2], 0, 0, -1, -1 }	|	AddToTransmissionUnit(0))         | TT
            15     | ({ [2], 0, 0, -1, -1 }	|	AddToBothUnits(cpu = 0, tu = 0))  | TB
            16     | ({ [2], 0, 1, -1, 0 }	|	NoOperation)                      | TL
            17     | ({ [2], 0, 1, -1, 0 }	|	AddToTransmissionUnit(0))         | TB
            18     | ({ [2], 1, 0, 0, -1 }	|	NoOperation)                      | TT
            19     | ({ [2], 1, 0, 0, -1 }	|	AddToCPU(0))                      | TB
            20     | ({ [2], 1, 1, 0, 0 }	|	NoOperation)                      | TB
         */
        val TL = systemCofig.pLoc
        val TT = systemCofig.pTx * systemCofig.beta
        val TB = TL + TT

        val expectedCoefficients = mapOf(
            0 to 0.0,
            1 to TL,
            2 to TT,
            3 to TB,
            4 to 0.0,
            5 to TL,
            6 to TT,
            7 to TL,
            8 to TB,
            9 to TT,
            10 to TB,
            11 to TB,
            12 to 0.0,
            13 to TL,
            14 to TT,
            15 to TB,
            16 to TL,
            17 to TB,
            18 to TT,
            19 to TB,
            20 to TB
        ).orderedValues()

        assertThat(lp.rows[1])
            .isNotNull()

        assertThat(lp.rows[1]!!.type)
            .isEqualTo(EquationRow.Type.LessThan)

        assertThat(lp.rows[1]!!.coefficients)
            .hasSize(21)

        lp.rows[1]!!.coefficients.forEachIndexed { index, d ->
            // println("index = $index | ${offloadingLinearProgram.indexMapping.stateActionByCoefficientIndex[index]}")
            assertThat(d).isApproxEqualTo(expectedCoefficients[index])
        }
    }

    @Test
    fun testEquation2Rhs() {
        assertThat(lp.rows[1]!!.rhs)
            .isApproxEqualTo(systemCofig.pMax)
    }

    @Test
    fun testEquation3Coefficients() {
        /*
            Variable Table:

            Index  | State                  |   Action                            | Coefficient
            --------------------------------------------------------------------------------------------------------------------
            0      | ({ [0], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            1      | ({ [0], 0, 1, -1, 0 }	|	NoOperation)                      | 0.0
            2      | ({ [0], 1, 0, 0, -1 }	|	NoOperation)                      | 0.0
            3      | ({ [0], 1, 1, 0, 0 }	|	NoOperation)                      | 0.0
            4      | ({ [1], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            5      | ({ [1], 0, 0, -1, -1 }	|	AddToCPU(0))                      | TL
            6      | ({ [1], 0, 0, -1, -1 }	|	AddToTransmissionUnit(0))         | TT
            7      | ({ [1], 0, 1, -1, 0 }	|	NoOperation)                      | 0.0
            8      | ({ [1], 0, 1, -1, 0 }	|	AddToTransmissionUnit(0))         | TT
            9      | ({ [1], 1, 0, 0, -1 }	|	NoOperation)                      | 0.0
            10     | ({ [1], 1, 0, 0, -1 }	|	AddToCPU(0))                      | TL
            11     | ({ [1], 1, 1, 0, 0 }	|	NoOperation)                      | 0.0
            12     | ({ [2], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            13     | ({ [2], 0, 0, -1, -1 }	|	AddToCPU(0))                      | TL
            14     | ({ [2], 0, 0, -1, -1 }	|	AddToTransmissionUnit(0))         | TT
            15     | ({ [2], 0, 0, -1, -1 }	|	AddToBothUnits(cpu = 0, tu = 0))  | TB
            16     | ({ [2], 0, 1, -1, 0 }	|	NoOperation)                      | 0.0
            17     | ({ [2], 0, 1, -1, 0 }	|	AddToTransmissionUnit(0))         | TT
            18     | ({ [2], 1, 0, 0, -1 }	|	NoOperation)                      | 0.0
            19     | ({ [2], 1, 0, 0, -1 }	|	AddToCPU(0))                      | TL
            20     | ({ [2], 1, 1, 0, 0 }	|	NoOperation)                      | 0.0
         */
        val TL = (1.0 - systemCofig.eta!![0])
        val TT = -systemCofig.eta!![0]
        val TB = (1.0 - 2.0 * systemCofig.eta!![0])

        val expectedCoefficients = mapOf(
            0 to 0.0,
            1 to 0.0,
            2 to 0.0,
            3 to 0.0,
            4 to 0.0,
            5 to TL,
            6 to TT,
            7 to 0.0,
            8 to TT,
            9 to 0.0,
            10 to TL,
            11 to 0.0,
            12 to 0.0,
            13 to TL,
            14 to TT,
            15 to TB,
            16 to 0.0,
            17 to TT,
            18 to 0.0,
            19 to TL,
            20 to 0.0
        ).orderedValues()

        validateCoefficients(2, expectedCoefficients)
    }

    @Test
    fun testEquationRowCount() {
        val stateManager = UserEquipmentStateManager.fromSystemConfig(systemCofig)
        val allStates = stateManager.allStates()
        assertThat(lp.rows)
            .hasSize(2 + systemCofig.numberOfQueues + allStates.size + 1)
    }

    @Test
    fun testEquationRowsNotNull() {
        lp.rows.forEach {
            assertThat(it)
                .isNotNull()
        }
    }

    @Test
    fun testEquation4Coefficients() {
        val stateManager = UserEquipmentStateManager.fromSystemConfig(systemCofig)

        val state = UserEquipmentState.singleQueue(2, 0, 1)
        println(state)
        val stateIndex = stateManager.allStates().indexOf(state)
        assertThat(stateIndex)
            .isEqualTo(9)
        /*
            State Table

            State               | Index
            ========================================
             (0, 0, 0)          | 0
             (0, 0, 1)          | 1
             (0, 1, 0)          | 2
             (0, 1, 1)          | 3
             (1, 0, 0)          | 4
             (1, 0, 1)          | 5
             (1, 1, 0)          | 6
             (1, 1, 1)          | 7
             (2, 0, 0)          | 8
             (2, 0, 1)          | 9
             (2, 1, 0)          | 10
             (2, 1, 1)          | 11
         */

        val equationIndex = 2 + systemCofig.numberOfQueues + stateIndex
        /*
            0      | ({ [0], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            1      | ({ [0], 0, 1, -1, 0 }	|	NoOperation)                      | 0.0
            2      | ({ [0], 1, 0, 0, -1 }	|	NoOperation)                      | 0.0
            3      | ({ [0], 1, 1, 0, 0 }	|	NoOperation)                      | 0.0
            4      | ({ [1], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            5      | ({ [1], 0, 0, -1, -1 }	|	AddToCPU(0))                      | 0.0
            6      | ({ [1], 0, 0, -1, -1 }	|	AddToTransmissionUnit(0))         | 0.0
            7      | ({ [1], 0, 1, -1, 0 }	|	NoOperation)                      | 0.0
            8      | ({ [1], 0, 1, -1, 0 }	|	AddToTransmissionUnit(0))         | 0.0
            9      | ({ [1], 1, 0, 0, -1 }	|	NoOperation)                      | 0.0
            10     | ({ [1], 1, 0, 0, -1 }	|	AddToCPU(0))                      | 0.0
            11     | ({ [1], 1, 1, 0, 0 }	|	NoOperation)                      | 0.0
            12     | ({ [2], 0, 0, -1, -1 }	|	NoOperation)                      | 0.0
            13     | ({ [2], 0, 0, -1, -1 }	|	AddToCPU(0))                      | Alpha
            14     | ({ [2], 0, 0, -1, -1 }	|	AddToTransmissionUnit(0))         | 0.0
            15     | ({ [2], 0, 0, -1, -1 }	|	AddToBothUnits(cpu = 0, tu = 0))  | 0.0
            16     | ({ [2], 0, 1, -1, 0 }	|	NoOperation)                      | 0.0
            17     | ({ [2], 0, 1, -1, 0 }	|	AddToTransmissionUnit(0))         | 0.0
            18     | ({ [2], 1, 0, 0, -1 }	|	NoOperation)                      | 0.0
            19     | ({ [2], 1, 0, 0, -1 }	|	AddToCPU(0))                      | Alpha * Beta
            20     | ({ [2], 1, 1, 0, 0 }	|	NoOperation)                      | 0.0
         */
        val expectedCoefficients: MutableList<Double> = mutableListOfZeros(21)
        expectedCoefficients[13] = systemCofig.alpha[0]
        expectedCoefficients[19] = systemCofig.beta * systemCofig.alpha[0]
        expectedCoefficients[16] = -1.0
        expectedCoefficients[17] = -1.0

        validateCoefficients(equationIndex, expectedCoefficients)
    }
}