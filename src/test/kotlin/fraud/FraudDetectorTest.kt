package com.vhtor.fraud

import com.vhtor.data.MccRiskMap
import com.vhtor.data.NormalizationConfig
import com.vhtor.data.ReferenceStore
import com.vhtor.models.*
import com.vhtor.search.VectorIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FraudDetectorTest {

    private fun createNormalization() = NormalizationConfig(
        maxAmount = 10000f,
        maxInstallments = 12f,
        amountVsAvgRatio = 10f,
        maxMinutes = 1440f,
        maxKm = 1000f,
        maxTxCount24h = 20f,
        maxMerchantAvgAmount = 10000f
    )

    private fun createMccRisk() = MccRiskMap(mapOf("5411" to 0.1f, "7995" to 0.9f))

    /**
     * Cria um store onde os 5 primeiros vetores são fraude e os 5 seguintes legit.
     * Todos os vetores têm valores separados para que a busca seja determinística.
     *
     * Vetores de fraude: centrados em [0.9, 0.9, ..., 0.9] (14D)
     * Vetores legit: centrados em [0.1, 0.1, ..., 0.1] (14D)
     */
    private fun createTestStore(): ReferenceStore {
        val dim = 14
        val numVectors = 10

        val vectors = FloatArray(numVectors * dim)
        val labels = BooleanArray(numVectors)

        // Primeiros 5: fraude (valores altos ~0.9)
        for (i in 0 until 5) {
            for (d in 0 until dim) {
                vectors[i * dim + d] = 0.85f + (i * 0.02f) // 0.85, 0.87, 0.89, 0.91, 0.93
            }
            labels[i] = true
        }

        // Últimos 5: legit (valores baixos ~0.1)
        for (i in 5 until 10) {
            for (d in 0 until dim) {
                vectors[i * dim + d] = 0.05f + ((i - 5) * 0.02f) // 0.05, 0.07, 0.09, 0.11, 0.13
            }
            labels[i] = false
        }

        return ReferenceStore(vectors, labels, numVectors, dim)
    }

    private fun createDetector(store: ReferenceStore): FraudDetector {
        val vectorIndex = VectorIndex(store)
        return FraudDetector(vectorIndex, store, createNormalization(), createMccRisk())
    }

    /**
     * Request que gera vetor próximo da zona de fraude (~0.9 em todas as dimensões).
     * amount=9000 → 0.9, installments=11 → 0.917, etc.
     */
    private fun createFraudulentRequest() = FraudRequest(
        id = "tx-fraud-001",
        transaction = Transaction(
            amount = 9000f,
            installments = 11,
            requestedAt = "2026-03-15T21:00:00Z" // hour=21 → 21/23=0.913, day=Sun → 6/6=1.0
        ),
        customer = Customer(
            avgAmount = 1000f, // amount_vs_avg = 9000/1000/10 = 0.9
            txCount24h = 18,   // 18/20 = 0.9
            knownMerchants = listOf("merchant-other")
        ),
        merchant = Merchant(
            id = "merchant-unknown",
            mcc = "7995",      // mcc_risk = 0.9
            avgAmount = 9000f  // 9000/10000 = 0.9
        ),
        terminal = Terminal(
            isOnline = true,     // 1.0
            cardPresent = false, // 0.0 (diverge, mas ok)
            kmFromHome = 900f    // 900/1000 = 0.9
        ),
        lastTransaction = LastTransaction(
            timestamp = "2026-03-15T20:50:00Z", // 10 min → 10/1440 = 0.007 (baixo, diverge)
            kmFromCurrent = 900f                 // 900/1000 = 0.9
        )
    )

    /**
     * Request que gera vetor próximo da zona legítima (~0.1 em todas as dimensões).
     */
    private fun createLegitRequest() = FraudRequest(
        id = "tx-legit-001",
        transaction = Transaction(
            amount = 100f,
            installments = 1,
            requestedAt = "2026-03-11T02:00:00Z" // hour=2 → 2/23=0.087, day=Wed → 2/6=0.333
        ),
        customer = Customer(
            avgAmount = 1000f,  // 100/1000/10 = 0.01
            txCount24h = 1,     // 1/20 = 0.05
            knownMerchants = listOf("merchant-known")
        ),
        merchant = Merchant(
            id = "merchant-known",
            mcc = "5411",      // mcc_risk = 0.1
            avgAmount = 1000f  // 1000/10000 = 0.1
        ),
        terminal = Terminal(
            isOnline = false,    // 0.0
            cardPresent = true,  // 1.0 (diverge, mas ok)
            kmFromHome = 50f     // 50/1000 = 0.05
        ),
        lastTransaction = LastTransaction(
            timestamp = "2026-03-11T01:50:00Z", // 10 min → 10/1440 = 0.007
            kmFromCurrent = 50f                  // 50/1000 = 0.05
        )
    )

    @Test
    fun `fraudulent request returns high score and not approved`() {
        val store = createTestStore()
        val detector = createDetector(store)

        val result = detector.evaluate(createFraudulentRequest())

        assertEquals("tx-fraud-001", result.id)
        // Os 5 vizinhos mais próximos do vetor ~0.9 devem ser os 5 de fraude
        assertTrue(result.fraudScore >= 0.6f, "Fraud score should be >= 0.6, was ${result.fraudScore}")
        assertFalse(result.approved, "Should NOT be approved")
    }

    @Test
    fun `legitimate request returns low score and approved`() {
        val store = createTestStore()
        val detector = createDetector(store)

        val result = detector.evaluate(createLegitRequest())

        assertEquals("tx-legit-001", result.id)
        // Os 5 vizinhos mais próximos do vetor ~0.1 devem ser os 5 legit
        assertTrue(result.fraudScore < 0.6f, "Fraud score should be < 0.6, was ${result.fraudScore}")
        assertTrue(result.approved, "Should be approved")
    }

    @Test
    fun `score is exactly fraud_count divided by neighbors_found`() {
        val dim = 14

        // 8 vetores no total:
        // - 5 vetores "perto" (todos iguais) -> serão os top-5
        // - 3 vetores "longe" -> nunca entram no top-5
        val vectors = FloatArray(8 * dim)
        val labels = BooleanArray(8)

        // Top-5 controlado: 3 fraudes e 2 legítimos => 3/5 = 0.6
        val top5Labels = booleanArrayOf(true, true, true, false, false)

        // Vetores próximos (indices 0..4) todos em 0.5
        for (i in 0 until 5) {
            for (d in 0 until dim) {
                vectors[i * dim + d] = 0.5f
            }
            labels[i] = top5Labels[i]
        }

        // Outliers bem distantes (indices 5..7), labels não importam
        for (i in 5 until 8) {
            for (d in 0 until dim) {
                vectors[i * dim + d] = 1.0f
            }
            labels[i] = false
        }

        val store = ReferenceStore(vectors, labels, 8, dim)
        val detector = createDetector(store)

        // Reaproveita request já usado em outros testes.
        // Mesmo que o query real não seja "exatamente 0.5", os vetores 0..4
        // continuam estritamente mais próximos do que os outliers em 1.0.
        val request = FraudRequest(
            id = "tx-mixed",
            transaction = Transaction(amount = 5000f, installments = 6, requestedAt = "2026-03-12T12:00:00Z"),
            customer = Customer(avgAmount = 1000f, txCount24h = 10, knownMerchants = emptyList()),
            merchant = Merchant(id = "m1", mcc = "5411", avgAmount = 5000f),
            terminal = Terminal(isOnline = true, cardPresent = true, kmFromHome = 500f),
            lastTransaction = LastTransaction(timestamp = "2026-03-12T00:00:00Z", kmFromCurrent = 500f)
        )

        val result = detector.evaluate(request)

        assertEquals(0.6f, result.fraudScore, 0.001f)
        assertFalse(result.approved, "Score 0.6 should NOT be approved (threshold is strictly <)")
    }

    @Test
    fun `boundary case - score exactly at threshold is not approved`() {
        // Este teste valida que 0.6 (3/5) NÃO é aprovado
        // A spec diz: approved = fraud_score < 0.6 (estritamente menor)
        // Portanto score = 0.6 → NOT approved

        // Criamos um cenário onde exatamente 3 de 5 vizinhos são fraude
        val dim = 14
        val vectors = FloatArray(5 * dim)
        val labels = booleanArrayOf(true, true, true, false, false)

        // Todos no mesmo ponto (distância ~0 entre eles)
        for (i in 0 until 5) {
            for (d in 0 until dim) {
                vectors[i * dim + d] = 0.5f
            }
        }

        val store = ReferenceStore(vectors, labels, 5, dim)
        val detector = createDetector(store)

        val request = FraudRequest(
            id = "tx-boundary",
            transaction = Transaction(amount = 5000f, installments = 6, requestedAt = "2026-03-12T12:00:00Z"),
            customer = Customer(avgAmount = 1000f, txCount24h = 10, knownMerchants = emptyList()),
            merchant = Merchant(id = "m1", mcc = "5411", avgAmount = 5000f),
            terminal = Terminal(isOnline = true, cardPresent = true, kmFromHome = 500f),
            lastTransaction = LastTransaction(timestamp = "2026-03-12T00:00:00Z", kmFromCurrent = 500f)
        )

        val result = detector.evaluate(request)

        // 3 frauds / 5 = 0.6 → NOT approved
        assertEquals(0.6f, result.fraudScore, 0.001f)
        assertFalse(result.approved, "Exactly 0.6 must NOT be approved (< is strict)")
    }

    @Test
    fun `all neighbors legit gives score zero and approved`() {
        val dim = 14
        val vectors = FloatArray(5 * dim)
        val labels = booleanArrayOf(false, false, false, false, false) // all legit

        for (i in 0 until 5) {
            for (d in 0 until dim) {
                vectors[i * dim + d] = 0.5f
            }
        }

        val store = ReferenceStore(vectors, labels, 5, dim)
        val detector = createDetector(store)

        val request = FraudRequest(
            id = "tx-all-legit",
            transaction = Transaction(amount = 5000f, installments = 6, requestedAt = "2026-03-12T12:00:00Z"),
            customer = Customer(avgAmount = 1000f, txCount24h = 10, knownMerchants = emptyList()),
            merchant = Merchant(id = "m1", mcc = "5411", avgAmount = 5000f),
            terminal = Terminal(isOnline = true, cardPresent = true, kmFromHome = 500f),
            lastTransaction = LastTransaction(timestamp = "2026-03-12T00:00:00Z", kmFromCurrent = 500f)
        )

        val result = detector.evaluate(request)

        assertEquals(0f, result.fraudScore, 0.001f)
        assertTrue(result.approved)
    }
}
