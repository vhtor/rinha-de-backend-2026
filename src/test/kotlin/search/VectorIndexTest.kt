package com.vhtor.search

import com.vhtor.data.ReferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorIndexTest {

    /**
     * Cria um ReferenceStore com vetores conhecidos para testes.
     * Usa 3 dimensões para simplicidade (o algoritmo é agnostico ao número de dimensões).
     */
    private fun createTestStore(): ReferenceStore {
        // 6 vetores de 3 dimensões (mesmo exemplo da doc BUSCA_VETORIAL.md)
        val vectors = floatArrayOf(
            0.01f, 0.0833f, 0.05f,    // index 0: legit
            0.5796f, 0.9167f, 1.00f,  // index 1: fraud
            0.0035f, 0.1667f, 0.05f,  // index 2: legit
            0.9708f, 1.0000f, 1.00f,  // index 3: fraud
            0.4082f, 1.0000f, 1.00f,  // index 4: fraud
            0.0092f, 0.0833f, 0.05f   // index 5: legit
        )
        val labels = booleanArrayOf(false, true, false, true, true, false)
        return ReferenceStore(vectors, labels, 6, 3)
    }

    @Test
    fun `finds 5 nearest neighbors in correct order`() {
        val store = createTestStore()
        val index = VectorIndex(store)

        // Query: [1.0, 0.96, 0.96] — próximo dos vetores de fraude
        val query = floatArrayOf(1.0f, 0.96f, 0.96f)
        val neighbors = index.findNearest(query)

        assertEquals(5, neighbors.size, "Should return K=5 neighbors")

        // O vizinho mais próximo deve ser o index 3: [0.9708, 1.0, 1.0]
        assertEquals(3, neighbors[0].index, "Nearest should be index 3")

        // Verifica ordenação: distâncias devem ser crescentes
        for (i in 0 until neighbors.size - 1) {
            assertTrue(
                neighbors[i].distanceSquared <= neighbors[i + 1].distanceSquared,
                "Neighbors should be sorted by distance"
            )
        }
    }

    @Test
    fun `returns fewer than K when store has fewer vectors`() {
        // Store com apenas 3 vetores
        val vectors = floatArrayOf(
            0.1f, 0.2f, 0.3f,
            0.4f, 0.5f, 0.6f,
            0.7f, 0.8f, 0.9f
        )
        val labels = booleanArrayOf(false, true, false)
        val store = ReferenceStore(vectors, labels, 3, 3)
        val index = VectorIndex(store)

        val query = floatArrayOf(0.5f, 0.5f, 0.5f)
        val neighbors = index.findNearest(query)

        assertEquals(3, neighbors.size, "Should return only 3 (less than K=5)")
    }

    @Test
    fun `exact match has distance zero`() {
        val store = createTestStore()
        val index = VectorIndex(store)

        // Query idêntico ao vetor de index 3
        val query = floatArrayOf(0.9708f, 1.0000f, 1.00f)
        val neighbors = index.findNearest(query)

        assertEquals(3, neighbors[0].index, "Exact match should be first")
        assertEquals(0f, neighbors[0].distanceSquared, 0.0001f, "Distance should be ~0")
    }

    @Test
    fun `handles sentinel values correctly`() {
        // Vetores com sentinela -1 (simula last_transaction = null)
        val vectors = floatArrayOf(
            -1f, -1f, 0.5f,    // index 0: tem sentinela
            0.3f, 0.4f, 0.5f,  // index 1: valores normais
            -1f, -1f, 0.6f,    // index 2: tem sentinela
            0.8f, 0.9f, 0.5f   // index 3: valores normais
        )
        val labels = booleanArrayOf(true, false, true, false)
        val store = ReferenceStore(vectors, labels, 4, 3)
        val index = VectorIndex(store)

        // Query com sentinela: deve estar mais próximo de outros com sentinela
        val query = floatArrayOf(-1f, -1f, 0.55f)
        val neighbors = index.findNearest(query)

        // Os dois primeiros devem ser index 0 e 2 (também têm sentinela)
        val firstTwo = neighbors.take(2).map { it.index }.toSet()
        assertTrue(firstTwo.contains(0), "Sentinel vector 0 should be near")
        assertTrue(firstTwo.contains(2), "Sentinel vector 2 should be near")
    }

    @Test
    fun `works with 14 dimensions`() {
        // Teste com dimensionalidade real do projeto
        val dim = 14
        val numVectors = 100

        // Gera vetores aleatórios mas determinísticos
        val vectors = FloatArray(numVectors * dim) { i -> (i % 7) / 10f }
        val labels = BooleanArray(numVectors) { it % 3 == 0 }
        val store = ReferenceStore(vectors, labels, numVectors, dim)
        val index = VectorIndex(store)

        val query = FloatArray(dim) { 0.5f }
        val neighbors = index.findNearest(query)

        assertEquals(5, neighbors.size)
        // Verifica ordenação
        for (i in 0 until neighbors.size - 1) {
            assertTrue(neighbors[i].distanceSquared <= neighbors[i + 1].distanceSquared)
        }
    }
}
