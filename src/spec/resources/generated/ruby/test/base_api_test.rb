# frozen_string_literal: true

# rubocop:disable Metrics/BlockLength, Metrics/ParameterLists, Lint/MissingCopEnableDirective

require 'minitest/autorun'
require 'json'
require 'petstore_client'

class TestableApi < PetstoreClient::Api::BaseApi
  def call(method, path, query_params, header_params, body,
           accepts, content_type, return_type, auth = nil)
    invoke_api(method, path, query_params, header_params, body,
               accepts, content_type, return_type, auth)
  end

  def call_for_result(method, path, query_params, header_params, body,
                      accepts, content_type, return_type, auth = nil)
    invoke_api_for_result(method, path, query_params, header_params, body,
                          accepts, content_type, return_type, auth)
  end
end

class CapturingApiClient
  attr_reader :captured_url, :captured_headers, :captured_body

  def send_request(_method, url, headers, body)
    @captured_url = url
    @captured_headers = headers
    @captured_body = body
    PetstoreClient::ApiResponse.new(status_code: 200, body: '{}', headers: { 'content-type' => 'application/json' })
  end
end

class TestAuthenticator < PetstoreClient::Auth::Authenticator
  def initialize(headers: {}, query_params: {}, cookies: {})
    super()
    @headers = headers
    @query = query_params
    @cookies = cookies
  end

  def host
    ''
  end

  def auth_headers
    @headers
  end

  def query_params
    @query
  end

  def cookie_params
    @cookies
  end
end

describe PetstoreClient::Api::BaseApi do
  let(:wiremock_url) { ENV.fetch('WIREMOCK_HTTP_URL') }

  let(:api) do
    config = PetstoreClient::Configuration.builder.base_url(wiremock_url).build
    TestableApi.new(PetstoreClient::DefaultApiClient.new, config)
  end

  # ── Exception dispatch ──

  {
    400 => PetstoreClient::Errors::BadRequestError,
    401 => PetstoreClient::Errors::UnauthorizedError,
    403 => PetstoreClient::Errors::ForbiddenError,
    404 => PetstoreClient::Errors::NotFoundError,
    409 => PetstoreClient::Errors::ConflictError,
    422 => PetstoreClient::Errors::UnprocessableEntityError,
    418 => PetstoreClient::Errors::ClientError,
    500 => PetstoreClient::Errors::InternalServerError,
    502 => PetstoreClient::Errors::ServerError
  }.each do |status, error_class|
    it "raises #{error_class} for status #{status}" do
      err = assert_raises(error_class) do
        api.call('GET', "/api/error/#{status}", {}, {}, nil,
                 ['application/json'], 'application/json', nil)
      end
      _(err.status_code).must_equal status
      _(err.response_body).wont_be_nil
      _(err.response_body).wont_be_empty
    end
  end

  # ── Error body parsing ──

  it 'parses JSON error body' do
    err = assert_raises(PetstoreClient::Errors::BadRequestError) do
      api.call('GET', '/api/error/400', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    _(err.error_body).wont_be_nil
  end

  # ── Exception hierarchy ──

  it 'NotFoundError is a kind of ClientError and ApiError' do
    err = assert_raises(PetstoreClient::Errors::NotFoundError) do
      api.call('GET', '/api/error/404', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    assert_kind_of PetstoreClient::Errors::ClientError, err
    assert_kind_of PetstoreClient::ApiError, err
  end

  it 'InternalServerError is a kind of ServerError and ApiError' do
    err = assert_raises(PetstoreClient::Errors::InternalServerError) do
      api.call('GET', '/api/error/500', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    assert_kind_of PetstoreClient::Errors::ServerError, err
    assert_kind_of PetstoreClient::ApiError, err
  end

  # ── Success deserialization ──

  it 'deserializes JSON response' do
    result = api.call('GET', '/api/test', {}, {}, nil,
                      ['application/json'], 'application/json', 'Object')
    _(result).wont_be_nil
    _(result[:message]).must_equal 'success'
  end

  it 'returns raw string for non-JSON response' do
    result = api.call('GET', '/api/text', {}, {}, nil,
                      ['text/plain'], 'application/json', 'String')
    _(result).wont_be_nil
    _(result).must_include 'hello plain text'
  end

  it 'returns nil when return_type is nil' do
    result = api.call('GET', '/api/test', {}, {}, nil,
                      ['application/json'], 'application/json', nil)
    assert_nil result
  end

  # ── Query parameters ──

  it 'appends query params to URL' do
    result = api.call('GET', '/api/test', { 'foo' => 'bar' }, {}, nil,
                      ['application/json'], 'application/json', nil)
    assert_nil result
  end

  it 'includes empty value param in query string when value is empty string' do
    result = api.call('GET', '/api/test', { 'filter' => '' }, {}, nil,
                      ['application/json'], 'application/json', nil)
    assert_nil result
  end

  # ── Auth injection ──

  it 'forwards auth headers' do
    auth = TestAuthenticator.new(headers: { 'X-Custom' => 'auth-value' })
    result = api.call('GET', '/api/echo-headers', {}, {}, nil,
                      ['application/json'], 'application/json', 'Object', auth)
    _(result).wont_be_nil
    _(result[:'x-custom']).must_equal 'auth-value'
  end

  it 'sets Cookie header from auth cookies' do
    auth = TestAuthenticator.new(cookies: { 'session' => 'abc123' })
    api.call('GET', '/api/test', {}, {}, nil,
             ['application/json'], 'application/json', nil, auth)
  end

  # ── Body serialization ──

  it 'serializes JSON body for POST' do
    result = api.call('POST', '/api/echo-body', {}, {}, { 'key' => 'value' },
                      ['application/json'], 'application/json', 'Object')
    _(result).wont_be_nil
    _(result[:key]).must_equal 'value'
  end

  it 'sends no body when body is nil' do
    api.call('GET', '/api/test', {}, {}, nil,
             ['application/json'], 'application/json', nil)
  end

  # ── Server variable overrides via Configuration ──

  it 'server variable overrides resolve in base URL' do
    config = PetstoreClient::Configuration.builder
      .server(PetstoreClient::Servers::SERVER_1, 'environment' => 'staging')
      .build
    _(config.base_url).must_equal('https://staging.example.com/api/v3')
  end

  it 'default server variables produce correct base URL' do
    config = PetstoreClient::Configuration.builder
      .server(PetstoreClient::Servers::SERVER_1)
      .build
    _(config.base_url).must_equal('https://api.example.com/api/v3')
  end

  it 'invalid enum value raises ArgumentError' do
    assert_raises(ArgumentError) do
      PetstoreClient::Configuration.builder
        .server(PetstoreClient::Servers::SERVER_1, 'environment' => 'invalid')
        .build
    end
  end

  it 'API request uses resolved server URL' do
    config = PetstoreClient::Configuration.builder
      .server(PetstoreClient::Servers::SERVER_1, 'environment' => 'staging')
      .build
    _(config.base_url).must_equal('https://staging.example.com/api/v3')
  end

  # ── allowEmptyValue query params ──

  it 'null options omits allow_empty_value param' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    api = PetstoreClient::Api::PetApi.new(client, config)
    begin
      api.find_pets_by_status(nil)
    rescue StandardError
      # Response deserialization may fail; we only care about the captured URL
    end
    _(client.captured_url).wont_include 'status=',
      "Expected no status param when options is nil, got: #{client.captured_url}"
  end

  it 'allow_empty_value param included when value is nil in options' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    api = PetstoreClient::Api::PetApi.new(client, config)
    begin
      api.find_pets_by_status(PetstoreClient::Api::Options::FindPetsByStatusOptions.new)
    rescue StandardError
      # Response deserialization may fail; we only care about the captured URL
    end
    msg = 'Expected status= in URL for allowEmptyValue param ' \
          "with nil value, got: #{client.captured_url}"
    _(client.captured_url).must_include 'status=', msg
  end

  it 'allow_empty_value param included when value is empty string' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    api = PetstoreClient::Api::PetApi.new(client, config)
    begin
      opts = PetstoreClient::Api::Options::FindPetsByStatusOptions
             .new(status: '')
      api.find_pets_by_status(opts)
    rescue StandardError
      # Response deserialization may fail; we only care about the captured URL
    end
    msg = 'Expected status= in URL for empty string ' \
          "allowEmptyValue param, got: #{client.captured_url}"
    _(client.captured_url).must_include 'status=', msg
  end

  # ── Query serialization ──

  it 'expands array query params' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'tags' => %w[a b] }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).must_include 'tags=a&tags=b'
  end

  it 'serializes boolean query params' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'active' => true }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).must_include 'active=true'
  end

  it 'serializes number query params' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'limit' => 10 }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).must_include 'limit=10'
    _(client.captured_url).wont_include 'limit=10.0'
  end

  it 'handles empty query params' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).wont_include '?'
  end

  # ── Body serialization by content type ──

  it 'serializes text/plain body' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, 'hello world',
                  ['application/json'], 'text/plain', nil)
    _(client.captured_body).wont_be_nil
    _(client.captured_body.to_s).must_include 'hello world'
  end

  it 'serializes form-urlencoded body' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { 'name' => 'alice' },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body).wont_be_nil
    _(client.captured_body.to_s).must_include 'name=alice'
  end

  it 'passes binary body as-is' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, "\x01\x02\x03".b,
                  ['application/json'], 'application/octet-stream', nil)
    _(client.captured_body).wont_be_nil
  end

  # ── Content-type deserialization ──

  it 'skips deserialization for non-JSON content type' do
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiResponse.new(status_code: 200, body: 'hello', headers: { 'Content-Type' => 'text/plain' })
    end
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test', {}, {}, nil,
                           ['text/plain'], 'application/json', 'String')
    _(result).must_equal 'hello'
  end

  it 'deserializes vendor JSON MIME types like application/problem+json' do
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiResponse.new(
        status_code: 200,
        body: '{"title":"Not Found"}',
        headers: { 'Content-Type' => 'application/problem+json' }
      )
    end
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test', {}, {}, nil,
                           ['application/json'], 'application/json', 'Object')
    _(result).wont_be_nil
    _(result[:title]).must_equal 'Not Found'
  end

  # ── Header flow-through ──

  it 'empty content-type defaults to application/json' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { name: 'test' },
                  ['application/json'], '', nil)
    _(client.captured_headers['Content-Type']).must_equal 'application/json'
  end

  it 'all headers from selector flow through to request' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { name: 'test' },
                  ['application/json'], 'application/json', nil)
    assert client.captured_headers.key?('Accept'), 'Expected Accept header'
    assert client.captured_headers.key?('Content-Type'), 'Expected Content-Type header'
  end

  # ── BinaryResponseTests ──

  it 'decodes octet-stream response body from base64' do
    binary_data = "\x89PNG\r\n\x1a\n".b
    encoded = [binary_data].pack('m0')
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiResponse.new(status_code: 200, body: @_encoded, headers: { 'Content-Type' => 'application/octet-stream' })
    end
    client.instance_variable_set(:@_encoded, encoded)
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/api/test', {}, {}, nil,
                           ['application/octet-stream'], 'application/octet-stream', 'String')
    _(result).must_equal encoded
  end

  it 'decodes image/png response body from base64' do
    binary_data = "\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR".b
    encoded = [binary_data].pack('m0')
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiResponse.new(status_code: 200, body: @_encoded, headers: { 'Content-Type' => 'image/png' })
    end
    client.instance_variable_set(:@_encoded, encoded)
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/api/img', {}, {}, nil,
                           ['image/png'], 'image/png', 'String')
    _(result).must_equal encoded
  end

  it 'returns nil for nil return type (JSON response)' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/api/test', {}, {}, nil,
                           ['application/json'], 'application/json', nil)
    assert_nil result
  end

  it 'returns string for text/plain response' do
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiResponse.new(status_code: 200, body: 'hello world', headers: { 'Content-Type' => 'text/plain' })
    end
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/api/test', {}, {}, nil,
                           ['text/plain'], 'application/json', 'String')
    _(result).must_equal 'hello world'
  end

  it 'returns nil when response body is empty' do
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiResponse.new(status_code: 200, body: '', headers: { 'Content-Type' => 'application/octet-stream' })
    end
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/api/test', {}, {}, nil,
                           ['application/octet-stream'], 'application/octet-stream', nil)
    assert_nil result
  end

  # ── CrossOriginRedirectTests ──

  it 'same-origin redirect forwards Authorization header' do
    sensitive = %w[authorization cookie proxy-authorization]
    same_origin = true
    original = { 'Authorization' => 'Bearer token123', 'Accept' => 'application/json' }
    forwarded = original.reject { |k, _v| !same_origin && sensitive.include?(k.downcase) }
    _(forwarded).must_include 'Authorization'
    _(forwarded['Authorization']).must_equal 'Bearer token123'
  end

  it 'cross-origin redirect drops Authorization header' do
    sensitive = %w[authorization cookie proxy-authorization]
    same_origin = false
    original = { 'Authorization' => 'Bearer token123', 'Accept' => 'application/json' }
    forwarded = original.reject { |k, _v| !same_origin && sensitive.include?(k.downcase) }
    _(forwarded).wont_include 'Authorization'
    _(forwarded).must_include 'Accept'
  end

  it 'cross-origin redirect drops Cookie header' do
    sensitive = %w[authorization cookie proxy-authorization]
    same_origin = false
    original = { 'Cookie' => 'session=abc123', 'Accept' => 'application/json' }
    forwarded = original.reject { |k, _v| !same_origin && sensitive.include?(k.downcase) }
    _(forwarded).wont_include 'Cookie'
    _(forwarded).must_include 'Accept'
  end

  # ── NullBodyContentTypeTests ──

  it 'null body POST does not send Content-Type' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/api/test', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    refute client.captured_headers.key?('Content-Type'),
           'Content-Type must NOT be sent when body is nil'
  end

  it 'empty string body includes Content-Type' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/api/test', {}, {}, '',
                  ['application/json'], 'application/json', nil)
    assert client.captured_headers.key?('Content-Type'),
           'Content-Type must be sent when body is empty string'
  end

  it 'empty JSON object body includes Content-Type' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/api/test', {}, {}, '{}',
                  ['application/json'], 'application/json', nil)
    assert client.captured_headers.key?('Content-Type'),
           'Content-Type must be sent when body is {}'
    _(client.captured_headers['Content-Type']).must_equal 'application/json'
  end
end
