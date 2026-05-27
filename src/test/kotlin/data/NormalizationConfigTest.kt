package com.vhtor.data

import kotlin.test.*

class NormalizationConfigTest {

    private val testResourcesDir = "src/test/resources"

    @Test
    fun `loads normalization config with correct values`() {
        val config = loadNormalization("$testResourcesDir/normalization.json")

        assertEquals(10000f, config.maxAmount)
        assertEquals(12f, config.maxInstallments)
        assertEquals(10f, config.amountVsAvgRatio)
        assertEquals(1440f, config.maxMinutes)
        assertEquals(1000f, config.maxKm)
        assertEquals(20f, config.maxTxCount24h)
        assertEquals(10000f, config.maxMerchantAvgAmount)
    }

    @Test
    fun `all normalization values are positive`() {
        val config = loadNormalization("$testResourcesDir/normalization.json")

        assertTrue(config.maxAmount > 0f)
        assertTrue(config.maxInstallments > 0f)
        assertTrue(config.amountVsAvgRatio > 0f)
        assertTrue(config.maxMinutes > 0f)
        assertTrue(config.maxKm > 0f)
        assertTrue(config.maxTxCount24h > 0f)
        assertTrue(config.maxMerchantAvgAmount > 0f)
    }
}
