package com.vhtor.data

import kotlin.test.*

class MccRiskMapTest {

    private val testResourcesDir = "src/test/resources"

    private lateinit var mccRiskMap: MccRiskMap

    @BeforeTest
    fun setup() {
        mccRiskMap = loadMccRisk("$testResourcesDir/mcc_risk.json")
    }

    @Test
    fun `returns correct risk for known MCC codes`() {
        assertEquals(0.15f, mccRiskMap.getRisk("5411"))
        assertEquals(0.80f, mccRiskMap.getRisk("7801"))
        assertEquals(0.85f, mccRiskMap.getRisk("7995"))
        assertEquals(0.35f, mccRiskMap.getRisk("4511"))
    }

    @Test
    fun `returns default 0_5 for unknown MCC codes`() {
        assertEquals(0.5f, mccRiskMap.getRisk("9999"))
        assertEquals(0.5f, mccRiskMap.getRisk("0000"))
        assertEquals(0.5f, mccRiskMap.getRisk(""))
    }

    @Test
    fun `all known MCC risks are between 0 and 1`() {
        val knownCodes = listOf("5411", "5812", "5912", "5944", "7801", "7802", "7995", "4511", "5311", "5999")
        for (code in knownCodes) {
            val risk = mccRiskMap.getRisk(code)
            assertTrue(risk in 0f..1f, "MCC $code has risk $risk outside [0, 1]")
        }
    }
}
