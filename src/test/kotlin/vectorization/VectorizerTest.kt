package com.vhtor.vectorization

import com.vhtor.data.MccRiskMap
import com.vhtor.data.NormalizationConfig
import com.vhtor.models.*
import kotlin.test.Test
import kotlin.test.assertEquals

class VectorizerTest {

    // Constantes da spec (normalization.json)
    private val normalization = NormalizationConfig(
        maxAmount = 10000f,
        maxInstallments = 12f,
        amountVsAvgRatio = 10f,
        maxMinutes = 1440f,
        maxKm = 1000f,
        maxTxCount24h = 20f,
        maxMerchantAvgAmount = 10000f
    )

    // mcc_risk.json parcial — só os MCCs usados nos exemplos
    private val mccRisk = MccRiskMap(mapOf(
        "5411" to 0.15f,
        "7802" to 0.75f,
        "5912" to 0.30f
    ))

    private val tolerance = 0.001f

    /**
     * Exemplo legítimo da spec (REGRAS_DE_DETECCAO.md):
     * Compra de baixo valor, perto de casa, comerciante conhecido, sem transação anterior.
     */
    @Test
    fun `vectorize legitimate transaction from spec`() {
        val request = FraudRequest(
            id = "tx-1329056812",
            transaction = Transaction(
                amount = 41.12f,
                installments = 2,
                requestedAt = "2026-03-11T18:45:53Z"
            ),
            customer = Customer(
                avgAmount = 82.24f,
                txCount24h = 3,
                knownMerchants = listOf("MERC-003", "MERC-016")
            ),
            merchant = Merchant(
                id = "MERC-016",
                mcc = "5411",
                avgAmount = 60.25f
            ),
            terminal = Terminal(
                isOnline = false,
                cardPresent = true,
                kmFromHome = 29.23f
            ),
            lastTransaction = null
        )

        val expected = floatArrayOf(
            0.0041f,  // amount: 41.12 / 10000
            0.1667f,  // installments: 2 / 12
            0.05f,    // amount_vs_avg: (41.12 / 82.24) / 10
            0.7826f,  // hour: 18 / 23
            0.3333f,  // day: Tuesday(1) / 6
            -1f,      // minutes_since_last: null → -1
            -1f,      // km_from_last: null → -1
            0.0292f,  // km_from_home: 29.23 / 1000
            0.15f,    // tx_count_24h: 3 / 20
            0f,       // is_online: false
            1f,       // card_present: true
            0f,       // unknown_merchant: MERC-016 IN known_merchants
            0.15f,    // mcc_risk: 5411 → 0.15
            0.006f    // merchant_avg: 60.25 / 10000
        )

        val result = Vectorizer.vectorize(request, normalization, mccRisk)

        expected.forEachIndexed { i, exp ->
            assertEquals(exp, result[i], tolerance, "Dimension $i mismatch")
        }
    }

    /**
     * Exemplo fraudulento da spec:
     * Valor alto, longe de casa, comerciante desconhecido, sem transação anterior.
     */
    @Test
    fun `vectorize fraudulent transaction from spec`() {
        val request = FraudRequest(
            id = "tx-3330991687",
            transaction = Transaction(
                amount = 9505.97f,
                installments = 10,
                requestedAt = "2026-03-14T05:15:12Z"
            ),
            customer = Customer(
                avgAmount = 81.28f,
                txCount24h = 20,
                knownMerchants = listOf("MERC-008", "MERC-007", "MERC-005")
            ),
            merchant = Merchant(
                id = "MERC-068",
                mcc = "7802",
                avgAmount = 54.86f
            ),
            terminal = Terminal(
                isOnline = false,
                cardPresent = true,
                kmFromHome = 952.27f
            ),
            lastTransaction = null
        )

        val expected = floatArrayOf(
            0.9506f,  // amount: 9505.97 / 10000
            0.8333f,  // installments: 10 / 12
            1.0f,     // amount_vs_avg: (9505.97 / 81.28) / 10 = 11.69 → clamp(1.0)
            0.2174f,  // hour: 5 / 23
            0.8333f,  // day: Saturday(5) / 6
            -1f,      // minutes_since_last: null
            -1f,      // km_from_last: null
            0.9523f,  // km_from_home: 952.27 / 1000
            1.0f,     // tx_count_24h: 20 / 20
            0f,       // is_online: false
            1f,       // card_present: true
            1f,       // unknown_merchant: MERC-068 NOT in known_merchants
            0.75f,    // mcc_risk: 7802 → 0.75
            0.0055f   // merchant_avg: 54.86 / 10000
        )

        val result = Vectorizer.vectorize(request, normalization, mccRisk)

        expected.forEachIndexed { i, exp ->
            assertEquals(exp, result[i], tolerance, "Dimension $i mismatch")
        }
    }

    /**
     * Testa last_transaction presente — dimensões 5 e 6 devem ser calculadas.
     */
    @Test
    fun `vectorize with last_transaction present`() {
        val request = FraudRequest(
            id = "tx-test",
            transaction = Transaction(
                amount = 384.88f,
                installments = 3,
                requestedAt = "2026-03-11T20:23:35Z"
            ),
            customer = Customer(
                avgAmount = 769.76f,
                txCount24h = 3,
                knownMerchants = listOf("MERC-009", "MERC-001")
            ),
            merchant = Merchant(
                id = "MERC-001",
                mcc = "5912",
                avgAmount = 298.95f
            ),
            terminal = Terminal(
                isOnline = false,
                cardPresent = true,
                kmFromHome = 13.71f
            ),
            lastTransaction = LastTransaction(
                timestamp = "2026-03-11T14:58:35Z",
                kmFromCurrent = 18.86f
            )
        )

        val result = Vectorizer.vectorize(request, normalization, mccRisk)

        // Dimensão 5: minutos entre 14:58:35 e 20:23:35 = 325 minutos
        // 325 / 1440 = 0.2257
        assertEquals(0.2257f, result[5], tolerance, "Dimension 5: minutes_since_last")

        // Dimensão 6: km_from_current / max_km = 18.86 / 1000 = 0.01886
        assertEquals(0.0189f, result[6], tolerance, "Dimension 6: km_from_last")
    }

    /**
     * Testa que valores extremos são clampados corretamente.
     */
    @Test
    fun `vectorize clamps extreme values`() {
        val request = FraudRequest(
            id = "tx-extreme",
            transaction = Transaction(
                amount = 99999f,  // muito acima de max_amount (10000)
                installments = 48, // muito acima de max_installments (12)
                requestedAt = "2026-01-01T00:00:00Z"
            ),
            customer = Customer(
                avgAmount = 1f,    // avg muito baixo → ratio explode
                txCount24h = 100,  // muito acima de max (20)
                knownMerchants = emptyList()
            ),
            merchant = Merchant(
                id = "MERC-999",
                mcc = "0000",      // MCC desconhecido → default 0.5
                avgAmount = 50000f // acima de max
            ),
            terminal = Terminal(
                isOnline = true,
                cardPresent = false,
                kmFromHome = 5000f // acima de max_km (1000)
            ),
            lastTransaction = null
        )

        val result = Vectorizer.vectorize(request, normalization, mccRisk)

        // Todos os valores com clamp devem ser <= 1.0
        assertEquals(1.0f, result[0], tolerance, "amount clamped")
        assertEquals(1.0f, result[1], tolerance, "installments clamped")
        assertEquals(1.0f, result[2], tolerance, "amount_vs_avg clamped")
        assertEquals(1.0f, result[7], tolerance, "km_from_home clamped")
        assertEquals(1.0f, result[8], tolerance, "tx_count_24h clamped")
        assertEquals(1.0f, result[13], tolerance, "merchant_avg clamped")

        // Boolean → 0/1
        assertEquals(1f, result[9], tolerance, "is_online = true → 1")
        assertEquals(0f, result[10], tolerance, "card_present = false → 0")
        assertEquals(1f, result[11], tolerance, "unknown merchant → 1")

        // MCC desconhecido → default 0.5
        assertEquals(0.5f, result[12], tolerance, "unknown MCC → 0.5")
    }
}
