package com.vhtor.routes

import com.vhtor.data.DataLoader
import com.vhtor.fraud.FraudDetector
import com.vhtor.search.VectorIndex
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FraudRoutesTest {

    /**
     * Carrega um dataset mínimo para testes.
     * Usa os mesmos arquivos de teste do DataLoaderTest (em src/test/resources/).
     */
    private fun createTestDetector(): FraudDetector {
        val resourcesDir = System.getProperty("TEST_RESOURCES_DIR")
            ?: "src/test/resources"
        val dataContext = DataLoader.loadAll(resourcesDir, expectedReferenceSize = 6)
        val vectorIndex = VectorIndex(dataContext.references)
        return FraudDetector(vectorIndex, dataContext.references, dataContext.normalization, dataContext.mccRisk)
    }

    @Test
    fun `POST fraud-score returns 200 with valid JSON response`() = testApplication {
        val fraudDetector = createTestDetector()

        install(ContentNegotiation) { json() }

        application {
            configureFraudRoutes(fraudDetector)
        }

        val requestJson = """
        {
            "id": "tx-test-001",
            "transaction": {
                "amount": 150.00,
                "installments": 1,
                "requested_at": "2026-03-11T14:30:00Z"
            },
            "customer": {
                "avg_amount": 200.00,
                "tx_count_24h": 2,
                "known_merchants": ["merchant-abc"]
            },
            "merchant": {
                "id": "merchant-abc",
                "mcc": "5411",
                "avg_amount": 300.00
            },
            "terminal": {
                "is_online": false,
                "card_present": true,
                "km_from_home": 5.0
            },
            "last_transaction": {
                "timestamp": "2026-03-11T10:00:00Z",
                "km_from_current": 3.0
            }
        }
        """.trimIndent()

        val response = client.post("/fraud-score") {
            contentType(ContentType.Application.Json)
            setBody(requestJson)
        }

        // Deve retornar 200
        assertEquals(HttpStatusCode.OK, response.status)

        // Deve ter os campos esperados no JSON
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body.containsKey("transaction_id"), "Response must have transaction_id")
        assertTrue(body.containsKey("fraud_score"), "Response must have fraud_score")
        assertTrue(body.containsKey("approved"), "Response must have approved")

        // transaction_id deve ser o mesmo do request
        assertEquals("tx-test-001", body["transaction_id"]!!.jsonPrimitive.content)

        // fraud_score deve estar entre 0 e 1
        val score = body["fraud_score"]!!.jsonPrimitive.float
        assertTrue(score in 0f..1f, "fraud_score must be in [0, 1], was $score")
    }

    @Test
    fun `POST fraud-score with null last_transaction works`() = testApplication {
        val fraudDetector = createTestDetector()

        install(ContentNegotiation) { json() }

        application {
            configureFraudRoutes(fraudDetector)
        }

        val requestJson = """
        {
            "id": "tx-no-last",
            "transaction": {
                "amount": 500.00,
                "installments": 3,
                "requested_at": "2026-03-12T09:00:00Z"
            },
            "customer": {
                "avg_amount": 400.00,
                "tx_count_24h": 5,
                "known_merchants": []
            },
            "merchant": {
                "id": "merchant-xyz",
                "mcc": "7995",
                "avg_amount": 600.00
            },
            "terminal": {
                "is_online": true,
                "card_present": false,
                "km_from_home": 200.0
            },
            "last_transaction": null
        }
        """.trimIndent()

        val response = client.post("/fraud-score") {
            contentType(ContentType.Application.Json)
            setBody(requestJson)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("tx-no-last", body["transaction_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST fraud-score with malformed JSON returns 200 fail-open`() = testApplication {
        val fraudDetector = createTestDetector()

        install(ContentNegotiation) { json() }

        application {
            configureFraudRoutes(fraudDetector)
        }

        val response = client.post("/fraud-score") {
            contentType(ContentType.Application.Json)
            setBody("{ invalid json }")
        }

        // Fail-open: retorna 200 com approved=true em vez de 400/500
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["approved"]!!.jsonPrimitive.boolean, "Malformed request should fail-open (approved=true)")
        assertEquals(0f, body["fraud_score"]!!.jsonPrimitive.float, 0.001f)
    }
}
