package com.vhtor.data

class MccRiskMap(private val risks: Map<String, Float>) {
    companion object {
        private const val DEFAULT_RISK = 0.5f
    }

    fun getRisk(mcc: String): Float = risks.getOrDefault(mcc, DEFAULT_RISK)
}
