package com.example.petstore

/**
 * Utility for injecting W3C Trace Context headers.
 */
object TraceContextUtil {
    @JvmStatic
    fun injectTraceContext(headers: MutableMap<String, String>) {
        try {
            io.opentelemetry.api.GlobalOpenTelemetry
                .getPropagators()
                .textMapPropagator
                .inject(
                    io.opentelemetry.context.Context
                        .current(),
                    headers,
                ) { carrier, key, value -> carrier?.put(key, value) }
        } catch (_: LinkageError) {
        } catch (_: RuntimeException) {
        }
    }
}
