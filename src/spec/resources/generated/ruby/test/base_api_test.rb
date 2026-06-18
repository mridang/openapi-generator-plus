# frozen_string_literal: true
# rubocop:disable all

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

  def send_request(_method, url, headers, body, no_redirect: false)
    @captured_url = url
    @captured_headers = headers
    @captured_body = body
    PetstoreClient::ApiHttpResponse.new(status_code: 200, body: '{}', headers: { 'content-type' => 'application/json' })
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
  parallelize_me!

  let(:chasm_url) { ENV.fetch('CHASM_HTTP_URL') }

  let(:api) do
    config = PetstoreClient::Configuration.builder.base_url(chasm_url).build
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
        api.call('GET', "/test/status/#{status}", {}, {}, nil,
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
      api.call('GET', '/test/status/400', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    _(err.error_body).wont_be_nil
  end

  # ── Exception hierarchy ──

  it 'NotFoundError is a kind of ClientError and ApiError' do
    err = assert_raises(PetstoreClient::Errors::NotFoundError) do
      api.call('GET', '/test/status/404', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    assert_kind_of PetstoreClient::Errors::ClientError, err
    assert_kind_of PetstoreClient::ApiError, err
  end

  it 'InternalServerError is a kind of ServerError and ApiError' do
    err = assert_raises(PetstoreClient::Errors::InternalServerError) do
      api.call('GET', '/test/status/500', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    assert_kind_of PetstoreClient::Errors::ServerError, err
    assert_kind_of PetstoreClient::ApiError, err
  end

  # ── Success deserialization ──

  it 'deserializes JSON response' do
    result = api.call('GET', '/test/echo', {}, {}, nil,
                      ['application/json'], 'application/json', 'Object')
    _(result).wont_be_nil
    _(result[:method]).must_equal 'GET'
  end

  it 'returns raw string for non-JSON response' do
    result = api.call('GET', '/test/text-plain', {}, {}, nil,
                      ['text/plain'], 'application/json', 'String')
    _(result).wont_be_nil
    _(result).must_include 'hello world'
  end

  it 'returns nil when return_type is nil' do
    result = api.call('GET', '/test/echo', {}, {}, nil,
                      ['application/json'], 'application/json', nil)
    assert_nil result
  end

  # ── Query parameters ──

  it 'appends query params to URL' do
    result = api.call('GET', '/test/echo', { 'foo' => 'bar' }, {}, nil,
                      ['application/json'], 'application/json', nil)
    assert_nil result
  end

  it 'includes empty value param in query string when value is empty string' do
    result = api.call('GET', '/test/echo', { 'filter' => '' }, {}, nil,
                      ['application/json'], 'application/json', nil)
    assert_nil result
  end

  # ── Auth injection ──

  it 'forwards auth headers' do
    auth = TestAuthenticator.new(headers: { 'X-Custom' => 'auth-value' })
    result = api.call('GET', '/test/echo', {}, {}, nil,
                      ['application/json'], 'application/json', 'Object', auth)
    _(result).wont_be_nil
    _(result[:headers][:'x-custom']).must_equal 'auth-value'
  end

  it 'sets Cookie header from auth cookies' do
    auth = TestAuthenticator.new(cookies: { 'session' => 'abc123' })
    api.call('GET', '/test/echo', {}, {}, nil,
             ['application/json'], 'application/json', nil, auth)
  end

  it 'falls back to client-level authenticator when no per-call auth is supplied' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    client_auth = TestAuthenticator.new(headers: { 'X-Client-Auth' => 'client-level-token' })
    test_api = TestableApi.new(client, config, client_auth)
    test_api.call('GET', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_headers['X-Client-Auth']).must_equal 'client-level-token'
  end

  it 'per-call auth overrides the client-level authenticator' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    client_auth = TestAuthenticator.new(headers: { 'X-Client-Auth' => 'client-level-token' })
    per_call_auth = TestAuthenticator.new(headers: { 'X-Client-Auth' => 'per-call-token' })
    test_api = TestableApi.new(client, config, client_auth)
    test_api.call('GET', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil, per_call_auth)
    _(client.captured_headers['X-Client-Auth']).must_equal 'per-call-token'
  end

  # ── Body serialization ──

  it 'serializes JSON body for POST' do
    result = api.call('POST', '/test/echo', {}, {}, { 'key' => 'value' },
                      ['application/json'], 'application/json', 'Object')
    _(result).wont_be_nil
    body = JSON.parse(result[:body], symbolize_names: true)
    _(body[:key]).must_equal 'value'
  end

  it 'sends no body when body is nil' do
    api.call('GET', '/test/echo', {}, {}, nil,
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

  it 'collapses double-slash when base_url has trailing slash' do
    # Gap Z — base_url='http://x/' + path='/y' must produce
    # 'http://x/y', not 'http://x//y' which most servers route to 404.
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost/').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).must_equal 'http://localhost/test'
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

  it 'form-urlencoded body encodes space as + not %20' do
    # form-urlencoded-space-plus-vs-pct20: application/x-www-form-urlencoded
    # mandates '+' for a space (WHATWG/HTML form-encoding), not '%20'.
    # URI.encode_www_form emits '+', matching the other SDKs.
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { 'full name' => 'Ada Lovelace' },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'full+name=Ada+Lovelace'
    _(client.captured_body.to_s).wont_include '%20'
  end

  # Canonical behavior 1 — a form-body array property serializes to
  # repeated keys (`tags=a&tags=b`), not a single comma-joined value or a
  # `tags[]` bracket form. URI.encode_www_form repeats the key for each
  # array element, matching the other SDKs.
  it 'form-urlencoded body expands an array property to repeated keys' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { 'tags' => %w[a b] },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'tags=a&tags=b'
    _(client.captured_body.to_s).wont_include 'tags=a,b'
    _(client.captured_body.to_s).wont_include 'tags%5B%5D'
  end

  # Canonical behavior 2 — an optional form property whose value is absent
  # is omitted from the encoded body entirely. The operation method only
  # inserts non-nil optional form params into request_body, so a hash that
  # never received the key produces no `note=` pair at all.
  it 'form-urlencoded body omits an absent optional property' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    # Mirrors what the operation builds when `note` is nil: the key is
    # simply never added to request_body.
    test_api.call('POST', '/test', {}, {}, { 'nickname' => 'Rex' },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'nickname=Rex'
    _(client.captured_body.to_s).wont_include 'note'
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
      PetstoreClient::ApiHttpResponse.new(status_code: 200, body: 'hello', headers: { 'Content-Type' => 'text/plain' })
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
      PetstoreClient::ApiHttpResponse.new(
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

  # ── Default header merge order (Gap C / R5-1) ──

  # R5-1: a caller's config.default_headers Accept must win over the
  # operation-negotiated Accept. select_headers runs first, then
  # config.default_headers is merged OVER it (matching java putAll, python,
  # elixir). RED before the merge-order fix, GREEN after.
  it 'config.default_headers Accept wins over operation-negotiated Accept' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder
      .base_url('http://localhost')
      .default_header('Accept', 'application/xml')
      .build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_headers['Accept']).must_equal 'application/xml'
  end

  it 'config.default_headers custom headers propagate to request' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder
      .base_url('http://localhost')
      .default_header('X-Custom', 'v')
      .build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_headers['X-Custom']).must_equal 'v'
  end

  it 'header_params Accept wins over both default and computed Accept' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder
      .base_url('http://localhost')
      .default_header('Accept', 'text/plain')
      .build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', {}, { 'Accept' => 'application/xml' }, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_headers['Accept']).must_equal 'application/xml'
  end

  # ── BinaryResponseTests ──

  it 'octet-stream binary response decodes to original bytes (roundtrip)' do
    require 'base64'
    original_bytes = "\x00\xFF\x42".b
    encoded = Base64.strict_encode64(original_bytes)
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiHttpResponse.new(status_code: 200, body: @_encoded, headers: { 'Content-Type' => 'application/octet-stream' })
    end
    client.instance_variable_set(:@_encoded, encoded)
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['application/octet-stream'], 'application/octet-stream', 'String')
    decoded = Base64.strict_decode64(result)
    assert_equal original_bytes, decoded.b, 'binary roundtrip must preserve bytes exactly'
  end

  it 'image/png binary response decodes to original bytes (roundtrip)' do
    require 'base64'
    original_bytes = "\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR".b
    encoded = Base64.strict_encode64(original_bytes)
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiHttpResponse.new(status_code: 200, body: @_encoded, headers: { 'Content-Type' => 'image/png' })
    end
    client.instance_variable_set(:@_encoded, encoded)
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['image/png'], 'image/png', 'String')
    decoded = Base64.strict_decode64(result)
    assert_equal original_bytes, decoded.b, 'binary roundtrip must preserve bytes exactly'
  end

  it 'returns nil for nil return type (JSON response)' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['application/json'], 'application/json', nil)
    assert_nil result
  end

  it 'returns string for text/plain response' do
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiHttpResponse.new(status_code: 200, body: 'hello world', headers: { 'Content-Type' => 'text/plain' })
    end
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['text/plain'], 'application/json', 'String')
    _(result).must_equal 'hello world'
  end

  it 'returns nil when response body is empty' do
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiHttpResponse.new(status_code: 200, body: '', headers: { 'Content-Type' => 'application/octet-stream' })
    end
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['application/octet-stream'], 'application/octet-stream', nil)
    assert_nil result
  end

  # ── apiresult-rawbody-nullability: raw_body is always a non-nil String ──

  it 'ApiResult#raw_body is a non-nil String even for an empty body' do
    client = CapturingApiClient.new
    def client.send_request(_method, _url, _headers, _body)
      PetstoreClient::ApiHttpResponse.new(status_code: 204, body: '', headers: { 'Content-Type' => 'application/json' })
    end
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call_for_result('GET', '/test/echo', {}, {}, nil,
                                      ['application/json'], 'application/json', nil)
    _(result.raw_body).must_be_kind_of String
    _(result.raw_body).must_equal ''
  end

  it 'with_http_info variant returns ApiResult with status, data, headers, and raw_body' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call_for_result('GET', '/test/echo', {}, {}, nil,
                                      ['application/json'], 'application/json', 'Object')
    _(result.status_code).must_equal 200
    _(result.data).wont_be_nil
    _(result.headers).wont_be_nil
    _(result.raw_body).must_equal '{}'
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
    test_api.call('POST', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    refute client.captured_headers.key?('Content-Type'),
           'Content-Type must NOT be sent when body is nil'
  end

  it 'empty string body includes Content-Type' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test/echo', {}, {}, '',
                  ['application/json'], 'application/json', nil)
    assert client.captured_headers.key?('Content-Type'),
           'Content-Type must be sent when body is empty string'
  end

  it 'empty JSON object body includes Content-Type' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test/echo', {}, {}, '{}',
                  ['application/json'], 'application/json', nil)
    assert client.captured_headers.key?('Content-Type'),
           'Content-Type must be sent when body is {}'
    _(client.captured_headers['Content-Type']).must_equal 'application/json'
  end

  # ── Empty array in query is omitted entirely (#20) ──

  it 'empty array query param is omitted entirely (no bare key)' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'tags' => [] }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).wont_include 'tags'
    _(client.captured_url).wont_include '?'
  end

  it 'mixed query with empty array omits the empty array but keeps other params' do
    client = CapturingApiClient.new
    config = PetstoreClient::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'tags' => [], 'limit' => 10 }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).wont_include 'tags'
    _(client.captured_url).must_include 'limit=10'
  end

  # ── Typed error body (#7) ──

  it 'ApiError#typed_error_body deserializes error body to typed model' do
    err = PetstoreClient::ApiError.new(
      status_code: 400,
      response_body: '{"id":42,"name":"BadCategory"}',
      response_headers: {}
    )
    body = err.typed_error_body('Category')
    _(body).must_be_kind_of PetstoreClient::Models::Category
    _(body.id).must_equal 42
    _(body.name).must_equal 'BadCategory'
  end

  it 'ApiError#typed_error_body returns nil for empty body' do
    err = PetstoreClient::ApiError.new(
      status_code: 500,
      response_body: '',
      response_headers: {}
    )
    assert_nil err.typed_error_body('Category')
  end
end
