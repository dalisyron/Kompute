package stochastic.lp

data class OffloadingEtaType(
    val queueTypes: List<Int>
) {

    companion object {
        private const val TYPE_OFFLOAD_ONLY = 0
        private const val TYPE_LOCAL_ONLY = 1
        private const val TYPE_BOTH = 2

        fun fromEtaConfig(etaConfig: List<Double>): OffloadingEtaType {
            val queueTypes = etaConfig.map {
                when (it) {
                    0.0 -> {
                        TYPE_OFFLOAD_ONLY
                    }
                    1.0 -> {
                        TYPE_LOCAL_ONLY
                    }
                    else -> {
                        TYPE_BOTH
                    }
                }
            }

            return OffloadingEtaType(queueTypes)
        }
    }
}