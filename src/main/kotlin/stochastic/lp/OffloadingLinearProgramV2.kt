package stochastic.lp

import core.ue.OffloadingSystemConfig
import ue.UserEquipmentState

class OffloadingLinearProgramV2(
    val cObjective: Map<Index, Double>,
    val rhsObjective: Double,
    val cEquation2: Map<Index, Double>,
    val rhsEquation2: Double,
    val cEquation3: Map<Index, Double>,
    val rhsEquation3: Double,
    val cEquation4: Map<UserEquipmentState, Map<Index, Double>>,
    val rhsEquation4: Double,
    val cEquation5: Map<Index, Double>,
    val rhsEquation5: Double,
    val config: OffloadingSystemConfig
) : StandardLinearProgram {

    override val rows: List<EquationRow> = run {
        TODO()
    }


}