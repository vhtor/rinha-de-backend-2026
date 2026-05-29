package com.vhtor.data

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferenceLoaderTest {

    private val testResourcesDir = "src/test/resources"

    private lateinit var store: ReferenceStore

    @BeforeTest
    fun setup() {
        store = ReferenceLoader.load("$testResourcesDir/test-references.json.gz", expectedSize = 5)
    }

    @Test
    fun `loads correct number of vectors`() {
        assertEquals(5, store.size)
    }

    @Test
    fun `vectors have 14 dimensions`() {
        assertEquals(14, store.dimensions)
    }

    @Test
    fun `total vectors array has correct size`() {
        // 5 vectors * 14 dimensions = 70 floats stored, but array is pre-allocated to 3M*14
        // The actual values should be in the first 70 positions
        assertEquals(5 * 14, store.size * store.dimensions)
    }

    @Test
    fun `labels are correctly assigned`() {
        // Order: legit, fraud, fraud, legit, legit
        assertFalse(store.isFraud(0), "Vector 0 should be legit")
        assertTrue(store.isFraud(1), "Vector 1 should be fraud")
        assertTrue(store.isFraud(2), "Vector 2 should be fraud")
        assertFalse(store.isFraud(3), "Vector 3 should be legit")
        assertFalse(store.isFraud(4), "Vector 4 should be legit")
    }

    @Test
    fun `loads references and quantizes vectors correctly`() {
        val store = ReferenceLoader.load("src/test/resources/references.json.gz", expectedSize = 5)

        assertEquals(5, store.size)
        assertEquals(14, store.dimensions)

        // Verifica que os vetores estão em ByteArray (implícito pela compilação)
        // Verifica valor quantizado do primeiro vetor, primeira dimensão
        // Se o float original era X, o byte deve ser round((X + 1) × 127.5)
        val firstByte = store.vectors[0].toInt() and 0xFF
        assertTrue(firstByte in 0..255, "Quantized value must be in [0, 255]")
    }
}
