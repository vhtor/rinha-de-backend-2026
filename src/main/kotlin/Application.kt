package com.vhtor

import com.vhtor.data.DataLoader
import com.vhtor.fraud.FraudDetector
import com.vhtor.routes.configureFraudRoutes
import com.vhtor.routes.configureHealthRoutes
import com.vhtor.search.VectorIndex
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.rootModule() {
    val resourcesDir = System.getenv("RESOURCES_DIR")
        ?: System.getProperty("RESOURCES_DIR")
        ?: "./resources"
    val expectedSize = System.getProperty("EXPECTED_REFERENCE_SIZE")?.toIntOrNull() ?: 3_000_000
    val dataContext = DataLoader.loadAll(resourcesDir, expectedSize)
    val vectorIndex = VectorIndex(dataContext.references)

    val fraudDetector = FraudDetector(
        vectorIndex = vectorIndex,
        store = dataContext.references,
        normalization = dataContext.normalization,
        mccRisk = dataContext.mccRisk
    )

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    configureHealthRoutes()
    configureFraudRoutes(fraudDetector)
}
