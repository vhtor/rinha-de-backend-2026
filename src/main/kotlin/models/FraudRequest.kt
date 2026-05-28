package com.vhtor.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FraudRequest(
    val id: String,
    val transaction: Transaction,
    val customer: Customer,
    val merchant: Merchant,
    val terminal: Terminal,
    @SerialName("last_transaction") val lastTransaction: LastTransaction?
)

@Serializable
data class Transaction(
    val amount: Float,
    val installments: Int,
    @SerialName("requested_at") val requestedAt: String
)

@Serializable
data class Customer(
    @SerialName("avg_amount") val avgAmount: Float,
    @SerialName("tx_count_24h") val txCount24h: Int,
    @SerialName("known_merchants") val knownMerchants: List<String>
)

@Serializable
data class Merchant(
    val id: String,
    val mcc: String,
    @SerialName("avg_amount") val avgAmount: Float
)

@Serializable
data class Terminal(
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("card_present") val cardPresent: Boolean,
    @SerialName("km_from_home") val kmFromHome: Float
)

@Serializable
data class LastTransaction(
    val timestamp: String,
    @SerialName("km_from_current") val kmFromCurrent: Float
)
