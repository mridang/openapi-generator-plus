=begin
#Swagger Petstore - OpenAPI 3.0

#Unit tests for TraceContextUtil.

=end

require 'spec_helper'
require 'opigen_client/trace_context_util'

RSpec.describe OpigenClient::TraceContextUtil do
  describe '.inject_trace_context' do
    it 'injects traceparent when OpenTelemetry is available' do
      mock_propagation = double('propagation')
      expect(mock_propagation).to receive(:inject) do |headers|
        headers['traceparent'] = '00-abcdef1234567890abcdef1234567890-0123456789abcdef-01'
      end

      stub_otel = Module.new do
        define_method(:propagation) { mock_propagation }
        module_function :propagation
      end

      stub_const('OpenTelemetry', stub_otel)
      allow(described_class).to receive(:require).with('opentelemetry-api').and_return(true)

      headers = {}
      described_class.inject_trace_context(headers)
      expect(headers).to include('traceparent')
      expect(headers['traceparent']).to eq('00-abcdef1234567890abcdef1234567890-0123456789abcdef-01')
    end

    it 'does not inject traceparent when OpenTelemetry is not installed' do
      headers = {}
      described_class.inject_trace_context(headers)
      expect(headers).not_to include('traceparent')
    end

    it 'does not raise any exception' do
      headers = {}
      expect { described_class.inject_trace_context(headers) }.not_to raise_error
    end
  end
end
