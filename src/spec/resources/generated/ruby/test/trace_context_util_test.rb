# frozen_string_literal: true

require 'test_helper'
require 'petstore_client/trace_context_util'

describe PetstoreClient::TraceContextUtil do
  describe '.inject_trace_context' do
    it 'injects traceparent when OpenTelemetry is available' do
      mock_propagation = Object.new
      mock_propagation.define_singleton_method(:inject) do |headers|
        headers['traceparent'] = '00-abcdef1234567890abcdef1234567890-0123456789abcdef-01'
      end

      stub_otel = Module.new do
        define_method(:propagation) { mock_propagation }
        module_function :propagation
      end

      Object.const_set(:OpenTelemetry, stub_otel)
      begin
        PetstoreClient::TraceContextUtil.stub(:require, true) do
          headers = {}
          PetstoreClient::TraceContextUtil.inject_trace_context(headers)
          _(headers).must_include('traceparent')
          _(headers['traceparent']).must_equal('00-abcdef1234567890abcdef1234567890-0123456789abcdef-01')
        end
      ensure
        Object.send(:remove_const, :OpenTelemetry)
      end
    end

    it 'does not inject traceparent when OpenTelemetry is not installed' do
      headers = {}
      PetstoreClient::TraceContextUtil.inject_trace_context(headers)
      _(headers).wont_include('traceparent')
    end

    it 'does not raise any exception' do
      headers = {}
      PetstoreClient::TraceContextUtil.inject_trace_context(headers)
    end
  end
end
