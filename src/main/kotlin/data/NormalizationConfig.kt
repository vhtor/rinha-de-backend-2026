package com.vhtor.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NormalizationConfig(
    @SerialName("max_amount") val maxAmount: Float,
    @SerialName("max_installments") val maxInstallments: Float,
    @SerialName("amount_vs_avg_ratio") val amountVsAvgRatio: Float,
    @SerialName("max_minutes") val maxMinutes: Float,
    @SerialName("max_km") val maxKm: Float,
    @SerialName("max_tx_count_24h") val maxTxCount24h: Float,
    @SerialName("max_merchant_avg_amount") val maxMerchantAvgAmount: Float
)
