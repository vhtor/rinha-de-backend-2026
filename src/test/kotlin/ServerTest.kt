package com.vhtor

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {

    @BeforeTest
    fun setup() {
        System.setProperty("RESOURCES_DIR", "src/test/resources")
        System.setProperty("EXPECTED_REFERENCE_SIZE", "5")
    }

    @Test
    fun `ready endpoint returns 200 after data is loaded`() = testApplication {
        application {
            rootModule()
        }
        val response = client.get("/ready")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
