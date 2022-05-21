package stochastic.lp

import com.google.common.truth.Truth.assertThat
import core.environment.EnvironmentParameters
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig
import org.junit.jupiter.api.Test
import policy.Action
import ue.*
import core.ue.UserEquipmentStateConfig.Companion.allStates

class OffloadingLPCreatorTest {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 2,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 2
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = 0.30,
                beta = 0.4,
                eta = 0.2,
                pTx = 1.0,
                pLoc = 0.8,
                pMax = 200.0
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
    fun testObjectiveCoefficientsSimple1() {
        val systemCofig = getSimpleConfig()

        val creator = OffloadingLPCreator(systemConfig = systemCofig)

        val lp = creator.createLP() as StandardLinearProgram

        /*
            Variable Index Table:

            var id      | state         | action
            ---------------------------------------------
            0           | (0, 0, 0)     | NoOperation
            1           | (0, 0, 0)     | AddToCPU
            2           | (0, 0, 0)     | AddToTransmissionUnit
            3           | (0, 0, 0)     | AddToBothUnits
            4           | (0, 0, 1)     | NoOperation
            5           | (0, 0, 1)     | AddToCPU
            6           | (0, 0, 1)     | AddToTransmissionUnit
            7           | (0, 0, 1)     | AddToBothUnits
            8           | (0, 1, 0)     | NoOperation
            9           | (0, 1, 0)     | AddToCPU
            10          | (0, 1, 0)     | AddToTransmissionUnit
            11          | (0, 1, 0)     | AddToBothUnits
            12          | (0, 1, 1)     | NoOperation
            13          | (0, 1, 1)     | AddToCPU
            14          | (0, 1, 1)     | AddToTransmissionUnit
            15          | (0, 1, 1)     | AddToBothUnits
            16          | (1, 0, 0)     | NoOperation
            17          | (1, 0, 0)     | AddToCPU
            18          | (1, 0, 0)     | AddToTransmissionUnit
            19          | (1, 0, 0)     | AddToBothUnits
            20          | (1, 0, 1)     | NoOperation
            21          | (1, 0, 1)     | AddToCPU
            22          | (1, 0, 1)     | AddToTransmissionUnit
            23          | (1, 0, 1)     | AddToBothUnits
            24          | (1, 1, 0)     | NoOperation
            25          | (1, 1, 0)     | AddToCPU
            26          | (1, 1, 0)     | AddToTransmissionUnit
            27          | (1, 1, 0)     | AddToBothUnits
            28          | (1, 1, 1)     | NoOperation
            29          | (1, 1, 1)     | AddToCPU
            30          | (1, 1, 1)     | AddToTransmissionUnit
            31          | (1, 1, 1)     | AddToBothUnits
            32          | (2, 0, 0)     | NoOperation
            33          | (2, 0, 0)     | AddToCPU
            34          | (2, 0, 0)     | AddToTransmissionUnit
            35          | (2, 0, 0)     | AddToBothUnits
            36          | (2, 0, 1)     | NoOperation
            37          | (2, 0, 1)     | AddToCPU
            38          | (2, 0, 1)     | AddToTransmissionUnit
            39          | (2, 0, 1)     | AddToBothUnits
            40          | (2, 1, 0)     | NoOperation
            41          | (2, 1, 0)     | AddToCPU
            42          | (2, 1, 0)     | AddToTransmissionUnit
            43          | (2, 1, 0)     | AddToBothUnits
            44          | (2, 1, 1)     | NoOperation
            45          | (2, 1, 1)     | AddToCPU
            46          | (2, 1, 1)     | AddToTransmissionUnit
            47          | (2, 1, 1)     | AddToBothUnits
         */
        val expectedCoefficients: List<Double> =
            ((1..16).map { 0.0 } + (1..16).map { 1.0 } + (1..16).map { 2.0 }).map { it / systemCofig.alpha }

        assertThat(lp.rows[0].coefficients)
            .hasSize(48)

        lp.rows[0].coefficients.forEachIndexed { idx, it ->
            assertThat(it)
                .isWithin(1e-3)
                .of(expectedCoefficients[idx])
        }
    }

    @Test
    fun testNumberOfRows() {
        val systemConfig = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(systemConfig)
        val lp = lpCreator.createLP() as StandardLinearProgram

        assertThat(lp.rows)
            .hasSize(4 + systemConfig.stateConfig.allStates().size)
    }

    private fun index(config: OffloadingSystemConfig, state: UserEquipmentState, action: Action): Int {
        val (x, y, z) = state
        var idx = 0
        idx += x * (config.tuNumberOfPackets + 1) * (config.cpuNumberOfSections) * config.actionCount
        idx += y * (config.cpuNumberOfSections) * config.actionCount
        idx += z * config.actionCount
        idx += action.order

        return idx
    }

    fun getStateActionByIndex(systemConfig: OffloadingSystemConfig): Map<Int, Index> {
        val stateActionByIndex: MutableMap<Int, Index> = mutableMapOf()
        var idx = 0
        systemConfig.stateConfig.allStates().forEach { state ->
            systemConfig.allActions.forEach { action ->
                stateActionByIndex[idx] = Index(state, action)
                idx += 1
            }
        }
        return stateActionByIndex
    }
    @Test
    fun testCoefficientsEquation2() {
        val systemConfig = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(systemConfig)
        val lp = lpCreator.createLP() as StandardLinearProgram

        /*
                   Variable Index Table:

                   var id      | state         | action
                   ---------------------------------------------
                   0           | (0, 0, 0)     | NoOperation                | Z0
                   1           | (0, 0, 0)     | AddToCPU                   | Z0
                   2           | (0, 0, 0)     | AddToTransmissionUnit      | Z0
                   3           | (0, 0, 0)     | AddToBothUnits             | Z0
                   4           | (0, 0, 1)     | NoOperation                | L0
                   5           | (0, 0, 1)     | AddToCPU                   | Z0
                   6           | (0, 0, 1)     | AddToTransmissionUnit      | Z0
                   7           | (0, 0, 1)     | AddToBothUnits             | Z0
                   8           | (0, 1, 0)     | NoOperation                | T0
                   9           | (0, 1, 0)     | AddToCPU                   | Z0
                   10          | (0, 1, 0)     | AddToTransmissionUnit      | Z0
                   11          | (0, 1, 0)     | AddToBothUnits             | Z0
                   12          | (0, 1, 1)     | NoOperation                | Z0
                   13          | (0, 1, 1)     | AddToCPU                   | Z0
                   14          | (0, 1, 1)     | AddToTransmissionUnit      | Z0
                   15          | (0, 1, 1)     | AddToBothUnits             | Z0
                   16          | (1, 0, 0)     | NoOperation                | Z0
                   17          | (1, 0, 0)     | AddToCPU                   | L0
                   18          | (1, 0, 0)     | AddToTransmissionUnit      | T0
                   19          | (1, 0, 0)     | AddToBothUnits             | Z0
                   20          | (1, 0, 1)     | NoOperation                | L0
                   21          | (1, 0, 1)     | AddToCPU                   | Z0
                   22          | (1, 0, 1)     | AddToTransmissionUnit      | B0
                   23          | (1, 0, 1)     | AddToBothUnits             | Z0
                   24          | (1, 1, 0)     | NoOperation                | T0
                   25          | (1, 1, 0)     | AddToCPU                   | B0
                   26          | (1, 1, 0)     | AddToTransmissionUnit      | Z0
                   27          | (1, 1, 0)     | AddToBothUnits             | Z0
                   28          | (1, 1, 1)     | NoOperation                | B0
                   29          | (1, 1, 1)     | AddToCPU                   | Z0
                   30          | (1, 1, 1)     | AddToTransmissionUnit      | Z0
                   31          | (1, 1, 1)     | AddToBothUnits             | Z0
                   32          | (2, 0, 0)     | NoOperation                | Z0
                   33          | (2, 0, 0)     | AddToCPU                   | L0
                   34          | (2, 0, 0)     | AddToTransmissionUnit      | T0
                   35          | (2, 0, 0)     | AddToBothUnits             | B0
                   36          | (2, 0, 1)     | NoOperation                | L0
                   37          | (2, 0, 1)     | AddToCPU                   | Z0
                   38          | (2, 0, 1)     | AddToTransmissionUnit      | B0
                   39          | (2, 0, 1)     | AddToBothUnits             | Z0
                   40          | (2, 1, 0)     | NoOperation                | T0
                   41          | (2, 1, 0)     | AddToCPU                   | B0
                   42          | (2, 1, 0)     | AddToTransmissionUnit      | Z0
                   43          | (2, 1, 0)     | AddToBothUnits             | Z0
                   44          | (2, 1, 1)     | NoOperation                | B0
                   45          | (2, 1, 1)     | AddToCPU                   | Z0
                   46          | (2, 1, 1)     | AddToTransmissionUnit      | Z0
                   47          | (2, 1, 1)     | AddToBothUnits             | Z0
                */
        val Z0 = 0.0
        val L0 = systemConfig.pLoc
        val T0 = systemConfig.beta * systemConfig.pTx
        val B0 = T0 + L0
        val coefficients = listOf<Double>(
            Z0, Z0, Z0, Z0, L0, Z0, Z0, Z0, T0, Z0, Z0, Z0, B0, Z0, Z0, Z0,
            Z0, L0, T0, Z0, L0, Z0, B0, Z0, T0, B0, Z0, Z0, B0, Z0, Z0, Z0,
            Z0, L0, T0, B0, L0, Z0, B0, Z0, T0, B0, Z0, Z0, B0, Z0, Z0, Z0
        )

        val stateActionByIndex: MutableMap<Int, Index> = mutableMapOf()
        var idx = 0
        systemConfig.stateConfig.allStates().forEach { state ->
            systemConfig.allActions.forEach { action ->
                stateActionByIndex[idx] = Index(state, action)
                idx += 1
            }
        }

        assertThat(lp.rows[1].coefficients)
            .hasSize(48)

        assertThat(lp.rows[1].type)
            .isEqualTo(EquationRow.Type.LessThan)

        coefficients.forEachIndexed { index, d ->
            // println("${stateActionByIndex[index]} | Actual: ${lp.rows[1].coefficients[index]} | Expected: ${coefficients[index]}")
            assertThat(lp.rows[1].coefficients[index])
                .isWithin(1e-3)
                .of(coefficients[index])
        }
    }

    @Test
    fun testRhsEquation2() {
        val lp = getSimpleLP()

        assertThat(lp.rows[1].rhs)
            .isWithin(1e-6)
            .of(getSimpleConfig().pMax)
    }

    @Test
    fun testCoefficientsEquation3() {
        val config = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(config)
        val lp = lpCreator.createLP() as StandardLinearProgram

        assertThat(lp.rows[2].coefficients)
            .hasSize(48)

        /*
                   Variable Index Table:

                   var id      | state         | action
                   ---------------------------------------------
                   0           | (0, 0, 0)     | NoOperation                | TZ
                   1           | (0, 0, 0)     | AddToCPU                   | TZ
                   2           | (0, 0, 0)     | AddToTransmissionUnit      | TZ
                   3           | (0, 0, 0)     | AddToBothUnits             | TZ
                   4           | (0, 0, 1)     | NoOperation                | TZ
                   5           | (0, 0, 1)     | AddToCPU                   | TZ
                   6           | (0, 0, 1)     | AddToTransmissionUnit      | TZ
                   7           | (0, 0, 1)     | AddToBothUnits             | TZ
                   8           | (0, 1, 0)     | NoOperation                | TZ
                   9           | (0, 1, 0)     | AddToCPU                   | TZ
                   10          | (0, 1, 0)     | AddToTransmissionUnit      | TZ
                   11          | (0, 1, 0)     | AddToBothUnits             | TZ
                   12          | (0, 1, 1)     | NoOperation                | TZ
                   13          | (0, 1, 1)     | AddToCPU                   | TZ
                   14          | (0, 1, 1)     | AddToTransmissionUnit      | TZ
                   15          | (0, 1, 1)     | AddToBothUnits             | TZ
                   16          | (1, 0, 0)     | NoOperation                | TZ
                   17          | (1, 0, 0)     | AddToCPU                   | T1
                   18          | (1, 0, 0)     | AddToTransmissionUnit      | T2
                   19          | (1, 0, 0)     | AddToBothUnits             | TZ
                   20          | (1, 0, 1)     | NoOperation                | TZ
                   21          | (1, 0, 1)     | AddToCPU                   | TZ
                   22          | (1, 0, 1)     | AddToTransmissionUnit      | T2
                   23          | (1, 0, 1)     | AddToBothUnits             | TZ
                   24          | (1, 1, 0)     | NoOperation                | TZ
                   25          | (1, 1, 0)     | AddToCPU                   | T1
                   26          | (1, 1, 0)     | AddToTransmissionUnit      | TZ
                   27          | (1, 1, 0)     | AddToBothUnits             | TZ
                   28          | (1, 1, 1)     | NoOperation                | TZ
                   29          | (1, 1, 1)     | AddToCPU                   | TZ
                   30          | (1, 1, 1)     | AddToTransmissionUnit      | TZ
                   31          | (1, 1, 1)     | AddToBothUnits             | TZ
                   32          | (2, 0, 0)     | NoOperation                | TZ
                   33          | (2, 0, 0)     | AddToCPU                   | T1
                   34          | (2, 0, 0)     | AddToTransmissionUnit      | T2
                   35          | (2, 0, 0)     | AddToBothUnits             | T3
                   36          | (2, 0, 1)     | NoOperation                | TZ
                   37          | (2, 0, 1)     | AddToCPU                   | TZ
                   38          | (2, 0, 1)     | AddToTransmissionUnit      | T2
                   39          | (2, 0, 1)     | AddToBothUnits             | TZ
                   40          | (2, 1, 0)     | NoOperation                | TZ
                   41          | (2, 1, 0)     | AddToCPU                   | T1
                   42          | (2, 1, 0)     | AddToTransmissionUnit      | TZ
                   43          | (2, 1, 0)     | AddToBothUnits             | TZ
                   44          | (2, 1, 1)     | NoOperation                | TZ
                   45          | (2, 1, 1)     | AddToCPU                   | TZ
                   46          | (2, 1, 1)     | AddToTransmissionUnit      | TZ
                   47          | (2, 1, 1)     | AddToBothUnits             | TZ
                */
        val eta = config.eta
        val coefficients = mapOf<Int, Double>(
            0 to 0.0,
            1 to 0.0,
            2 to 0.0,
            3 to 0.0,
            4 to 0.0,
            5 to 0.0,
            6 to 0.0,
            7 to 0.0,
            8 to 0.0,
            9 to 0.0,
            10 to 0.0,
            11 to 0.0,
            12 to 0.0,
            13 to 0.0,
            14 to 0.0,
            15 to 0.0,
            16 to 0.0,
            17 to 1.0 - eta,
            18 to -eta,
            19 to 0.0,
            20 to 0.0,
            21 to 0.0,
            22 to -eta,
            23 to 0.0,
            24 to 0.0,
            25 to 1.0 - eta,
            26 to 0.0,
            27 to 0.0,
            28 to 0.0,
            29 to 0.0,
            30 to 0.0,
            31 to 0.0,
            32 to 0.0,
            33 to 1.0 - eta,
            34 to -eta,
            35 to 1 - 2 * eta,
            36 to 0.0,
            37 to 0.0,
            38 to -eta,
            39 to 0.0,
            40 to 0.0,
            41 to 1.0 - eta,
            42 to 0.0,
            43 to 0.0,
            44 to 0.0,
            45 to 0.0,
            46 to 0.0,
            47 to 0.0
        ).toList().sortedBy { it.first }.map { it.second }

        val stateActionByIndex = getStateActionByIndex(config)

        println(coefficients)
        lp.rows[2].coefficients.forEachIndexed { index: Int, value: Double ->
            println("$index : ${stateActionByIndex[index]} | Actual: ${lp.rows[2].coefficients[index]} | Expected: ${coefficients[index]}")
            assertThat(value)
                .isWithin(1e-3)
                .of(coefficients[index])
        }
    }

    @Test
    fun testRhsEquation3() {
        val config = getSimpleConfig()
        val lpCreator = OffloadingLPCreator(config)
        val lp = lpCreator.createLP() as StandardLinearProgram

        assertThat(lp.rows[2].rhs)
            .isWithin(1e-6)
            .of(0.0)
    }

    fun getSimpleLP(): StandardLinearProgram {
        val config = getSimpleConfig()

        val creator = OffloadingLPCreator(config)
        val lp = creator.createLP() as StandardLinearProgram
        return lp
    }

    @Test
    fun testCoefficientsEquation5() {
        val config = getSimpleConfig()

        val creator = OffloadingLPCreator(config)
        val lp = creator.createLP() as StandardLinearProgram

        assertThat(lp.rows.last().coefficients)
            .hasSize(48)

        lp.rows.last().coefficients.forEach {
            assertThat(it)
                .isWithin(1e-3)
                .of(1.0)
        }
    }

    @Test
    fun testRhsEquation5() {
        val lp = getSimpleLP()

        assertThat(lp.rows.last().rhs)
            .isWithin(1e-6)
            .of(1.0)
    }

    @Test
    fun testCoefficientsEquation4Offloading() {
        val simpleConfig = getSimpleConfig()
        val creator = OffloadingLPCreator(simpleConfig)
        val lp = creator.createLP()

        val expectedCoefficients = mutableMapOf<Index, Double>()
        simpleConfig.stateConfig.allStates().forEach { source ->
            simpleConfig.allActions.forEach { action ->
                expectedCoefficients[Index(source, action)] = 0.0
            }
        }
        val destState = UserEquipmentState(2, 0, 1)

        expectedCoefficients[Index(UserEquipmentState(2, 1, 0), Action.AddToCPU)] = simpleConfig.beta * simpleConfig.alpha
        expectedCoefficients[Index(UserEquipmentState(2, 0, 0), Action.AddToCPU)] = simpleConfig.alpha
        simpleConfig.allActions.forEach { action ->
            expectedCoefficients[Index(destState, action)] = expectedCoefficients[Index(destState, action)]!! - 1.0
        }

        lp.cEquation4[destState]!!.forEach { key: Index, value: Double ->
            // println("$key : Expected = ${expectedCoefficients[key]} | Actual = $value")
            assertThat(value)
                .isWithin(1e-6)
                .of(expectedCoefficients[key]!!)
        }
    }

    @Test
    fun testCoefficientsEquation4() {
        val simpleConfig = getSimpleConfig() // (2, 1, 2)
        val creator = OffloadingLPCreator(simpleConfig)
        val lp = creator.createLP() as StandardLinearProgram
        val numberOfEquations = simpleConfig.stateConfig.allStates().size

        val equations = lp.rows.subList(3, 3 + numberOfEquations)

        val state = UserEquipmentState(2, 0, 1)
        val index = 9
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

        val eqCoefficients = equations[index].coefficients

        /*
                   State Action Table:

                   var id      | source state  | action
                   ---------------------------------------------
                   0           | (0, 0, 0)     | NoOperation                | TZ
                   1           | (0, 0, 0)     | AddToCPU                   | TZ
                   2           | (0, 0, 0)     | AddToTransmissionUnit      | TZ
                   3           | (0, 0, 0)     | AddToBothUnits             | TZ
                   4           | (0, 0, 1)     | NoOperation                | TZ
                   5           | (0, 0, 1)     | AddToCPU                   | TZ
                   6           | (0, 0, 1)     | AddToTransmissionUnit      | TZ
                   7           | (0, 0, 1)     | AddToBothUnits             | TZ
                   8           | (0, 1, 0)     | NoOperation                | TZ
                   9           | (0, 1, 0)     | AddToCPU                   | TZ
                   10          | (0, 1, 0)     | AddToTransmissionUnit      | TZ
                   11          | (0, 1, 0)     | AddToBothUnits             | TZ
                   12          | (0, 1, 1)     | NoOperation                | TZ
                   13          | (0, 1, 1)     | AddToCPU                   | TZ
                   14          | (0, 1, 1)     | AddToTransmissionUnit      | TZ
                   15          | (0, 1, 1)     | AddToBothUnits             | TZ
                   16          | (1, 0, 0)     | NoOperation                | TZ
                   17          | (1, 0, 0)     | AddToCPU                   | TZ
                   18          | (1, 0, 0)     | AddToTransmissionUnit      | TZ
                   19          | (1, 0, 0)     | AddToBothUnits             | TZ
                   20          | (1, 0, 1)     | NoOperation                | TZ
                   21          | (1, 0, 1)     | AddToCPU                   | TZ
                   22          | (1, 0, 1)     | AddToTransmissionUnit      | TZ
                   23          | (1, 0, 1)     | AddToBothUnits             | TZ
                   24          | (1, 1, 0)     | NoOperation                | TZ
                   25          | (1, 1, 0)     | AddToCPU                   | TZ
                   26          | (1, 1, 0)     | AddToTransmissionUnit      | TZ
                   27          | (1, 1, 0)     | AddToBothUnits             | TZ
                   28          | (1, 1, 1)     | NoOperation                | TZ
                   29          | (1, 1, 1)     | AddToCPU                   | TZ
                   30          | (1, 1, 1)     | AddToTransmissionUnit      | TZ
                   31          | (1, 1, 1)     | AddToBothUnits             | TZ
                   32          | (2, 0, 0)     | NoOperation                | TZ
                   33          | (2, 0, 0)     | AddToCPU                   | Alpha
                   34          | (2, 0, 0)     | AddToTransmissionUnit      | TZ
                   35          | (2, 0, 0)     | AddToBothUnits             | TZ
                   36          | (2, 0, 1)     | NoOperation                | TZ
                   37          | (2, 0, 1)     | AddToCPU                   | TZ
                   38          | (2, 0, 1)     | AddToTransmissionUnit      | TZ
                   39          | (2, 0, 1)     | AddToBothUnits             | TZ
                   40          | (2, 1, 0)     | NoOperation                | TZ
                   41          | (2, 1, 0)     | AddToCPU                   | Alpha * Beta
                   42          | (2, 1, 0)     | AddToTransmissionUnit      | TZ
                   43          | (2, 1, 0)     | AddToBothUnits             | TZ
                   44          | (2, 1, 1)     | NoOperation                | TZ
                   45          | (2, 1, 1)     | AddToCPU                   | TZ
                   46          | (2, 1, 1)     | AddToTransmissionUnit      | TZ
                   47          | (2, 1, 1)     | AddToBothUnits             | TZ
                */
        val expectedCoefficients: MutableList<Double> = (1..48).map { 0.0 }.toMutableList()
        expectedCoefficients[41] = simpleConfig.beta * simpleConfig.alpha
        expectedCoefficients[33] = simpleConfig.alpha
        expectedCoefficients[36] = -1.0
        expectedCoefficients[37] = -1.0
        expectedCoefficients[38] = -1.0
        expectedCoefficients[39] = -1.0

        val stateActionByIndex = getStateActionByIndex(simpleConfig)
        eqCoefficients.forEachIndexed { i, d ->
            println("${stateActionByIndex[i]} : $d")
        }
        eqCoefficients.forEachIndexed { i, d ->
            println("$i : ${stateActionByIndex[i]} | Actual: ${eqCoefficients[i]} | Expected: ${expectedCoefficients[i]}")
            assertThat(d)
                .isWithin(1e-6)
                .of(expectedCoefficients[i])
        }
    }

    @Test
    fun testCoefficientsEquation4T2() {
        val simpleConfig = getSimpleConfig() // (2, 1, 2)
        val creator = OffloadingLPCreator(simpleConfig)
        val lp = creator.createLP() as StandardLinearProgram
        val numberOfEquations = simpleConfig.stateConfig.allStates().size

        val equations = lp.rows.subList(3, 3 + numberOfEquations)

        val index = 10 // UserEquipmentState(2, 1, 0)
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

        val eqCoefficients = equations[index].coefficients

        /*
                   State Action Table:

                   var id      | source state  | action
                   ---------------------------------------------
                   0           | (0, 0, 0)     | NoOperation                | TZ
                   1           | (0, 0, 0)     | AddToCPU                   | TZ
                   2           | (0, 0, 0)     | AddToTransmissionUnit      | TZ
                   3           | (0, 0, 0)     | AddToBothUnits             | TZ
                   4           | (0, 0, 1)     | NoOperation                | TZ
                   5           | (0, 0, 1)     | AddToCPU                   | TZ
                   6           | (0, 0, 1)     | AddToTransmissionUnit      | TZ
                   7           | (0, 0, 1)     | AddToBothUnits             | TZ
                   8           | (0, 1, 0)     | NoOperation                | TZ
                   9           | (0, 1, 0)     | AddToCPU                   | TZ
                   10          | (0, 1, 0)     | AddToTransmissionUnit      | TZ
                   11          | (0, 1, 0)     | AddToBothUnits             | TZ
                   12          | (0, 1, 1)     | NoOperation                | TZ
                   13          | (0, 1, 1)     | AddToCPU                   | TZ
                   14          | (0, 1, 1)     | AddToTransmissionUnit      | TZ
                   15          | (0, 1, 1)     | AddToBothUnits             | TZ
                   16          | (1, 0, 0)     | NoOperation                | TZ
                   17          | (1, 0, 0)     | AddToCPU                   | TZ
                   18          | (1, 0, 0)     | AddToTransmissionUnit      | TZ
                   19          | (1, 0, 0)     | AddToBothUnits             | TZ
                   20          | (1, 0, 1)     | NoOperation                | TZ
                   21          | (1, 0, 1)     | AddToCPU                   | TZ
                   22          | (1, 0, 1)     | AddToTransmissionUnit      | TZ
                   23          | (1, 0, 1)     | AddToBothUnits             | TZ
                   24          | (1, 1, 0)     | NoOperation                | Alpha * BetaC
                   25          | (1, 1, 0)     | AddToCPU                   | TZ
                   26          | (1, 1, 0)     | AddToTransmissionUnit      | TZ
                   27          | (1, 1, 0)     | AddToBothUnits             | TZ
                   28          | (1, 1, 1)     | NoOperation                | TZ
                   29          | (1, 1, 1)     | AddToCPU                   | TZ
                   30          | (1, 1, 1)     | AddToTransmissionUnit      | TZ
                   31          | (1, 1, 1)     | AddToBothUnits             | TZ
                   32          | (2, 0, 0)     | NoOperation                | TZ
                   33          | (2, 0, 0)     | AddToCPU                   | TZ
                   34          | (2, 0, 0)     | AddToTransmissionUnit      | Alpha * BetaC
                   35          | (2, 0, 0)     | AddToBothUnits             | TZ
                   36          | (2, 0, 1)     | NoOperation                | TZ
                   37          | (2, 0, 1)     | AddToCPU                   | TZ
                   38          | (2, 0, 1)     | AddToTransmissionUnit      | Alpha * BetaC
                   39          | (2, 0, 1)     | AddToBothUnits             | TZ
                   40          | (2, 1, 0)     | NoOperation                | BetaC
                   41          | (2, 1, 0)     | AddToCPU                   | TZ
                   42          | (2, 1, 0)     | AddToTransmissionUnit      | TZ
                   43          | (2, 1, 0)     | AddToBothUnits             | TZ
                   44          | (2, 1, 1)     | NoOperation                | BetaC
                   45          | (2, 1, 1)     | AddToCPU                   | TZ
                   46          | (2, 1, 1)     | AddToTransmissionUnit      | TZ
                   47          | (2, 1, 1)     | AddToBothUnits             | TZ
                */
        val expectedCoefficients: MutableList<Double> = (1..48).map { 0.0 }.toMutableList()
        expectedCoefficients[24] = simpleConfig.alpha * (1.0 - simpleConfig.beta)
        expectedCoefficients[28] = simpleConfig.alpha * (1.0 - simpleConfig.beta)
        expectedCoefficients[34] = (1.0 - simpleConfig.beta) * simpleConfig.alpha
        expectedCoefficients[38] = (1.0 - simpleConfig.beta) * simpleConfig.alpha
        expectedCoefficients[40] = (1.0 - simpleConfig.beta)
        expectedCoefficients[44] = (1.0 - simpleConfig.beta)
        expectedCoefficients[40] -= 1.0
        expectedCoefficients[41] -= 1.0
        expectedCoefficients[42] -= 1.0
        expectedCoefficients[43] -= 1.0

        val stateActionByIndex = getStateActionByIndex(simpleConfig)
        eqCoefficients.forEachIndexed { i, d ->
            println("${stateActionByIndex[i]} : $d")
        }
        eqCoefficients.forEachIndexed { i, d ->
            println("$i : ${stateActionByIndex[i]} | Actual: ${eqCoefficients[i]} | Expected: ${expectedCoefficients[i]}")
            assertThat(d)
                .isWithin(1e-6)
                .of(expectedCoefficients[i])
        }
    }

    @Test
    fun testCoefficientsEquation4T3() {
        val config = getSimpleConfig() // (2, 1, 2)
        val creator = OffloadingLPCreator(config)
        val lp = creator.createLP() as StandardLinearProgram
        val numberOfEquations = config.stateConfig.allStates().size

        val equations = lp.rows.subList(3, 3 + numberOfEquations)

        val index = 8 // UserEquipmentState(2, 0, 0)
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

        val eqCoefficients = equations[index].coefficients

        /*
                   State Action Table:

                   var id      | source state  | action
                   ---------------------------------------------
                   0           | (0, 0, 0)     | NoOperation                | TZ
                   1           | (0, 0, 0)     | AddToCPU                   | TZ
                   2           | (0, 0, 0)     | AddToTransmissionUnit      | TZ
                   3           | (0, 0, 0)     | AddToBothUnits             | TZ
                   4           | (0, 0, 1)     | NoOperation                | TZ
                   5           | (0, 0, 1)     | AddToCPU                   | TZ
                   6           | (0, 0, 1)     | AddToTransmissionUnit      | TZ
                   7           | (0, 0, 1)     | AddToBothUnits             | TZ
                   8           | (0, 1, 0)     | NoOperation                | TZ
                   9           | (0, 1, 0)     | AddToCPU                   | TZ
                   10          | (0, 1, 0)     | AddToTransmissionUnit      | TZ
                   11          | (0, 1, 0)     | AddToBothUnits             | TZ
                   12          | (0, 1, 1)     | NoOperation                | TZ
                   13          | (0, 1, 1)     | AddToCPU                   | TZ
                   14          | (0, 1, 1)     | AddToTransmissionUnit      | TZ
                   15          | (0, 1, 1)     | AddToBothUnits             | TZ
                   16          | (1, 0, 0)     | NoOperation                | Alpha
                   17          | (1, 0, 0)     | AddToCPU                   | TZ
                   18          | (1, 0, 0)     | AddToTransmissionUnit      | TZ
                   19          | (1, 0, 0)     | AddToBothUnits             | TZ
                   20          | (1, 0, 1)     | NoOperation                | Alpha
                   21          | (1, 0, 1)     | AddToCPU                   | TZ
                   22          | (1, 0, 1)     | AddToTransmissionUnit      | TZ
                   23          | (1, 0, 1)     | AddToBothUnits             | TZ
                   24          | (1, 1, 0)     | NoOperation                | Alpha * Beta
                   25          | (1, 1, 0)     | AddToCPU                   | TZ
                   26          | (1, 1, 0)     | AddToTransmissionUnit      | TZ
                   27          | (1, 1, 0)     | AddToBothUnits             | TZ
                   28          | (1, 1, 1)     | NoOperation                | Alpha * Beta
                   29          | (1, 1, 1)     | AddToCPU                   | TZ
                   30          | (1, 1, 1)     | AddToTransmissionUnit      | TZ
                   31          | (1, 1, 1)     | AddToBothUnits             | TZ
                   32          | (2, 0, 0)     | NoOperation                | 1.0
                   33          | (2, 0, 0)     | AddToCPU                   | TZ
                   34          | (2, 0, 0)     | AddToTransmissionUnit      | Beta * Alpha
                   35          | (2, 0, 0)     | AddToBothUnits             | TZ
                   36          | (2, 0, 1)     | NoOperation                | TZ
                   37          | (2, 0, 1)     | AddToCPU                   | TZ
                   38          | (2, 0, 1)     | AddToTransmissionUnit      | Beta * Alpha
                   39          | (2, 0, 1)     | AddToBothUnits             | TZ
                   40          | (2, 1, 0)     | NoOperation                | Beta
                   41          | (2, 1, 0)     | AddToCPU                   | TZ
                   42          | (2, 1, 0)     | AddToTransmissionUnit      | TZ
                   43          | (2, 1, 0)     | AddToBothUnits             | TZ
                   44          | (2, 1, 1)     | NoOperation                | Beta
                   45          | (2, 1, 1)     | AddToCPU                   | TZ
                   46          | (2, 1, 1)     | AddToTransmissionUnit      | TZ
                   47          | (2, 1, 1)     | AddToBothUnits             | TZ
                */
        val expectedCoefficients: MutableList<Double> = (1..48).map { 0.0 }.toMutableList()
        expectedCoefficients[16] = config.alpha
        expectedCoefficients[20] = config.alpha
        expectedCoefficients[24] = config.alpha * config.beta
        expectedCoefficients[28] = config.alpha * config.beta
        expectedCoefficients[32] = 1.0
        expectedCoefficients[34] = config.beta * config.alpha
        expectedCoefficients[36] = 1.0
        expectedCoefficients[38] = config.beta * config.alpha
        expectedCoefficients[40] = config.beta
        expectedCoefficients[44] = config.beta
        expectedCoefficients[32] -= 1.0
        expectedCoefficients[33] -= 1.0
        expectedCoefficients[34] -= 1.0
        expectedCoefficients[35] -= 1.0

        val stateActionByIndex = getStateActionByIndex(config)
        eqCoefficients.forEachIndexed { i, d ->
            println("${stateActionByIndex[i]} : $d")
        }
        eqCoefficients.forEachIndexed { i, d ->
            println("$i : ${stateActionByIndex[i]} | Actual: ${eqCoefficients[i]} | Expected: ${expectedCoefficients[i]}")
            assertThat(d)
                .isWithin(1e-6)
                .of(expectedCoefficients[i])
        }
    }
}