package com.vhtor.vectorization

import com.vhtor.data.MccRiskMap
import com.vhtor.data.NormalizationConfig
import com.vhtor.models.FraudRequest
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

object Vectorizer {
    private const val DIMENSIONS = 14

    fun vectorize(
        request: FraudRequest,
        normalization: NormalizationConfig,
        mccRisk: MccRiskMap
    ): FloatArray {
        val vector = FloatArray(DIMENSIONS)

        val tx = request.transaction
        val customer = request.customer
        val merchant = request.merchant
        val terminal = request.terminal
        val lastTx = request.lastTransaction

        val requestedAt = Instant.parse(tx.requestedAt)
        val dateTime = requestedAt.atOffset(ZoneOffset.UTC)

        // Dimensão 0: amount normalizado
        // Divide pelo máximo e clamp: valores > max_amount viram 1.0
        vector[0] = clamp(tx.amount / normalization.maxAmount)

        // Dimensão 1: installments normalizado
        // Divide por 12 (máximo de parcelas) e clamp
        vector[1] = clamp(tx.installments.toFloat() / normalization.maxInstallments)

        // Dimensão 2: razão amount / média do cliente
        // Captura o quanto esta transação é atípica para este cliente
        // Dividido adicionalmente por amount_vs_avg_ratio (10) para normalizar
        val customerTxRatio = tx.amount / customer.avgAmount
        vector[2] = clamp(customerTxRatio / normalization.amountVsAvgRatio)

        // Dimensão 3: hora do dia (UTC)
        // 0 = meia-noite, 23 = 23h → normalizado dividindo por 23
        // Transações em horários incomuns (madrugada) terão valores baixos
        vector[3] = dateTime.hour.toFloat() / 23f

        // Dimensão 4: dia da semana
        // java.time: MONDAY=1 ... SUNDAY=7
        // Spec: Monday=0, Sunday=6 → subtraímos 1
        // Normalizado dividindo por 6
        val dayOfWeek = dateTime.dayOfWeek.value - 1
        vector[4] = dayOfWeek.toFloat() / 6f

        // Dimensões 5 e 6: dependem de last_transaction
        if (lastTx == null) {
            // Sentinela -1: "não há transação anterior"
            // Fica FORA do range [0,1] — a distância euclidiana
            // Tratar esses vetores como cluster separado
            vector[5] = -1f
            vector[6] = -1f
        } else {
            // Dimensão 5: minutos desde a última transação
            // Mede velocidade de gasto — muitas transações em pouco tempo é suspeito
            val lastTimestamp = Instant.parse(lastTx.timestamp)
            val minutes = Duration.between(lastTimestamp, requestedAt).toMinutes().toFloat()
            vector[5] = clamp(minutes / normalization.maxMinutes)

            // Dimensão 6: km entre a transação anterior e a atual
            // Captura "viagens impossíveis" — compra em SP e 5 min depois em RJ
            vector[6] = clamp(lastTx.kmFromCurrent / normalization.maxKm)
        }

        // Dimensão 7: distância da residência do portador
        // Compras longe de casa são mais suspeitas
        vector[7] = clamp(terminal.kmFromHome / normalization.maxKm)

        // Dimensão 8: quantidade de transações nas últimas 24h
        // Muitas transações em sequência podem indicar teste de cartão
        vector[8] = clamp(customer.txCount24h.toFloat() / normalization.maxTxCount24h)

        // Dimensão 9: transação online?
        // Boolean → 0/1 — transações online têm perfil de risco diferente
        vector[9] = if (terminal.isOnline) 1f else 0f

        // Dimensão 10: cartão presente?
        // Sem cartão presente = mais risco (digitou os dados)
        vector[10] = if (terminal.cardPresent) 1f else 0f

        // Dimensão 11: comerciante desconhecido?
        // 1 = nunca comprou lá antes — fator de risco
        vector[11] = if (merchant.id !in customer.knownMerchants) 1f else 0f

        // Dimensão 12: risco do MCC (categoria do comerciante)
        // Alguns MCCs (ex: 7995 = apostas) são mais arriscados
        vector[12] = mccRisk.getRisk(merchant.mcc)

        // Dimensão 13: ticket médio do comerciante normalizado
        // Transação em loja de ticket médio baixo com valor alto é suspeita
        vector[13] = clamp(merchant.avgAmount / normalization.maxMerchantAvgAmount)

        return vector
    }

    // Mantém o valor no intervalo [0.0, 1.0].
    private inline fun clamp(value: Float): Float = value.coerceIn(0f, 1f)
}
