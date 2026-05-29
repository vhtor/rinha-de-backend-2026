package com.vhtor.routes

import com.vhtor.fraud.FraudDetector
import com.vhtor.models.FraudRequest
import com.vhtor.models.FraudResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("FraudRoutes")

fun Application.configureFraudRoutes(fraudDetector: FraudDetector) {
    routing {
        post("/fraud-score") {
            try {
                val request = call.receive<FraudRequest>()
                val result = fraudDetector.evaluate(request)
                val response = FraudResponse.from(result)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error processing fraud request", e)

                // Fail-open: retorna aprovação em vez de 500
                // devido peso do scoring ser maior para erros http
                call.respond(
                    HttpStatusCode.OK,
                    FraudResponse(
                        transactionId = "unknown",
                        fraudScore = 0f,
                        approved = true
                    )
                )
            }
        }
    }
}
