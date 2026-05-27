package com.vhtor.data

import kotlin.test.*

class DataLoaderTest {

    private val testResourcesDir = "src/test/resources"

    @Test
    fun `loadAll returns complete DataContext`() {
        val context = DataLoader.loadAll(testResourcesDir, expectedReferenceSize = 5)

        assertNotNull(context.normalization)
        assertNotNull(context.mccRisk)
        assertNotNull(context.references)
    }

    @Test
    fun `loadAll integrates all three loaders`() {
        val context = DataLoader.loadAll(testResourcesDir, expectedReferenceSize = 5)

        // Normalization loaded correctly
        assertEquals(10000f, context.normalization.maxAmount)

        // MCC risk loaded correctly
        assertEquals(0.15f, context.mccRisk.getRisk("5411"))

        // References loaded correctly
        assertEquals(5, context.references.size)
        assertEquals(14, context.references.dimensions)
    }

    @Test
    fun `loadAll fails with clear error on invalid path`() {
        assertFailsWith<Exception> {
            DataLoader.loadAll("/nonexistent/path")
        }
    }
}
