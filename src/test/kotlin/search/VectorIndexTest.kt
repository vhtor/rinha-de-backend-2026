package com.vhtor.search

import com.vhtor.data.ReferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorIndexTest {

    /**
     * Helper: cria ByteArray quantizado a partir de floats para facilitar os testes.
     */
    private fun quantizedVectors(vararg floats: Float): ByteArray {
        return ByteArray(floats.size) { i -> ReferenceStore.quantize(floats[i]) }
    }

    private fun createTestStore(): ReferenceStore {
        val vectors = quantizedVectors(
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

        val query = floatArrayOf(1.0f, 0.96f, 0.96f)
        val neighbors = index.findNearest(query)

        assertEquals(5, neighbors.size, "Should return K=5 neighbors")
        assertEquals(3, neighbors[0].index, "Nearest should be index 3")

        for (i in 0 until neighbors.size - 1) {
            assertTrue(
                neighbors[i].distanceSquared <= neighbors[i + 1].distanceSquared,
                "Neighbors should be sorted by distance"
            )
        }
    }

    @Test
    fun `returns fewer than K when store has fewer vectors`() {
        val vectors = quantizedVectors(
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

        // Query idêntico ao vetor de index 3 — após quantização, distância deve ser 0
        val query = floatArrayOf(0.9708f, 1.0000f, 1.00f)
        val neighbors = index.findNearest(query)

        assertEquals(3, neighbors[0].index, "Exact match should be first")
        // Distância pode ser 0 ou muito próxima (quantização round-trip pode ter ±1 step)
        assertTrue(neighbors[0].distanceSquared <= 3, "Distance should be ~0 (quantization noise only)")
    }

    @Test
    fun `handles sentinel values correctly`() {
        val vectors = quantizedVectors(
            -1f, -1f, 0.5f,
            0.3f, 0.4f, 0.5f,
            -1f, -1f, 0.6f,
            0.8f, 0.9f, 0.5f
        )
        val labels = booleanArrayOf(true, false, true, false)
        val store = ReferenceStore(vectors, labels, 4, 3)
        val index = VectorIndex(store)

        val query = floatArrayOf(-1f, -1f, 0.55f)
        val neighbors = index.findNearest(query)

        val firstTwo = neighbors.take(2).map { it.index }.toSet()
        assertTrue(firstTwo.contains(0), "Sentinel vector 0 should be near")
        assertTrue(firstTwo.contains(2), "Sentinel vector 2 should be near")
    }

    @Test
    fun `works with 14 dimensions`() {
        val dim = 14
        val numVectors = 100

        val floats = FloatArray(numVectors * dim) { i -> (i % 7) / 10f }
        val vectors = ByteArray(floats.size) { i -> ReferenceStore.quantize(floats[i]) }
        val labels = BooleanArray(numVectors) { it % 3 == 0 }
        val store = ReferenceStore(vectors, labels, numVectors, dim)
        val index = VectorIndex(store)

        val query = FloatArray(dim) { 0.5f }
        val neighbors = index.findNearest(query)

        assertEquals(5, neighbors.size)
        for (i in 0 until neighbors.size - 1) {
            assertTrue(neighbors[i].distanceSquared <= neighbors[i + 1].distanceSquared)
        }
    }
}
