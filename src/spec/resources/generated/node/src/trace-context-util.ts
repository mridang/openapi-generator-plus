/**
 * Utility for injecting W3C Trace Context headers (traceparent, tracestate)
 * into outgoing API requests when OpenTelemetry is available.
 *
 * If the @opentelemetry/api package is not installed, this function silently no-ops.
 */

const OTEL_MODULE = '@opentelemetry/api';

/**
 * Inject the current OpenTelemetry trace context into the given headers object.
 *
 * @param headers - mutable record of request headers
 */
export async function injectTraceContext(headers: Record<string, string>): Promise<void> {
  try {
    const otel = await (import(OTEL_MODULE) as Promise<{
      propagation: { inject: (ctx: unknown, carrier: unknown) => void };
      context: { active: () => unknown };
    }>);
    otel.propagation.inject(otel.context.active(), headers);
  } catch {
    /* empty */
  }
}
