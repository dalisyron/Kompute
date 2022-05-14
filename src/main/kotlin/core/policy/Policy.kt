package policy

import ue.UserEquipmentState

interface Policy {

    fun getActionForState(state: UserEquipmentState): Action
}