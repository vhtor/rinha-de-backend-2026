package com.vhtor.models

import com.vhtor.fraud.FraudResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FraudResponse(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("fraud_score") val fraudScore: Float,
    val approved: Boolean
) {
    companion object {
        fun from(result: FraudResult): FraudResponse {
            return FraudResponse(
                transactionId = result.id,
                fraudScore = result.fraudScore,
                approved = result.approved
            )
        }
    }
}
