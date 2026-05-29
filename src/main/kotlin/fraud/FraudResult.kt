package com.vhtor.fraud

/**
 * Resultado da análise de fraude para uma transação.
 *
 * @property id Identificador único da transação (passthrough do request)
 * @property fraudScore Proporção de vizinhos fraudulentos (0.0 a 1.0)
 * @property approved true se fraudScore < 0.6 (transação aprovada)
 */
data class FraudResult(
    val id: String,
    val fraudScore: Float,
    val approved: Boolean
) {}
