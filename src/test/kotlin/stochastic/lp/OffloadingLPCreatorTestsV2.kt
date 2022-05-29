package stochastic.lp

class OffloadingLPCreatorTestsV2 {
    /*

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

        val offloadingLinearProgram = creator.createOffloadingLinearProgram()
        val lp = offloadingLinearProgram.standardLinearProgram
        println(offloadingLinearProgram.indexMapping)

        /*
            Variable Index Table:

            var id      | state         | action
            ---------------------------------------------
            0             | (1, 0, 0)     | NoOperation
            1             | (1, 0, 0)     | AddToCPU
            2             | (1, 0, 0)     | AddToTransmissionUnit
            3             | (1, 0, 1)     | NoOperation
            4             | (1, 0, 1)     | AddToTransmissionUnit
            5             | (1, 1, 0)     | NoOperation
            6             | (1, 1, 0)     | AddToCPU
            7             | (2, 0, 0)     | NoOperation
            8             | (2, 0, 0)     | AddToCPU
            9             | (2, 0, 0)     | AddToTransmissionUnit
            10            | (2, 0, 0)     | AddToBothUnits
            11            | (2, 0, 1)     | NoOperation
            12            | (2, 0, 1)     | AddToTransmissionUnit
            13            | (2, 1, 0)     | NoOperation
            14            | (2, 1, 0)     | AddToCPU
         */
        val expectedCoefficients: List<Double> =
            ((1..7).map { 1.0 } + (1..8).map { 2.0 }).map { it / systemCofig.alpha }

        Truth.assertThat(lp.rows[0].coefficients)
            .hasSize(15)

        lp.rows[0].coefficients.forEachIndexed { idx, it ->
            Truth.assertThat(it)
                .isWithin(1e-6)
                .of(expectedCoefficients[idx])
        }
    }

    @Test
    fun testNumberOfRows() {
        val systemConfig = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(systemConfig)
        val lp = lpCreator.createOffloadingLinearProgram().standardLinearProgram

        assertThat(lp.rows)
            .hasSize(4 + systemConfig.stateConfig.allStates().size)
    }

    @Test
    fun testRhsObjective() {
        val systemConfig = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(systemConfig)
        val lp = lpCreator.createOffloadingLinearProgram().standardLinearProgram

        assertThat(lp.rows[0].type)
            .isEqualTo(EquationRow.Type.Objective)

        /*
                     Only possible stateActions

                     var id      | state         | action
                     ---------------------------------------------
                     0           | (0, 0, 0)     | NoOperation                | 0.0 / alpha
                     4           | (0, 0, 1)     | NoOperation                | 0.0 / alpha
                     8           | (0, 1, 0)     | NoOperation                | 0.0 / alpha
                     12          | (0, 1, 1)     | NoOperation                | 0.0 / alpha
                     28          | (1, 1, 1)     | NoOperation                | 1.0 / alpha
                     44          | (2, 1, 1)     | NoOperation                | 2.0 / alpha
                  */

        val expectedTaskTime =
            (1.0 - systemConfig.eta) * systemConfig.expectedTCloud() + systemConfig.eta * systemConfig.cpuNumberOfSections
        val expectedRhs = -(1.0 + 2.0) / systemConfig.alpha - expectedTaskTime

        assertThat(lp.rows[0].rhs)
            .isWithin(1e-6)
            .of(expectedRhs)
    }

    @Test
    fun testCoefficientsEquation2() {
        val systemConfig = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(systemConfig)
        val lp = lpCreator.createOffloadingLinearProgram().standardLinearProgram

        /*
                   Variable Index Table:

                   var id      | state         | action                     |Coeff  | Rhs
                   ----------------------------------------------------------------------
                   P           | (0, 0, 0)     | NoOperation                |       | Z0
                   Z           | (0, 0, 0)     | AddToCPU                   |       | Z0
                   Z           | (0, 0, 0)     | AddToTransmissionUnit      |       | Z0
                   Z           | (0, 0, 0)     | AddToBothUnits             |       | Z0
                   P           | (0, 0, 1)     | NoOperation                |       | L0
                   Z           | (0, 0, 1)     | AddToCPU                   |       | Z0
                   Z           | (0, 0, 1)     | AddToTransmissionUnit      |       | Z0
                   Z           | (0, 0, 1)     | AddToBothUnits             |       | Z0
                   P           | (0, 1, 0)     | NoOperation                |       | T0
                   Z           | (0, 1, 0)     | AddToCPU                   |       | Z0
                   Z           | (0, 1, 0)     | AddToTransmissionUnit      |       | Z0
                   Z           | (0, 1, 0)     | AddToBothUnits             |       | Z0
                   P           | (0, 1, 1)     | NoOperation                |       | B0
                   Z           | (0, 1, 1)     | AddToCPU                   |       | Z0
                   Z           | (0, 1, 1)     | AddToTransmissionUnit      |       | Z0
                   Z           | (0, 1, 1)     | AddToBothUnits             |       | Z0
                   0           | (1, 0, 0)     | NoOperation                | Z0    |
                   1           | (1, 0, 0)     | AddToCPU                   | L0    |
                   2           | (1, 0, 0)     | AddToTransmissionUnit      | T0    |
                   Z           | (1, 0, 0)     | AddToBothUnits             |       | Z0
                   3           | (1, 0, 1)     | NoOperation                | L0    |
                   Z           | (1, 0, 1)     | AddToCPU                   |       | Z0
                   4           | (1, 0, 1)     | AddToTransmissionUnit      | B0    |
                   Z           | (1, 0, 1)     | AddToBothUnits             |       | Z0
                   5           | (1, 1, 0)     | NoOperation                | T0    |
                   6           | (1, 1, 0)     | AddToCPU                   | B0    |
                   Z           | (1, 1, 0)     | AddToTransmissionUnit      |       | Z0
                   Z           | (1, 1, 0)     | AddToBothUnits             |       | Z0
                   P           | (1, 1, 1)     | NoOperation                |       | B0
                   Z           | (1, 1, 1)     | AddToCPU                   |       | Z0
                   Z           | (1, 1, 1)     | AddToTransmissionUnit      |       | Z0
                   Z           | (1, 1, 1)     | AddToBothUnits             |       | Z0
                   7           | (2, 0, 0)     | NoOperation                | Z0    |
                   8           | (2, 0, 0)     | AddToCPU                   | L0    |
                   9           | (2, 0, 0)     | AddToTransmissionUnit      | T0    |
                   10          | (2, 0, 0)     | AddToBothUnits             | B0    |
                   11          | (2, 0, 1)     | NoOperation                | L0    |
                   Z           | (2, 0, 1)     | AddToCPU                   |       | Z0
                   12          | (2, 0, 1)     | AddToTransmissionUnit      | B0    |
                   Z           | (2, 0, 1)     | AddToBothUnits             |       | Z0
                   13          | (2, 1, 0)     | NoOperation                | T0    |
                   14          | (2, 1, 0)     | AddToCPU                   | B0    |
                   Z           | (2, 1, 0)     | AddToTransmissionUnit      |       | Z0
                   Z           | (2, 1, 0)     | AddToBothUnits             |       | Z0
                   P           | (2, 1, 1)     | NoOperation                |       | B0
                   Z           | (2, 1, 1)     | AddToCPU                   |       | Z0
                   Z           | (2, 1, 1)     | AddToTransmissionUnit      |       | Z0
                   Z           | (2, 1, 1)     | AddToBothUnits             |       | Z0
                */
        val Z0 = 0.0
        val L0 = systemConfig.pLoc
        val T0 = systemConfig.beta * systemConfig.pTx
        val B0 = T0 + L0
        val expectedCoefficients = listOf<Double>(
            Z0, L0, T0, L0, B0, T0, B0, Z0, L0, T0, B0, L0, B0, T0, B0
        )

        assertThat(lp.rows[1].coefficients)
            .hasSize(15)

        assertThat(lp.rows[1].type)
            .isEqualTo(EquationRow.Type.LessThan)

        expectedCoefficients.forEachIndexed { index, d ->
            assertThat(lp.rows[1].coefficients[index])
                .isWithin(1e-3)
                .of(d)
        }
    }

    @Test
    fun testRhsEquation2() {
        val systemConfig = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(systemConfig)
        val lp = lpCreator.createOffloadingLinearProgram().standardLinearProgram

        /*
                   Variable Index Table:

                   var id      | state         | action                     |Coeff  | Rhs
                   ----------------------------------------------------------------------
                   P           | (0, 0, 0)     | NoOperation                |       | Z0
                   Z           | (0, 0, 0)     | AddToCPU                   |       |
                   Z           | (0, 0, 0)     | AddToTransmissionUnit      |       |
                   Z           | (0, 0, 0)     | AddToBothUnits             |       |
                   P           | (0, 0, 1)     | NoOperation                |       | L0
                   Z           | (0, 0, 1)     | AddToCPU                   |       |
                   Z           | (0, 0, 1)     | AddToTransmissionUnit      |       |
                   Z           | (0, 0, 1)     | AddToBothUnits             |       |
                   P           | (0, 1, 0)     | NoOperation                |       | T0
                   Z           | (0, 1, 0)     | AddToCPU                   |       |
                   Z           | (0, 1, 0)     | AddToTransmissionUnit      |       |
                   Z           | (0, 1, 0)     | AddToBothUnits             |       |
                   P           | (0, 1, 1)     | NoOperation                |       | B0
                   Z           | (0, 1, 1)     | AddToCPU                   |       |
                   Z           | (0, 1, 1)     | AddToTransmissionUnit      |       |
                   Z           | (0, 1, 1)     | AddToBothUnits             |       |
                   0           | (1, 0, 0)     | NoOperation                | Z0    |
                   1           | (1, 0, 0)     | AddToCPU                   | L0    |
                   2           | (1, 0, 0)     | AddToTransmissionUnit      | T0    |
                   Z           | (1, 0, 0)     | AddToBothUnits             |       |
                   3           | (1, 0, 1)     | NoOperation                | L0    |
                   Z           | (1, 0, 1)     | AddToCPU                   |       |
                   4           | (1, 0, 1)     | AddToTransmissionUnit      | B0    |
                   Z           | (1, 0, 1)     | AddToBothUnits             |       |
                   5           | (1, 1, 0)     | NoOperation                | T0    |
                   6           | (1, 1, 0)     | AddToCPU                   | B0    |
                   Z           | (1, 1, 0)     | AddToTransmissionUnit      |       |
                   Z           | (1, 1, 0)     | AddToBothUnits             |       |
                   P           | (1, 1, 1)     | NoOperation                |       | B0
                   Z           | (1, 1, 1)     | AddToCPU                   |       |
                   Z           | (1, 1, 1)     | AddToTransmissionUnit      |       |
                   Z           | (1, 1, 1)     | AddToBothUnits             |       |
                   7           | (2, 0, 0)     | NoOperation                | Z0    |
                   8           | (2, 0, 0)     | AddToCPU                   | L0    |
                   9           | (2, 0, 0)     | AddToTransmissionUnit      | T0    |
                   10          | (2, 0, 0)     | AddToBothUnits             | B0    |
                   11          | (2, 0, 1)     | NoOperation                | L0    |
                   Z           | (2, 0, 1)     | AddToCPU                   |       |
                   12          | (2, 0, 1)     | AddToTransmissionUnit      | B0    |
                   Z           | (2, 0, 1)     | AddToBothUnits             |       |
                   13          | (2, 1, 0)     | NoOperation                | T0    |
                   14          | (2, 1, 0)     | AddToCPU                   | B0    |
                   Z           | (2, 1, 0)     | AddToTransmissionUnit      |       |
                   Z           | (2, 1, 0)     | AddToBothUnits             |       |
                   P           | (2, 1, 1)     | NoOperation                |       | B0
                   Z           | (2, 1, 1)     | AddToCPU                   |       |
                   Z           | (2, 1, 1)     | AddToTransmissionUnit      |       |
                   Z           | (2, 1, 1)     | AddToBothUnits             |       |
                */
        val Z0 = 0.0
        val L0 = systemConfig.pLoc
        val T0 = systemConfig.beta * systemConfig.pTx
        val B0 = T0 + L0

        val expectedRhs = -(3 * B0 + T0 + L0) + systemConfig.pMax

        assertThat(lp.rows[1].type)
            .isEqualTo(EquationRow.Type.LessThan)

        assertThat(lp.rows[1].rhs)
            .isWithin(1e-6)
            .of(expectedRhs)
    }


    @Test
    fun testCoefficientsEquation3() {
        val config = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(config)
        val lp = lpCreator.createOffloadingLinearProgram().standardLinearProgram

        /*
                   Variable Index Table:

                    Trivial | var index | state         | action                     |Coeff  | Rhs
                   ------------------------------------------------------------------------------------
                    P       |           | (0, 0, 0)     | NoOperation                |       | Z0
                    Z       |           | (0, 0, 0)     | AddToCPU                   |       |
                    Z       |           | (0, 0, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 0, 0)     | AddToBothUnits             |       |
                    P       |           | (0, 0, 1)     | NoOperation                |       | L0
                    Z       |           | (0, 0, 1)     | AddToCPU                   |       |
                    Z       |           | (0, 0, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 0, 1)     | AddToBothUnits             |       |
                    P       |           | (0, 1, 0)     | NoOperation                |       | T0
                    Z       |           | (0, 1, 0)     | AddToCPU                   |       |
                    Z       |           | (0, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 1, 0)     | AddToBothUnits             |       |
                    P       |           | (0, 1, 1)     | NoOperation                |       | B0
                    Z       |           | (0, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (0, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 1, 1)     | AddToBothUnits             |       |
                            |  0        | (1, 0, 0)     | NoOperation                | TZ    |
                            |  1        | (1, 0, 0)     | AddToCPU                   | T1    |
                            |  2        | (1, 0, 0)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (1, 0, 0)     | AddToBothUnits             |       |
                            |  3        | (1, 0, 1)     | NoOperation                | TZ    |
                    Z       |           | (1, 0, 1)     | AddToCPU                   |       |
                            |  4        | (1, 0, 1)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (1, 0, 1)     | AddToBothUnits             |       |
                            |  5        | (1, 1, 0)     | NoOperation                | TZ    |
                            |  6        | (1, 1, 0)     | AddToCPU                   | T1    |
                    Z       |           | (1, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (1, 1, 0)     | AddToBothUnits             |       |
                    P       |           | (1, 1, 1)     | NoOperation                |       | B0
                    Z       |           | (1, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (1, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (1, 1, 1)     | AddToBothUnits             |       |
                            |  7        | (2, 0, 0)     | NoOperation                | TZ    |
                            |  8        | (2, 0, 0)     | AddToCPU                   | T1    |
                            |  9        | (2, 0, 0)     | AddToTransmissionUnit      | T2    |
                            |  10       | (2, 0, 0)     | AddToBothUnits             | T3    |
                            |  11       | (2, 0, 1)     | NoOperation                | TZ    |
                    Z       |           | (2, 0, 1)     | AddToCPU                   |       |
                            |  12       | (2, 0, 1)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (2, 0, 1)     | AddToBothUnits             |       |
                            |  13       | (2, 1, 0)     | NoOperation                | TZ    |
                            |  14       | (2, 1, 0)     | AddToCPU                   | T1    |
                    Z       |           | (2, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (2, 1, 0)     | AddToBothUnits             |       |
                    P       |           | (2, 1, 1)     | NoOperation                |       | B0
                    Z       |           | (2, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (2, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (2, 1, 1)     | AddToBothUnits             |       |
         */

        assertThat(lp.rows[2].coefficients)
            .hasSize(15)

        val eta = config.eta
        val TZ = 0.0
        val T1 = (1.0 - eta)
        val T2 = (-eta)
        val T3 = (1 - 2 * eta)
        val expectedCoefficients = mapOf(
            0 to TZ,
            1 to T1,
            2 to T2,
            3 to TZ,
            4 to T2,
            5 to TZ,
            6 to T1,
            7 to TZ,
            8 to T1,
            9 to T2,
            10 to T3,
            11 to TZ,
            12 to T2,
            13 to TZ,
            14 to T1,
        ).toList().sortedBy { it.first }.map { it.second }

        lp.rows[2].coefficients.forEachIndexed { index: Int, value: Double ->
            assertThat(value)
                .isWithin(1e-6)
                .of(expectedCoefficients[index])
        }
    }


    @Test
    fun testRhsEquation3() {
        val config = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(config)
        val lp = lpCreator.createOffloadingLinearProgram().standardLinearProgram

        /*
                   Variable Index Table:

                    Trivial | var index | state         | action                     |Coeff  | Rhs
                   ------------------------------------------------------------------------------------
                            |           | (0, 0, 0)     | NoOperation                |       | T0
                    Z       |           | (0, 0, 0)     | AddToCPU                   |       |
                    Z       |           | (0, 0, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 0, 0)     | AddToBothUnits             |       |
                            |           | (0, 0, 1)     | NoOperation                |       | T0
                    Z       |           | (0, 0, 1)     | AddToCPU                   |       |
                    Z       |           | (0, 0, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 0, 1)     | AddToBothUnits             |       |
                            |           | (0, 1, 0)     | NoOperation                |       | T0
                    Z       |           | (0, 1, 0)     | AddToCPU                   |       |
                    Z       |           | (0, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 1, 0)     | AddToBothUnits             |       |
                            |           | (0, 1, 1)     | NoOperation                |       | T0
                    Z       |           | (0, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (0, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 1, 1)     | AddToBothUnits             |       |
                            |  0        | (1, 0, 0)     | NoOperation                | TZ    |
                            |  1        | (1, 0, 0)     | AddToCPU                   | T1    |
                            |  2        | (1, 0, 0)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (1, 0, 0)     | AddToBothUnits             |       |
                            |  3        | (1, 0, 1)     | NoOperation                | TZ    |
                    Z       |           | (1, 0, 1)     | AddToCPU                   |       |
                            |  4        | (1, 0, 1)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (1, 0, 1)     | AddToBothUnits             |       |
                            |  5        | (1, 1, 0)     | NoOperation                | TZ    |
                            |  6        | (1, 1, 0)     | AddToCPU                   | T1    |
                    Z       |           | (1, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (1, 1, 0)     | AddToBothUnits             |       |
                            |           | (1, 1, 1)     | NoOperation                |       | T0
                    Z       |           | (1, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (1, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (1, 1, 1)     | AddToBothUnits             |       |
                            |  7        | (2, 0, 0)     | NoOperation                | TZ    |
                            |  8        | (2, 0, 0)     | AddToCPU                   | T1    |
                            |  9        | (2, 0, 0)     | AddToTransmissionUnit      | T2    |
                            |  10       | (2, 0, 0)     | AddToBothUnits             | T3    |
                            |  11       | (2, 0, 1)     | NoOperation                | TZ    |
                    Z       |           | (2, 0, 1)     | AddToCPU                   |       |
                            |  12       | (2, 0, 1)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (2, 0, 1)     | AddToBothUnits             |       |
                            |  13       | (2, 1, 0)     | NoOperation                | TZ    |
                            |  14       | (2, 1, 0)     | AddToCPU                   | T1    |
                    Z       |           | (2, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (2, 1, 0)     | AddToBothUnits             |       |
                            |           | (2, 1, 1)     | NoOperation                |       | T0
                    Z       |           | (2, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (2, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (2, 1, 1)     | AddToBothUnits             |       |
         */

        assertThat(lp.rows[2].type)
            .isEqualTo(EquationRow.Type.Equality)

        val eta = config.eta
        val TZ = 0.0
        val T1 = (1.0 - eta)
        val T2 = (-eta)
        val T3 = (1 - 2 * eta)
        val expectedRhs: Double = 0.0

        assertThat(lp.rows[2].rhs)
            .isWithin(1e-6)
            .of(expectedRhs)
    }

    @Test
    fun testCoefficientsEquation5() {
        val config = getSimpleConfig()

        val lpCreator = OffloadingLPCreator(config)
        val lp = lpCreator.createOffloadingLinearProgram().standardLinearProgram

        /*
                   Variable Index Table:

                    Trivial | var index | state         | action                     |Coeff  | Rhs
                   ------------------------------------------------------------------------------------
                    P       |           | (0, 0, 0)     | NoOperation                |       |
                    Z       |           | (0, 0, 0)     | AddToCPU                   |       |
                    Z       |           | (0, 0, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 0, 0)     | AddToBothUnits             |       |
                    P       |           | (0, 0, 1)     | NoOperation                |       | T0
                    Z       |           | (0, 0, 1)     | AddToCPU                   |       |
                    Z       |           | (0, 0, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 0, 1)     | AddToBothUnits             |       |
                    P       |           | (0, 1, 0)     | NoOperation                |       | T0
                    Z       |           | (0, 1, 0)     | AddToCPU                   |       |
                    Z       |           | (0, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 1, 0)     | AddToBothUnits             |       |
                    P       |           | (0, 1, 1)     | NoOperation                |       | T0
                    Z       |           | (0, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (0, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (0, 1, 1)     | AddToBothUnits             |       |
                            |  0        | (1, 0, 0)     | NoOperation                | TZ    |
                            |  1        | (1, 0, 0)     | AddToCPU                   | T1    |
                            |  2        | (1, 0, 0)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (1, 0, 0)     | AddToBothUnits             |       |
                            |  3        | (1, 0, 1)     | NoOperation                | TZ    |
                    Z       |           | (1, 0, 1)     | AddToCPU                   |       |
                            |  4        | (1, 0, 1)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (1, 0, 1)     | AddToBothUnits             |       |
                            |  5        | (1, 1, 0)     | NoOperation                | TZ    |
                            |  6        | (1, 1, 0)     | AddToCPU                   | T1    |
                    Z       |           | (1, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (1, 1, 0)     | AddToBothUnits             |       |
                    P       |           | (1, 1, 1)     | NoOperation                |       | T0
                    Z       |           | (1, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (1, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (1, 1, 1)     | AddToBothUnits             |       |
                            |  7        | (2, 0, 0)     | NoOperation                | TZ    |
                            |  8        | (2, 0, 0)     | AddToCPU                   | T1    |
                            |  9        | (2, 0, 0)     | AddToTransmissionUnit      | T2    |
                            |  10       | (2, 0, 0)     | AddToBothUnits             | T3    |
                            |  11       | (2, 0, 1)     | NoOperation                | TZ    |
                    Z       |           | (2, 0, 1)     | AddToCPU                   |       |
                            |  12       | (2, 0, 1)     | AddToTransmissionUnit      | T2    |
                    Z       |           | (2, 0, 1)     | AddToBothUnits             |       |
                            |  13       | (2, 1, 0)     | NoOperation                | TZ    |
                            |  14       | (2, 1, 0)     | AddToCPU                   | T1    |
                    Z       |           | (2, 1, 0)     | AddToTransmissionUnit      |       |
                    Z       |           | (2, 1, 0)     | AddToBothUnits             |       |
                    P       |           | (2, 1, 1)     | NoOperation                |       | T0
                    Z       |           | (2, 1, 1)     | AddToCPU                   |       |
                    Z       |           | (2, 1, 1)     | AddToTransmissionUnit      |       |
                    Z       |           | (2, 1, 1)     | AddToBothUnits             |       |
         */

        assertThat(lp.rows[2].type)
            .isEqualTo(EquationRow.Type.Equality)

        val eta = config.eta
        val TZ = 0.0
        val T1 = (1.0 - eta)
        val T2 = (-eta)
        val T3 = (1 - 2 * eta)
        val expectedRhs: Double = 0.0

        assertThat(lp.rows[2].rhs)
            .isWithin(1e-6)
            .of(expectedRhs)

    }

     */
}