package com.vhtor

import io.ktor.server.engine.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    embeddedServer(
        factory = io.ktor.server.cio.CIO,
        port = 9999,
        host = "0.0.0.0",
        module = Application::rootModule
    ).start(wait = true)
}
