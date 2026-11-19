# frozen_string_literal: true

require 'test_helper'
require 'logger'
require 'opentelemetry/sdk'
require 'opentelemetry-metrics-sdk'

# The recipe the README shows, included from the same partial, so the text a
# user copies is the text under test.
require 'opentelemetry-api'
require 'opentelemetry-metrics-api'

# A call hook that records one span, one request count and one duration per
# call. It needs the opentelemetry-api and opentelemetry-metrics-api gems, and
# does nothing until you configure an OpenTelemetry SDK or pass providers.
class TelemetryCallHook
  # Ends the span and records the count and the duration of one call.
  class Observer
    def initialize(span, attributes, count, duration, started)
      @span = span
      @attributes = attributes
      @count = count
      @duration = duration
      @started = started
    end

    def finish(outcome)
      attributes = @attributes.dup
      attributes['http.response.status_code'] = outcome.status_code if outcome.status_code.positive?
      failure = error_type(outcome)
      attributes['error.type'] = failure if failure
      @span.add_attributes(attributes.slice('http.response.status_code', 'error.type'))
      @span.status = OpenTelemetry::Trace::Status.error(failure) if failure
      @span.finish
      @count.add(1, attributes: attributes)
      @duration.record(Process.clock_gettime(Process::CLOCK_MONOTONIC) - @started, attributes: attributes)
    end

    private

    def error_type(outcome)
      return outcome.status_code.to_s if outcome.status_code >= 400

      case outcome.error
      when nil then nil
      when ::Petstore::Client::Errors::NetworkTimeoutError then 'timeout'
      when ::Petstore::Client::Errors::NetworkError then 'network_error'
      when ::Petstore::Client::Errors::SerializationError then 'serialization_error'
      when ::SignalException then 'cancelled'
      else '_OTHER'
      end
    end
  end

  def initialize(tracer_provider: OpenTelemetry.tracer_provider, meter_provider: OpenTelemetry.meter_provider)
    @tracer = tracer_provider.tracer('petstore_client', ::Petstore::Client::VERSION)
    meter = meter_provider.meter('petstore_client', version: ::Petstore::Client::VERSION)
    @count = meter.create_counter('http.client.request.count', unit: '{request}')
    @duration = meter.create_histogram('http.client.request.duration', unit: 's')
  end

  def start(info, headers)
    attributes = {
      'http.request.method' => info.http_method,
      'url.template' => info.url_template,
      'server.address' => info.server_address
    }
    span = @tracer.start_span(info.operation_id, kind: :client, attributes: attributes)
    OpenTelemetry.propagation.inject(headers, context: OpenTelemetry::Trace.context_with_span(span))
    Observer.new(span, attributes, @count, @duration, Process.clock_gettime(Process::CLOCK_MONOTONIC))
  end
end

OpenTelemetry.logger = Logger.new(File::NULL)
OpenTelemetry.propagation = OpenTelemetry::Trace::Propagation::TraceContext.text_map_propagator

# The API object the tests drive: it only exposes a call to the base class.
class RecipeApi < Petstore::Client::Api::BaseApi
  def call(client_path, query: {}, headers: {}, return_type: nil)
    invoke_api_for_result(:get, client_path, query, headers, nil, ['application/json'], 'application/json',
                          return_type, spec_operation_id: 'getItem-v2', url_template: '/items/{id}')
  end
end

# Answers each request from the block and keeps the headers it was sent.
class RecipeClient
  attr_reader :sent

  def initialize(&answer)
    @answer = answer
    @sent = [] # : Array[Hash[String, String]]
  end

  def send_request(_method, _url, headers, _body, **_kwargs)
    @sent << headers.dup
    @answer.call
  end
end

# A tracer provider whose spans count how often they are ended.
class RecipeCountingTracerProvider
  attr_reader :ends

  def initialize(provider)
    @provider = provider
    @ends = [] # : Array[Integer]
  end

  def tracer(name, version)
    tracer = @provider.tracer(name, version)
    ends = @ends
    wrapper = Object.new
    wrapper.define_singleton_method(:start_span) do |*args, **kwargs|
      span = tracer.start_span(*args, **kwargs)
      count = [0]
      ends << count
      span.define_singleton_method(:finish) do |*finish_args, **finish_kwargs|
        count[0] += 1
        super(*finish_args, **finish_kwargs)
      end
      span
    end
    wrapper
  end
end

# In-memory providers for traces and metrics, and the hook built on them.
class RecipeTelemetry
  attr_reader :hook, :span_exporter, :metric_exporter, :tracer_provider

  def initialize(tracer_provider: nil)
    @span_exporter = OpenTelemetry::SDK::Trace::Export::InMemorySpanExporter.new
    @tracer_provider = OpenTelemetry::SDK::Trace::TracerProvider.new
    @tracer_provider.add_span_processor(OpenTelemetry::SDK::Trace::Export::SimpleSpanProcessor.new(@span_exporter))
    @metric_exporter = OpenTelemetry::SDK::Metrics::Export::InMemoryMetricPullExporter.new
    @meter_provider = OpenTelemetry::SDK::Metrics::MeterProvider.new
    @meter_provider.add_metric_reader(@metric_exporter)
    @hook = TelemetryCallHook.new(tracer_provider: tracer_provider || @tracer_provider, meter_provider: @meter_provider)
  end

  def spans
    @span_exporter.finished_spans
  end

  def metrics
    @metric_exporter.pull
    @metric_exporter.metric_snapshots
  end

  def metric(name)
    metrics.find { |snapshot| snapshot.name == name }
  end
end

describe TelemetryCallHook do
  parallelize_me!

  def api_for(client, hook)
    config = Petstore::Client::Configuration.builder.base_url('https://api.example.com').call_hook(hook).build
    RecipeApi.new(client, config)
  end

  def ok_client
    RecipeClient.new do
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: '{}', headers: { 'content-type' => 'application/json' })
    end
  end

  def status_client(status)
    RecipeClient.new do
      Petstore::Client::ApiHttpResponse.new(status_code: status, body: '{}', headers: { 'content-type' => 'application/json' })
    end
  end

  def failing_client(error)
    RecipeClient.new { raise error }
  end

  it 'bhv_telemetry_001 names the span with the spec operation id and gives it kind client' do
    telemetry = RecipeTelemetry.new

    api_for(ok_client, telemetry.hook).call('/items/7')

    span = telemetry.spans.first
    _(span.name).must_equal 'getItem-v2'
    _(span.kind).must_equal :client
  end

  it 'bhv_telemetry_002 sets the request attributes and the status on a 2xx and leaves the span status unset' do
    telemetry = RecipeTelemetry.new

    api_for(ok_client, telemetry.hook).call('/items/7')

    span = telemetry.spans.first
    _(span.attributes).must_equal(
      'http.request.method' => 'GET',
      'url.template' => '/items/{id}',
      'server.address' => 'api.example.com',
      'http.response.status_code' => 200
    )
    _(span.status.code).must_equal OpenTelemetry::Trace::Status::UNSET
  end

  it 'bhv_telemetry_003 sets error.type to the status as text and marks the span as an error' do
    [404, 503].each_case do |status|
      telemetry = RecipeTelemetry.new

      _ { api_for(status_client(status), telemetry.hook).call('/items/7') }.must_raise Petstore::Client::Errors::ApiError

      span = telemetry.spans.first
      _(span.attributes['error.type']).must_equal status.to_s
      _(span.attributes['http.response.status_code']).must_equal status
      _(span.status.code).must_equal OpenTelemetry::Trace::Status::ERROR
    end
  end

  it 'bhv_telemetry_004 sets error.type to the failure token' do
    {
      'timeout' => -> { raise Petstore::Client::Errors::NetworkTimeoutError.new(message: 'timed out') },
      'network_error' => -> { raise Petstore::Client::Errors::NetworkError.new(message: 'refused') },
      'cancelled' => -> { raise Interrupt },
      '_OTHER' => -> { raise 'something else' },
      'serialization_error' => -> { Petstore::Client::ApiHttpResponse.new(status_code: 200, body: 'not json', headers: { 'content-type' => 'text/plain' }) }
    }.each_case do |token, failure|
      telemetry = RecipeTelemetry.new

      assert_raises(StandardError, Interrupt) { api_for(RecipeClient.new(&failure), telemetry.hook).call('/items/7', return_type: 'Object') }

      _(telemetry.spans.first.attributes['error.type']).must_equal token
      _(telemetry.spans.first.status.code).must_equal OpenTelemetry::Trace::Status::ERROR
    end
  end

  it 'bhv_telemetry_005 sets no status code when no response arrived' do
    telemetry = RecipeTelemetry.new

    _ { api_for(failing_client(Petstore::Client::Errors::NetworkError.new(message: 'refused')), telemetry.hook).call('/items/7') }
      .must_raise Petstore::Client::Errors::NetworkError

    _(telemetry.spans.first.attributes).wont_include 'http.response.status_code'
  end

  it 'bhv_telemetry_006 makes the span a child of the active span and sends its id in traceparent' do
    telemetry = RecipeTelemetry.new
    client = ok_client

    telemetry.tracer_provider.tracer('caller').in_span('caller') do
      api_for(client, telemetry.hook).call('/items/7')
    end

    parent = telemetry.spans.find { |span| span.name == 'caller' }
    child = telemetry.spans.find { |span| span.name == 'getItem-v2' }
    _(child.parent_span_id).must_equal parent.span_id
    _(child.trace_id).must_equal parent.trace_id
    sent = client.sent.first
    _(sent.keys.count { |name| name.casecmp?('traceparent') }).must_equal 1
    _(sent['traceparent']).must_equal "00-#{child.hex_trace_id}-#{child.hex_span_id}-01"
  end

  it 'bhv_telemetry_007 records one span and one count for a call the transport retries' do
    telemetry = RecipeTelemetry.new
    answers = [[503, { 'Retry-After' => '0' }, 'busy'], [200, { 'content-type' => 'application/json' }, '{}']]
    seen = [] # : Array[Integer]
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      5.times do
        stub.get('/items/7') do
          seen << 1
          answers[[seen.length, answers.length].min - 1]
        end
      end
    end
    connection = Faraday.new('https://api.example.com') { |f| f.adapter :test, stubs }
    client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.connection(connection).build)
    client.define_singleton_method(:pause) { |_seconds| nil }

    api_for(client, telemetry.hook).call('/items/7')

    _(seen.length).must_equal 2
    _(telemetry.spans.length).must_equal 1
    _(telemetry.metric('http.client.request.count').data_points.sum(&:value)).must_equal 1
  end

  it 'bhv_telemetry_008 increments the request count once per call with the attributes of the span' do
    telemetry = RecipeTelemetry.new

    api_for(ok_client, telemetry.hook).call('/items/7')

    count = telemetry.metric('http.client.request.count')
    _(count.unit).must_equal '{request}'
    _(count.data_points.length).must_equal 1
    _(count.data_points.first.value).must_equal 1
    _(count.data_points.first.attributes).must_equal telemetry.spans.first.attributes
  end

  it 'bhv_telemetry_009 records one duration in seconds per call with the attributes of the span' do
    telemetry = RecipeTelemetry.new
    client = RecipeClient.new do
      waited = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 0.02
      Process.clock_gettime(Process::CLOCK_MONOTONIC) until Process.clock_gettime(Process::CLOCK_MONOTONIC) >= waited
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: '{}', headers: { 'content-type' => 'application/json' })
    end

    api_for(client, telemetry.hook).call('/items/7')

    duration = telemetry.metric('http.client.request.duration')
    _(duration.unit).must_equal 's'
    _(duration.data_points.length).must_equal 1
    point = duration.data_points.first
    _(point.count).must_equal 1
    _(point.sum).must_be :>=, 0.02
    _(point.sum).must_be :<, 10
    _(point.attributes).must_equal telemetry.spans.first.attributes
  end

  it 'bhv_telemetry_010 names the instrumentation scope after the gem and gives it the SDK version' do
    telemetry = RecipeTelemetry.new

    api_for(ok_client, telemetry.hook).call('/items/7')

    scope = telemetry.spans.first.instrumentation_scope
    _(scope.name).must_equal 'petstore_client'
    _(scope.version).must_equal Petstore::Client::VERSION
    meter_scope = telemetry.metric('http.client.request.count').instrumentation_scope
    _(meter_scope.name).must_equal 'petstore_client'
    _(meter_scope.version).must_equal Petstore::Client::VERSION
  end

  it 'bhv_telemetry_011 uses the providers it was given' do
    telemetry = RecipeTelemetry.new
    other = RecipeTelemetry.new

    api_for(ok_client, telemetry.hook).call('/items/7')

    _(telemetry.spans.length).must_equal 1
    _(other.spans).must_be_empty
    _(other.metrics).must_be_empty
  end

  it 'bhv_telemetry_012 does nothing and leaves the call unchanged when no provider is configured' do
    plain = ok_client
    hooked = ok_client

    api_for(plain, nil).call('/items/7')
    result = api_for(hooked, TelemetryCallHook.new).call('/items/7')

    _(result.status_code).must_equal 200
    _(hooked.sent).must_equal plain.sent
  end

  it 'bhv_telemetry_013 ends the span exactly once, also when the call throws' do
    telemetry = RecipeTelemetry.new
    counting = RecipeCountingTracerProvider.new(telemetry.tracer_provider)
    hook = TelemetryCallHook.new(tracer_provider: counting, meter_provider: OpenTelemetry.meter_provider)

    api_for(ok_client, hook).call('/items/7')
    _ { api_for(status_client(500), hook).call('/items/7') }.must_raise Petstore::Client::Errors::ApiError

    _(counting.ends.map(&:first)).must_equal [1, 1]
    _(telemetry.spans.length).must_equal 2
  end

  it 'bhv_telemetry_014 puts no query string, header value or credential in any attribute' do
    telemetry = RecipeTelemetry.new
    config = Petstore::Client::Configuration.builder.base_url('https://api.example.com').call_hook(telemetry.hook).build
    auth = Petstore::Client::Auth::BearerAuthenticator.new('https://api.example.com', 'secret-credential')
    client = RecipeClient.new do
      Petstore::Client::ApiHttpResponse.new(
        status_code: 200, body: '{}', headers: { 'content-type' => 'application/json', 'x-private' => 'secret-header' }
      )
    end

    RecipeApi.new(client, config, auth).call('/items/7', query: { 'token' => 'secret-query' }, headers: { 'X-Mine' => 'secret-header' })

    attribute_sets = telemetry.spans.map(&:attributes) +
                     telemetry.metrics.flat_map { |snapshot| snapshot.data_points.map(&:attributes) }
    values = attribute_sets.flat_map(&:values).map(&:to_s)
    keys = attribute_sets.flat_map(&:keys)
    _(values.join(' ')).wont_match(/secret|\?|token/)
    _(keys).wont_include 'url.full'
  end
end
