package com.vhtor.data

import kotlin.test.*

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
    fun `first vector values are correct`() {
        val firstVector = store.getVector(0)
        assertEquals(14, firstVector.size)
        assertEquals(0.01f, firstVector[0], 0.0001f)
        assertEquals(0.0833f, firstVector[1], 0.0001f)
        assertEquals(0.05f, firstVector[2], 0.0001f)
        assertEquals(0.8261f, firstVector[3], 0.0001f)
        assertEquals(0.0416f, firstVector[13], 0.0001f)
    }

    @Test
    fun `sentinel values minus one are preserved`() {
        // First vector has -1 at indices 5 and 6
        val firstVector = store.getVector(0)
        assertEquals(-1f, firstVector[5], 0.0001f)
        assertEquals(-1f, firstVector[6], 0.0001f)
    }

    @Test
    fun `non-sentinel values at indices 5 and 6 are loaded correctly`() {
        // Fifth vector (index 4) has actual values at indices 5 and 6
        val fifthVector = store.getVector(4)
        assertEquals(0.1f, fifthVector[5], 0.0001f)
        assertEquals(0.2f, fifthVector[6], 0.0001f)
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
    fun `all vector values are in valid range`() {
        for (i in 0 until store.size) {
            val vector = store.getVector(i)
            for (d in 0 until store.dimensions) {
                val value = vector[d]
                // Valid range: [-1, 1] (sentinel -1 allowed at indices 5 and 6)
                assertTrue(
                    value in -1f..1f,
                    "Vector $i, dimension $d has value $value outside [-1, 1]"
                )
            }
        }
    }

    @Test
    fun `getVector returns independent copies`() {
        val v1 = store.getVector(0)
        val v2 = store.getVector(0)
        // Modifying one should not affect the other
        v1[0] = 999f
        assertNotEquals(v1[0], v2[0])
    }
}
