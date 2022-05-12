package dtmc

import ue.UserEquipmentStateConfig

class IndependentTransitionSymbolsCalculator(
    private val stateConfig: UserEquipmentStateConfig
) {

    private val dtmcCreator: DTMCCreator = DTMCCreator(stateConfig)

}