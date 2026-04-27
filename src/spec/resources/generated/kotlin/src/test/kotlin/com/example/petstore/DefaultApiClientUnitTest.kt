package com.example.petstore

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DefaultApiClientUnitTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Nested
    @DisplayName("User-Agent injection")
    inner class UserAgentInjection {
        @Test
        @DisplayName("injects custom User-Agent header")
        fun injectsCustomUserAgent() {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val transport =
                TransportOptions
                    .builder()
                    .userAgent("MyApp/1.0")
                    .build()
            val client = DefaultApiClient(transport)
            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", emptyMap(), null)
            }
            val request = server.takeRequest()
            assertEquals("MyApp/1.0", request.getHeader("User-Agent"))
        }

        @Test
        @DisplayName("injects default User-Agent when not explicitly set")
        fun injectsDefaultUserAgent() {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val client = DefaultApiClient()
            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", emptyMap(), null)
            }
            val request = server.takeRequest()
            val ua = request.getHeader("User-Agent")
            assertNotNull(ua)
            assertTrue(ua!!.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("X-Request-ID injection")
    inner class RequestIdInjection {
        @Test
        @DisplayName("injects X-Request-ID when enabled")
        fun injectsRequestId() {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val transport =
                TransportOptions
                    .builder()
                    .injectRequestId(true)
                    .build()
            val client = DefaultApiClient(transport)
            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", emptyMap(), null)
            }
            val request = server.takeRequest()
            val requestId = request.getHeader("X-Request-ID")
            assertNotNull(requestId)
            assertTrue(
                requestId!!.matches(
                    Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                ),
            )
        }

        @Test
        @DisplayName("does not inject X-Request-ID when disabled")
        fun doesNotInjectRequestIdWhenDisabled() {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val transport =
                TransportOptions
                    .builder()
                    .injectRequestId(false)
                    .build()
            val client = DefaultApiClient(transport)
            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", emptyMap(), null)
            }
            val request = server.takeRequest()
            assertNull(request.getHeader("X-Request-ID"))
        }

        @Test
        @DisplayName("does not override caller-provided X-Request-ID")
        fun doesNotOverrideCallerRequestId() {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val transport =
                TransportOptions
                    .builder()
                    .injectRequestId(true)
                    .build()
            val client = DefaultApiClient(transport)
            val headers = mapOf("X-Request-ID" to "caller-provided-id")
            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", headers, null)
            }
            val request = server.takeRequest()
            assertEquals("caller-provided-id", request.getHeader("X-Request-ID"))
        }

        @Test
        @DisplayName("generates unique X-Request-ID per request")
        fun generatesUniqueRequestIds() {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val transport =
                TransportOptions
                    .builder()
                    .injectRequestId(true)
                    .build()
            val client = DefaultApiClient(transport)

            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", emptyMap(), null)
            }
            val id1 = server.takeRequest().getHeader("X-Request-ID")

            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", emptyMap(), null)
            }
            val id2 = server.takeRequest().getHeader("X-Request-ID")

            assertNotEquals(id1, id2)
        }
    }

    @Nested
    @DisplayName("default headers")
    inner class DefaultHeaders {
        @Test
        @DisplayName("includes transport-level default headers")
        fun includesTransportDefaultHeaders() {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val transport =
                TransportOptions
                    .builder()
                    .defaultHeader("X-Custom", "custom-value")
                    .build()
            val client = DefaultApiClient(transport)
            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", emptyMap(), null)
            }
            val request = server.takeRequest()
            assertEquals("custom-value", request.getHeader("X-Custom"))
        }

        @Test
        @DisplayName("caller headers override transport default headers")
        fun callerHeadersOverrideDefaults() {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val transport =
                TransportOptions
                    .builder()
                    .defaultHeader("Accept", "text/plain")
                    .build()
            val client = DefaultApiClient(transport)
            val callerHeaders = mapOf("Accept" to "application/json")
            runBlocking {
                client.sendRequest("GET", baseUrl() + "/test", callerHeaders, null)
            }
            val request = server.takeRequest()
            assertEquals("application/json", request.getHeader("Accept"))
        }
    }
}
