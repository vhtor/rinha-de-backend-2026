package com.vhtor.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureHealthRoutes() {
    routing {
        get("/ready") {
            call.respond(HttpStatusCode.OK, "Application is ready!")
        }
    }
}
