package com.vhtor.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

object DataLoader {
    private val logger = LoggerFactory.getLogger(DataLoader::class.java)

    fun loadAll(resourcesDir: String, expectedReferenceSize: Int = 3_000_000): DataContext {
        logger.info("Loading data pipeline...")

        val startTime = System.currentTimeMillis()

        val normalization = loadNormalization("$resourcesDir/normalization.json")
        logger.info("   normalization.json loaded!")

        val mccRisk = loadMccRisk("$resourcesDir/mcc_risk.json")
        logger.info("   mcc_risk.json loaded!")

        val references = ReferenceLoader.load("$resourcesDir/references.json.gz", expectedReferenceSize)
        logger.info("   references.json.gz loaded!")

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("Data pipeline loaded in ${elapsed}ms")

        return DataContext(normalization, mccRisk, references)
    }
}

data class DataContext(
    val normalization: NormalizationConfig,
    val mccRisk: MccRiskMap,
    val references: ReferenceStore
)

fun loadNormalization(path: String): NormalizationConfig {
    val content = File(path).readText()
    return Json.decodeFromString<NormalizationConfig>(content)
}

fun loadMccRisk(path: String): MccRiskMap {
    val content = File(path).readText()
    val jsonObject = Json.decodeFromString<JsonObject>(content)
    val map = jsonObject.mapValues { (_, value) -> value.jsonPrimitive.float }
    return MccRiskMap(map)
}
