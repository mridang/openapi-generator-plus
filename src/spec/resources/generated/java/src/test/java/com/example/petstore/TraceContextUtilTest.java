package com.example.petstore;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for TraceContextUtil. */
class TraceContextUtilTest {

  @Test
  @DisplayName("should inject traceparent when a mock propagator is registered")
  void shouldInjectTraceparentWithMockPropagator() {
    TextMapPropagator propagator =
        new TextMapPropagator() {
          @Override
          public Collection<String> fields() {
            return List.of("traceparent");
          }

          @Override
          public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
            setter.set(carrier, "traceparent", "00-abcdef1234567890abcdef1234567890-0123456789abcdef-01");
          }

          @Override
          public <C> Context extract(
              Context context, @Nullable C carrier, TextMapGetter<C> getter) {
            return context;
          }
        };

    GlobalOpenTelemetry.resetForTest();
    GlobalOpenTelemetry.set(
        OpenTelemetry.propagating(ContextPropagators.create(propagator)));
    try {
      Map<String, String> headers = new HashMap<>();
      TraceContextUtil.injectTraceContext(headers);
      assertTrue(headers.containsKey("traceparent"), "traceparent header should be present");
      assertEquals(
          "00-abcdef1234567890abcdef1234567890-0123456789abcdef-01",
          headers.get("traceparent"));
    } finally {
      GlobalOpenTelemetry.resetForTest();
    }
  }

  @Test
  @DisplayName("should not inject traceparent when no propagator is configured")
  void shouldNotInjectTraceparentWithoutActivePropagator() {
    GlobalOpenTelemetry.resetForTest();
    try {
      Map<String, String> headers = new HashMap<>();
      TraceContextUtil.injectTraceContext(headers);
      assertFalse(headers.containsKey("traceparent"), "traceparent header should not be present");
    } finally {
      GlobalOpenTelemetry.resetForTest();
    }
  }

  @Test
  @DisplayName("should not throw any exception")
  void shouldNotThrowAnyException() {
    GlobalOpenTelemetry.resetForTest();
    try {
      Map<String, String> headers = new HashMap<>();
      assertDoesNotThrow(() -> TraceContextUtil.injectTraceContext(headers));
    } finally {
      GlobalOpenTelemetry.resetForTest();
    }
  }
}
