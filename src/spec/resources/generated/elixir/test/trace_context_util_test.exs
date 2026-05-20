defmodule PetstoreClient.TraceContextUtilTest do
  use ExUnit.Case, async: true

  describe "inject_trace_context/1" do
    test "is a no-op without tracer" do
      headers = %{}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result == %{}
    end

    test "empty headers do not cause exception" do
      headers = %{}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result == %{}
    end

    test "does not inject traceparent without OTel" do
      headers = %{}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      refute Map.has_key?(result, "traceparent")
    end

    test "does not inject tracestate without OTel" do
      headers = %{}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      refute Map.has_key?(result, "tracestate")
    end

    test "preserves Authorization header" do
      headers = %{"Authorization" => "Bearer token123"}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result["Authorization"] == "Bearer token123"
    end

    test "preserves Content-Type header" do
      headers = %{"Content-Type" => "application/json"}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result["Content-Type"] == "application/json"
    end

    test "preserves X-Request-ID header" do
      headers = %{"X-Request-ID" => "req-12345"}
      result = PetstoreClient.TraceContextUtil.inject_trace_context(headers)
      assert result["X-Request-ID"] == "req-12345"
    end

    test "preserves all existing headers" do
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
end
