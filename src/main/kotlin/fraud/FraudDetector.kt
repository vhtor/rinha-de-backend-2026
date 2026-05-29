package com.vhtor.fraud

import com.vhtor.data.MccRiskMap
import com.vhtor.data.NormalizationConfig
import com.vhtor.data.ReferenceStore
import com.vhtor.models.FraudRequest
import com.vhtor.search.VectorIndex
import com.vhtor.vectorization.Vectorizer

/**
 * Orquestra o pipeline completo de detecção de fraude:
 * FraudRequest → vetor 14D → k-NN search → fraud_score → decisão.
 */
class FraudDetector(
    private val vectorIndex: VectorIndex,
    private val store: ReferenceStore,
    private val normalization: NormalizationConfig,
    private val mccRisk: MccRiskMap
) {
    companion object {
        const val APPROVAL_THRESHOLD = 0.6f
    }

    /**
     * Processa uma transação e retorna o resultado de fraude.
     *
     * Pipeline:
     * 1. Vectorizer.vectorize(request) → FloatArray(14)
     * 2. vectorIndex.findNearest(vector) → List<Neighbor> (k=5)
     * 3. Conta quantos vizinhos são fraude via store.isFraud()
     * 4. fraud_score = fraudCount / neighborsFound
     * 5. approved = fraud_score < 0.6
     *
     * @param request Payload completo da transação
     * @return FraudResult com id, score e decisão
     */
    fun evaluate(request: FraudRequest): FraudResult {
        val queryVector = Vectorizer.vectorize(request, normalization, mccRisk)
        val neighbors = vectorIndex.findNearest(queryVector)
        val fraudCount = neighbors.count { neighbor -> store.isFraud(neighbor.index) }

        val fraudScore = if (neighbors.isNotEmpty()) {
            fraudCount.toFloat() / neighbors.size
        } else {
            0f
        }

        val approved = fraudScore < APPROVAL_THRESHOLD

        return FraudResult(
            id = request.id,
            fraudScore = fraudScore,
            approved = approved
        )
    }
}
