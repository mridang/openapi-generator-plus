defmodule PetstoreClient.TraceContextUtilTest do
  use ExUnit.Case, async: true

  require OpenTelemetry.Tracer, as: Tracer

  # A propagator that writes a traceparent only for a carrier holding the marker
  # header, so the tests of this module that inject without it see no change.
  defmodule MarkedPropagator do
    def inject(_context, carrier, set_fun, _options) do
      if Map.has_key?(carrier, "x-marked") do
        set_fun.(
          "traceparent",
          "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
          carrier
        )
      else
        carrier
      end
    end
  end

  defp traceparent(span_ctx, flags) do
    "00-#{:otel_span.hex_trace_id(span_ctx)}-#{:otel_span.hex_span_id(span_ctx)}-#{flags}"
  end

  # A remote parent span arriving with the given flags and tracestate.
  defp remote_parent(flags, tracestate) do
    :otel_propagator_text_map.extract([
      {"traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-#{flags}"},
      {"tracestate", tracestate}
    ])
  end

  describe "inject_trace_context/1" do
    test "bhv tracing 007 is a no-op without tracer" do
      headers = %{}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result == %{}
    end

    test "bhv tracing 003 empty headers do not cause exception" do
      headers = %{}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result == %{}
    end

    test "bhv tracing 001 does not inject traceparent without OTel" do
      headers = %{}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      refute Map.has_key?(result, "traceparent")
    end

    test "bhv tracing 002 does not inject tracestate without OTel" do
      headers = %{}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      refute Map.has_key?(result, "tracestate")
    end

    test "bhv tracing 010 preserves Authorization header" do
      headers = %{"Authorization" => "Bearer token123"}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result["Authorization"] == "Bearer token123"
    end

    test "bhv tracing 011 preserves Content-Type header" do
      headers = %{"Content-Type" => "application/json"}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result["Content-Type"] == "application/json"
    end

    test "bhv tracing 012 preserves X-Request-ID header" do
      headers = %{"X-Request-ID" => "req-12345"}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result["X-Request-ID"] == "req-12345"
    end

    test "bhv tracing 009 preserves all existing headers" do
      headers = %{
        "Authorization" => "Bearer token",
        "Content-Type" => "application/json",
        "X-Request-ID" => "abc-123"
      }

      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert map_size(result) == 3
      assert result["Authorization"] == "Bearer token"
      assert result["Content-Type"] == "application/json"
      assert result["X-Request-ID"] == "abc-123"
    end
  end

  test "bhv misc 002 a registered trace propagator is used to inject trace context and keeps headers already present" do
    previous = :opentelemetry.get_text_map_injector()
    :opentelemetry.set_text_map_injector(MarkedPropagator)
    on_exit(fn -> :opentelemetry.set_text_map_injector(previous) end)

    headers =
      PetstoreClient.TraceContextUtil.inject_trace_context(%{
        "x-marked" => "1",
        "x-keep" => "yes"
      })

    assert headers["traceparent"] == "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
    assert headers["x-keep"] == "yes"
    assert headers["x-marked"] == "1"
  end

  describe "inject_trace_context/1 with an active OpenTelemetry span" do
    # The OpenTelemetry SDK runs with a simple processor whose exporter sends
    # every finished span to this test process: an in-memory exporter.
    setup do
      Application.load(:opentelemetry)
      Application.put_env(:opentelemetry, :traces_exporter, :none)
      Application.put_env(:opentelemetry, :processors, [{:otel_simple_processor, %{}}])
      {:ok, _} = Application.ensure_all_started(:opentelemetry)
      :otel_simple_processor.set_exporter(:otel_exporter_pid, self())
      :ok
    end

    test "bhv tracing 006 injects traceparent when a span is active" do
      Tracer.with_span "request" do
        headers = PetstoreClient.TraceContextUtil.inject_trace_context(%{})
        assert headers["traceparent"] == traceparent(Tracer.current_span_ctx(), "01")
      end

      assert_receive {:span, _span}
    end

    test "bhv tracing 005 includes tracestate when present on the active span" do
      remote_parent("01", "vendor=value")

      Tracer.with_span "request" do
        headers = PetstoreClient.TraceContextUtil.inject_trace_context(%{})
        assert headers["tracestate"] == "vendor=value"
      end
    end

    test "bhv tracing 008 omits tracestate when empty on the active span" do
      Tracer.with_span "request" do
        headers = PetstoreClient.TraceContextUtil.inject_trace_context(%{})
        assert Map.has_key?(headers, "traceparent")
        refute Map.has_key?(headers, "tracestate")
      end
    end

    test "bhv tracing 004 formats trace flags correctly on the active span" do
      Tracer.with_span "sampled" do
        headers = PetstoreClient.TraceContextUtil.inject_trace_context(%{})
        assert headers["traceparent"] == traceparent(Tracer.current_span_ctx(), "01")
      end

      # The default sampler follows the parent, so a child of an unsampled
      # remote parent is not sampled either.
      remote_parent("00", "")

      Tracer.with_span "unsampled" do
        headers = PetstoreClient.TraceContextUtil.inject_trace_context(%{})
        assert headers["traceparent"] == traceparent(Tracer.current_span_ctx(), "00")
      end
    end
  end
end
