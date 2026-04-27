package com.example.petstore

/**
 * Generated server configurations from the OpenAPI specification.
 */
object Servers {
    /** Server 0: /api/v3 - Relative URL (no variables) */
    val SERVER_0: ServerConfiguration =
        ServerConfiguration(
            "/api/v3",
            "Relative URL (no variables)",
            emptyMap(),
        )

    /** Server 1: https://{environment}.example.com/api/{version} - Main API server with variables */
    val SERVER_1: ServerConfiguration =
        ServerConfiguration(
            "https://{environment}.example.com/api/{version}",
            "Main API server with variables",
            mapOf(
                "environment" to
                    ServerVariable(
                        "api",
                        "API environment",
                        listOf("api", "staging", "sandbox"),
                    ),
                "version" to
                    ServerVariable(
                        "v3",
                        "API version",
                        listOf("v2", "v3"),
                    ),
            ),
        )

    /** All server configurations in declaration order. */
    val ALL: List<ServerConfiguration> =
        listOf(
            SERVER_0,
            SERVER_1,
        )
}
