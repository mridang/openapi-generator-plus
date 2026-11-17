# frozen_string_literal: true

require 'minitest/autorun'
require 'json'
require 'petstore_client'

class TestableApi < Petstore::Client::Api::BaseApi
  def call(method, path, query_params, header_params, body,
           accepts, content_type, return_type, auth = nil)
    invoke_api(method, path, query_params, header_params, body,
               accepts, content_type, return_type, auth)
  end

  def call_for_result(method, path, query_params, header_params, body,
                      accepts, content_type, return_type, auth = nil, operation_id: nil, request_options: nil)
    invoke_api_for_result(method, path, query_params, header_params, body,
                          accepts, content_type, return_type, auth, operation_id: operation_id,
                                                                    request_options: request_options)
  end
end

CallOptions = Struct.new(:timeout, :headers, :max_retries)

# A DefaultApiClient that records what BaseApi hands the transport instead of
# sending it.
class RecordingDefaultApiClient < Petstore::Client::DefaultApiClient
  attr_reader :captured_kwargs

  def send_request(_method, _url, _headers, _body, **kwargs)
    @captured_kwargs = kwargs
    Petstore::Client::ApiHttpResponse.new(status_code: 200, body: '{}', headers: { 'content-type' => 'application/json' })
  end
end

class RaisingApiClient
  def send_request(_method, _url, _headers, _body, **_kwargs)
    raise Petstore::Client::Errors::NetworkError.new(message: 'connection refused')
  end
end

class CapturingApiClient
  attr_reader :captured_url, :captured_headers, :captured_body

  def send_request(_method, url, headers, body, **_kwargs)
    @captured_url = url
    @captured_headers = headers
    @captured_body = body
    Petstore::Client::ApiHttpResponse.new(status_code: 200, body: '{}', headers: { 'content-type' => 'application/json' })
  end
end

class FixedResponseApiClient
  def initialize(response)
    @response = response
  end

  def send_request(_method, _url, _headers, _body, **_kwargs)
    @response
  end
end

class TestAuthenticator < Petstore::Client::Auth::Authenticator
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

class HttpAwareTestAuthenticator < TestAuthenticator
  include Petstore::Client::Auth::HttpAwareAuthenticator

  attr_reader :injected_client

  def api_client=(client)
    @injected_client = client
  end
end

# Serves a scripted list of statuses for API calls and issues numbered tokens
# at the token endpoint, recording what each received.
class ReplayApiClient
  attr_reader :api_requests, :token_requests

  def initialize(api_statuses, token_statuses: [])
    @api_statuses = api_statuses
    @token_statuses = token_statuses
    @api_requests = []
    @token_requests = []
  end

  def send_request(_method, url, headers, body, **_kwargs)
    return token_response(body) if url.end_with?('/token')

    @api_requests << { headers: headers.dup, body: body }
    status = @api_statuses[@api_requests.length - 1] || @api_statuses.last
    Petstore::Client::ApiHttpResponse.new(
      status_code: status, body: '{}', headers: { 'content-type' => 'application/json' }
    )
  end

  private

  def token_response(body)
    @token_requests << body
    status = @token_statuses[@token_requests.length - 1] || 200
    payload = if status == 200
                { 'access_token' => "tok#{@token_requests.length}", 'refresh_token' => 'ref', 'expires_in' => 3600 }
              else
                { 'error' => 'server_error' }
              end
    Petstore::Client::ApiHttpResponse.new(
      status_code: status, body: payload.to_json, headers: { 'content-type' => 'application/json' }
    )
  end
end

describe Petstore::Client::Api::BaseApi do
  parallelize_me!

  let(:chasm_url) { ENV.fetch('CHASM_HTTP_URL') }

  let(:api) do
    config = Petstore::Client::Configuration.builder.base_url(chasm_url).build
    single_attempt = Petstore::Client::TransportOptions.builder.max_retries(0).build
    TestableApi.new(Petstore::Client::DefaultApiClient.new(single_attempt), config)
  end

  # ── Exception dispatch ──

  {
    400 => Petstore::Client::Errors::BadRequestError,
    401 => Petstore::Client::Errors::UnauthorizedError,
    403 => Petstore::Client::Errors::ForbiddenError,
    404 => Petstore::Client::Errors::NotFoundError,
    409 => Petstore::Client::Errors::ConflictError,
    422 => Petstore::Client::Errors::UnprocessableEntityError,
    429 => Petstore::Client::Errors::TooManyRequestsError,
    418 => Petstore::Client::Errors::ClientError,
    500 => Petstore::Client::Errors::InternalServerError,
    502 => Petstore::Client::Errors::ServerError
  }.each do |status, error_class|
    it "raises #{error_class} for status #{status}" do
      err = assert_raises(error_class) do
        api.call('GET', "/test/status/#{status}", {}, {}, nil,
                 ['application/json'], 'application/json', nil)
      end
      _(err).must_be_instance_of error_class
      _(err.status_code).must_equal status
      _(err.response_body).wont_be_nil
      _(err.response_body).wont_be_empty
      _(err.request_method).must_equal 'GET'
      _(err.request_path).must_equal "/test/status/#{status}"
    end
  end

  # The typed status routes answer on the path of a real operation, so the
  # error type is chosen from the status alone, whatever the route.
  {
    '/test/status/400/pet/1' => Petstore::Client::Errors::BadRequestError,
    '/test/status/401/pet/1' => Petstore::Client::Errors::UnauthorizedError,
    '/test/status/403/pet/1' => Petstore::Client::Errors::ForbiddenError,
    '/test/status/404/pet/1' => Petstore::Client::Errors::NotFoundError,
    '/test/status/409/pet/1' => Petstore::Client::Errors::ConflictError,
    '/test/status/418/pet/1' => Petstore::Client::Errors::ClientError,
    '/test/status/422/pet/1' => Petstore::Client::Errors::UnprocessableEntityError,
    '/test/status/500/pet/1' => Petstore::Client::Errors::InternalServerError,
    '/test/status/502/pet/1' => Petstore::Client::Errors::ServerError,
    '/test/status/404/store/order/1' => Petstore::Client::Errors::NotFoundError,
    '/test/status/422/store/order/1' => Petstore::Client::Errors::UnprocessableEntityError,
    '/test/status/500/store/order/1' => Petstore::Client::Errors::InternalServerError,
    '/test/status/502/store/order/1' => Petstore::Client::Errors::ServerError
  }.each do |path, error_class|
    it "maps #{path} to #{error_class}" do
      err = assert_raises(error_class) do
        api.call('GET', path, {}, {}, nil, ['application/json'], 'application/json', nil)
      end
      _(err).must_be_instance_of error_class
      _(err.status_code).must_equal path[%r{/status/(\d+)}, 1].to_s.to_i
      _(err.error_body).must_be_kind_of Hash
    end
  end

  # ── Error body parsing ──

  it 'parses JSON error body' do
    err = assert_raises(Petstore::Client::Errors::BadRequestError) do
      api.call('GET', '/test/status/400', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    _(err.error_body).wont_be_nil
  end

  # ── Exception hierarchy ──

  it 'NotFoundError is a kind of ClientError, ApiError and the root error' do
    err = assert_raises(Petstore::Client::Errors::NotFoundError) do
      api.call('GET', '/test/status/404', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    assert_kind_of Petstore::Client::Errors::ClientError, err
    assert_kind_of Petstore::Client::Errors::ApiError, err
    assert_kind_of ::Petstore::Client::Errors::OpenAPIError, err
  end

  it 'InternalServerError is a kind of ServerError, ApiError and the root error' do
    err = assert_raises(Petstore::Client::Errors::InternalServerError) do
      api.call('GET', '/test/status/500', {}, {}, nil,
               ['application/json'], 'application/json', nil)
    end
    assert_kind_of Petstore::Client::Errors::ServerError, err
    assert_kind_of Petstore::Client::Errors::ApiError, err
    assert_kind_of ::Petstore::Client::Errors::OpenAPIError, err
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
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    auth = TestAuthenticator.new(cookies: { 'session' => 'abc123' })
    test_api.call('GET', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil, auth)
    _(client.captured_headers['Cookie']).must_equal 'session=abc123'
  end

  it 'appends auth cookies to a Cookie header the operation already set' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    auth = TestAuthenticator.new(cookies: { 'session' => 'abc123' })
    test_api.call('GET', '/test/echo', {}, { 'Cookie' => 'theme=dark' }, nil,
                  ['application/json'], 'application/json', nil, auth)
    _(client.captured_headers['Cookie']).must_equal 'theme=dark; session=abc123'
  end

  it 'falls back to client-level authenticator when no per-call auth is supplied' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    client_auth = TestAuthenticator.new(headers: { 'X-Client-Auth' => 'client-level-token' })
    test_api = TestableApi.new(client, config, client_auth)
    test_api.call('GET', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_headers['X-Client-Auth']).must_equal 'client-level-token'
  end

  it 'per-call auth overrides the client-level authenticator' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    client_auth = TestAuthenticator.new(headers: { 'X-Client-Auth' => 'client-level-token' })
    per_call_auth = TestAuthenticator.new(headers: { 'X-Client-Auth' => 'per-call-token' })
    test_api = TestableApi.new(client, config, client_auth)
    test_api.call('GET', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil, per_call_auth)
    _(client.captured_headers['X-Client-Auth']).must_equal 'per-call-token'
  end

  it 'hands the transport to a per-call authenticator that needs HTTP' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    per_call_auth = HttpAwareTestAuthenticator.new(headers: { 'X-Client-Auth' => 'per-call-token' })
    test_api = TestableApi.new(client, config, nil)
    test_api.call('GET', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil, per_call_auth)
    _(per_call_auth.injected_client).must_be_same_as client
  end

  # AUTH-APPLIED (Wave A1) — a configured authenticator must actually reach the
  # wire on a SECURED operation. This is the cross-SDK regression guard for the
  # Elixir bug where a configured authenticator was silently dropped: here we
  # drive a real BearerAuthenticator through the generated, secured delete_pet
  # operation (which forwards options.auth into invoke_api) and assert the
  # OUTBOUND request actually carried the Bearer credential.
  it 'applies a configured Bearer authenticator to a secured operation request' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    api = Petstore::Client::Api::PetApi.new(client, config)
    auth = Petstore::Client::Auth::BearerAuthenticator.new('http://localhost', 'secret-token')
    options = Petstore::Client::Api::Options::DeletePetOptions.new(auth: auth)
    begin
      api.delete_pet(1, options)
    rescue StandardError
      # Response handling is irrelevant; we only assert the captured request.
    end
    _(client.captured_headers['Authorization']).must_equal 'Bearer secret-token'
  end

  # SECURITY-NONE (Wave A2) — an operation declared `security: []` is explicitly
  # unauthenticated, so a configured CLIENT-LEVEL authenticator must be SUPPRESSED
  # for it: no credential may reach the wire. get_inventory is a security:[]
  # operation (no auth methods), so the generated method passes the NO_AUTH
  # sentinel into invoke_api, which must NOT fall back to the client
  # authenticator. We configure the client with an authenticator that injects a
  # header, a query param, AND a cookie, then assert NONE of them are present on
  # the captured outbound request.
  it 'suppresses the client authenticator on a security:[] operation' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    client_auth = TestAuthenticator.new(
      headers: { 'Authorization' => 'Bearer client-token', 'api-key' => 'client-key' },
      query_params: { 'api_key' => 'client-key' },
      cookies: { 'session' => 'client-session' }
    )
    api = Petstore::Client::Api::StoreApi.new(client, config, client_auth)
    begin
      api.get_inventory
    rescue StandardError
      # Response handling is irrelevant; we only assert the captured request.
    end
    refute client.captured_headers.key?('Authorization'),
           'security:[] op must not carry an Authorization header from the client authenticator'
    refute client.captured_headers.key?('api-key'),
           'security:[] op must not carry an api-key header from the client authenticator'
    refute client.captured_headers.key?('Cookie'),
           'security:[] op must not carry an auth cookie from the client authenticator'
    _(client.captured_url).wont_include 'api_key',
                                        "security:[] op must not carry an api-key query param, got: #{client.captured_url}"
  end

  # INHERITED GLOBAL SECURITY — get_pet_by_id declares no security of its own, so
  # it inherits the global requirement. This generator strips the inherited auth
  # methods off such an operation, so the generated method passes nil (not the
  # NO_AUTH sentinel) into invoke_api, which must fall back to the client-level
  # authenticator. A configured client credential MUST reach the wire — the
  # regression that silently dropped auth on every inherited-security operation.
  it 'applies the client authenticator on an inherited-security operation' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    client_auth = TestAuthenticator.new(
      headers: { 'Authorization' => 'Bearer client-token' }
    )
    api = Petstore::Client::Api::PetApi.new(client, config, client_auth)
    begin
      api.get_pet_by_id(1)
    rescue StandardError
      # Response handling is irrelevant; we only assert the captured request.
    end
    _(client.captured_headers['Authorization']).must_equal 'Bearer client-token'
  end

  # SECURITY-NONE companion — the same suppression must hold via the generic
  # invoke path when the operation passes the NO_AUTH sentinel explicitly, even
  # though a client-level authenticator is configured. Guards the three-state
  # resolution directly (sentinel beats client fallback).
  it 'NO_AUTH sentinel suppresses the client authenticator in invoke_api' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    client_auth = TestAuthenticator.new(headers: { 'Authorization' => 'Bearer client-token' })
    test_api = TestableApi.new(client, config, client_auth)
    test_api.call('GET', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil,
                  Petstore::Client::Api::BaseApi::NO_AUTH)
    refute client.captured_headers.key?('Authorization'),
           'NO_AUTH sentinel must suppress the client authenticator credential'
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
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_body).must_be_nil
    refute client.captured_headers.key?('Content-Type'), 'Content-Type must NOT be sent when body is nil'
    refute client.captured_headers.key?('Content-Length'), 'Content-Length must NOT be set by the SDK when body is nil'
  end

  # ── Server variable overrides via Configuration ──

  it 'server variable overrides resolve in base URL' do
    config = Petstore::Client::Configuration.builder
      .server(Petstore::Client::Servers::SERVER_1, 'environment' => 'staging')
      .build
    _(config.base_url).must_equal('https://staging.example.com/api/v3')
  end

  it 'default server variables produce correct base URL' do
    config = Petstore::Client::Configuration.builder
      .server(Petstore::Client::Servers::SERVER_1)
      .build
    _(config.base_url).must_equal('https://api.example.com/api/v3')
  end

  it 'default server variables produce correct base URL' do
    config = Petstore::Client::Configuration.builder
      .server(Petstore::Client::Servers::SERVER_1)
      .build
    _(config.base_url).must_equal('https://api.example.com/api/v3')
  end

  # The third declared server carries quotes and an ampersand in its values:
  # they reach the URL exactly as written.
  it 'resolves the default and an enum value of the server with special characters' do
    default_config = Petstore::Client::Configuration.builder.server(Petstore::Client::Servers::SERVER_2).build
    _(default_config.base_url).must_equal("https://o'neil.example.com/api")

    other_config = Petstore::Client::Configuration.builder
      .server(Petstore::Client::Servers::SERVER_2, 'tenant' => 'a&b')
      .build
    _(other_config.base_url).must_equal('https://a&b.example.com/api')
  end

  it 'invalid enum value raises ArgumentError' do
    assert_raises(ArgumentError) do
      Petstore::Client::Configuration.builder
        .server(Petstore::Client::Servers::SERVER_1, 'environment' => 'invalid')
        .build
    end
  end

  it 'API request uses resolved server URL' do
    config = Petstore::Client::Configuration.builder
      .server(Petstore::Client::Servers::SERVER_1, 'environment' => 'staging')
      .build
    _(config.base_url).must_equal('https://staging.example.com/api/v3')
  end

  # ── allowEmptyValue query params ──

  it 'null options omits allow_empty_value param' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    api = Petstore::Client::Api::PetApi.new(client, config)
    begin
      api.find_pets_by_status(nil)
    rescue StandardError
      # Response deserialization may fail; we only care about the captured URL
    end
    _(client.captured_url).wont_include 'status=',
                                        "Expected no status param when options is nil, got: #{client.captured_url}"
  end

  it 'allow_empty_value param omitted when value is nil in options' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    api = Petstore::Client::Api::PetApi.new(client, config)
    begin
      api.find_pets_by_status(Petstore::Client::Api::Options::FindPetsByStatusOptions.new)
    rescue StandardError
      # Response deserialization may fail; we only care about the captured URL
    end
    msg = 'Expected no status= in URL for allowEmptyValue param ' \
          "with nil value (key omitted, not empty), got: #{client.captured_url}"
    _(client.captured_url).wont_include 'status=', msg
  end

  it 'allow_empty_value param included when value is empty string' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    api = Petstore::Client::Api::PetApi.new(client, config)
    begin
      opts = Petstore::Client::Api::Options::FindPetsByStatusOptions
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
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'tags' => %w[a b] }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).must_include 'tags=a&tags=b'
  end

  it 'serializes boolean query params' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'active' => true }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).must_include 'active=true'
  end

  it 'serializes number query params' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'limit' => 10 }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).must_include 'limit=10'
    _(client.captured_url).wont_include 'limit=10.0'
  end

  it 'handles empty query params' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).wont_include '?'
  end

  it 'collapses double-slash when base_url has trailing slash' do
    # Gap Z — base_url='http://x/' + path='/y' must produce
    # 'http://x/y', not 'http://x//y' which most servers route to 404.
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost/').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).must_equal 'http://localhost/test'
  end

  # ── Body serialization by content type ──

  it 'serializes text/plain body' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, 'hello world',
                  ['application/json'], 'text/plain', nil)
    _(client.captured_body).wont_be_nil
    _(client.captured_body.to_s).must_include 'hello world'
  end

  it 'serializes form-urlencoded body' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
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
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { 'full name' => 'Ada Lovelace' },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'full+name=Ada+Lovelace'
    _(client.captured_body.to_s).wont_include '%20'
  end

  it 'form-urlencoded body keeps ~ literal and escapes * in keys and values' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { 'a~b*c' => "a~b*c!'()" },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'a~b%2Ac=a~b%2Ac%21%27%28%29'
  end

  # Canonical behavior 1 — a form-body array property serializes to
  # repeated keys (`tags=a&tags=b`), not a single comma-joined value or a
  # `tags[]` bracket form. URI.encode_www_form repeats the key for each
  # array element, matching the other SDKs.
  it 'form-urlencoded body expands an array property to repeated keys' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
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
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    # Mirrors what the operation builds when `note` is nil: the key is
    # simply never added to request_body.
    test_api.call('POST', '/test', {}, {}, { 'nickname' => 'Rex' },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'nickname=Rex'
    _(client.captured_body.to_s).wont_include 'note'
  end

  it 'form-urlencoded body skips nil elements inside an array property' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { 'tags' => ['a', nil, 'b'] },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'tags=a&tags=b'
  end

  it 'form-urlencoded body stringifies a date-time in the canonical wire form' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    when_at = Time.new(2024, 1, 15, 10, 30, 0, '+00:00')
    test_api.call('POST', '/test', {}, {}, { 'remindAt' => when_at },
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'remindAt=2024-01-15T10%3A30%3A00.000%2B00%3A00'
  end

  it 'form-urlencoded body stringifies a duration, a boolean and a number' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    form = { 'snooze' => ISO8601::Duration.new('PT1H'), 'urgent' => true, 'weight' => 1.5 }
    test_api.call('POST', '/test', {}, {}, form,
                  ['application/json'], 'application/x-www-form-urlencoded', nil)
    _(client.captured_body.to_s).must_equal 'snooze=3600s&urgent=true&weight=1.5'
  end

  it 'form-urlencoded body rejects a nested map value' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    err = assert_raises(Petstore::Client::Errors::SerializationError) do
      test_api.call('POST', '/test', {}, {}, { 'location' => { 'lat' => 1.5 } },
                    ['application/json'], 'application/x-www-form-urlencoded', nil)
    end
    _(err.message).must_equal 'form-urlencoded field location must be a scalar or array of scalars'
    _(client.captured_body).must_be_nil
  end

  it 'passes binary body as-is' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, "\x01\x02\x03".b,
                  ['application/json'], 'application/octet-stream', nil)
    _(client.captured_body).wont_be_nil
  end

  # ── Content-type deserialization ──

  it 'skips deserialization for non-JSON content type' do
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: 'hello', headers: { 'Content-Type' => 'text/plain' })
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test', {}, {}, nil,
                           ['text/plain'], 'application/json', 'String')
    _(result).must_equal 'hello'
  end

  it 'raises a SerializationError for a non-JSON body on a model return type' do
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: 'hello', headers: { 'Content-Type' => 'text/plain' })
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    err = _ { test_api.call('GET', '/test', {}, {}, nil, ['text/plain'], 'application/json', 'Object') }
          .must_raise(Petstore::Client::Errors::SerializationError)
    _(err.message).must_include "Unexpected response Content-Type 'text/plain'"
    _(err.message).must_include 'expected a JSON body'
  end

  it 'names the operation, not an HTTP verb, in the unexpected Content-Type error' do
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: 'hello', headers: { 'Content-Type' => 'text/plain' })
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    err = _ do
      test_api.call_for_result('GET', '/test', {}, {}, nil, ['text/plain'], 'application/json', 'Object',
                               operation_id: 'PetApi.get_pet_by_id')
    end.must_raise(Petstore::Client::Errors::SerializationError)
    _(err.message).must_include 'for PetApi.get_pet_by_id: expected a JSON body'
    _(err.message).wont_include 'get:'
  end

  it 'deserializes vendor JSON MIME types like application/problem+json' do
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(
        status_code: 200,
        body: '{"title":"Not Found"}',
        headers: { 'Content-Type' => 'application/problem+json' }
      )
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test', {}, {}, nil,
                           ['application/json'], 'application/json', 'Object')
    _(result).wont_be_nil
    _(result[:title]).must_equal 'Not Found'
  end

  # ── Header flow-through ──

  it 'empty content-type defaults to application/json' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test', {}, {}, { name: 'test' },
                  ['application/json'], '', nil)
    _(client.captured_headers['Content-Type']).must_equal 'application/json'
  end

  it 'all headers from selector flow through to request' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
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
    config = Petstore::Client::Configuration.builder
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
    config = Petstore::Client::Configuration.builder
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
    config = Petstore::Client::Configuration.builder
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
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: encoded, headers: { 'Content-Type' => 'application/octet-stream' })
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
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
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: encoded, headers: { 'Content-Type' => 'image/png' })
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['image/png'], 'image/png', 'String')
    decoded = Base64.strict_decode64(result)
    assert_equal original_bytes, decoded.b, 'binary roundtrip must preserve bytes exactly'
  end

  it 'returns nil for nil return type (JSON response)' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['application/json'], 'application/json', nil)
    assert_nil result
  end

  it 'returns string for text/plain response' do
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: 'hello world', headers: { 'Content-Type' => 'text/plain' })
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['text/plain'], 'application/json', 'String')
    _(result).must_equal 'hello world'
  end

  it 'returns nil when response body is empty' do
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(status_code: 200, body: '', headers: { 'Content-Type' => 'application/octet-stream' })
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call('GET', '/test/echo', {}, {}, nil,
                           ['application/octet-stream'], 'application/octet-stream', nil)
    assert_nil result
  end

  # ── apiresult-rawbody-nullability: raw_body is always a non-nil String ──

  it 'ApiResult#raw_body is a non-nil String even for an empty body' do
    client = FixedResponseApiClient.new(
      Petstore::Client::ApiHttpResponse.new(status_code: 204, body: '', headers: { 'Content-Type' => 'application/json' })
    )
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call_for_result('GET', '/test/echo', {}, {}, nil,
                                      ['application/json'], 'application/json', nil)
    _(result.raw_body).must_be_kind_of String
    _(result.raw_body).must_equal ''
  end

  it 'with_http_info variant returns ApiResult with status, data, headers, and raw_body' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    result = test_api.call_for_result('GET', '/test/echo', {}, {}, nil,
                                      ['application/json'], 'application/json', 'Object')
    _(result.status_code).must_equal 200
    _(result.data).wont_be_nil
    _(result.headers).wont_be_nil
    _(result.raw_body).must_equal '{}'
  end

  # ── NullBodyContentTypeTests ──

  it 'null body POST does not send Content-Type' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test/echo', {}, {}, nil,
                  ['application/json'], 'application/json', nil)
    refute client.captured_headers.key?('Content-Type'),
           'Content-Type must NOT be sent when body is nil'
    _(client.captured_body).must_be_nil
  end

  it 'empty string body includes Content-Type' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('POST', '/test/echo', {}, {}, '',
                  ['application/json'], 'application/json', nil)
    assert client.captured_headers.key?('Content-Type'),
           'Content-Type must be sent when body is empty string'
  end

  it 'empty JSON object body includes Content-Type' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
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
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'tags' => [] }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).wont_include 'tags'
    _(client.captured_url).wont_include '?'
  end

  it 'mixed query with empty array omits the empty array but keeps other params' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)
    test_api.call('GET', '/test', { 'tags' => [], 'limit' => 10 }, {}, nil,
                  ['application/json'], 'application/json', nil)
    _(client.captured_url).wont_include 'tags'
    _(client.captured_url).must_include 'limit=10'
  end

  # ── Typed error body (#7) ──

  it 'ApiError#typed_error_body deserializes error body to typed model' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 400,
      response_body: '{"id":42,"name":"BadCategory"}',
      response_headers: {}
    )
    body = err.typed_error_body('Category')
    _(body).must_be_kind_of Petstore::Client::Models::Category
    _(body.id).must_equal 42
    _(body.name).must_equal 'BadCategory'
  end

  it 'ApiError#typed_error_body returns nil for empty body' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 500,
      response_body: '',
      response_headers: {}
    )
    assert_nil err.typed_error_body('Category')
  end

  # ── F855: a 401 drops the cached OAuth2 token and replays the request once ──

  def replay_api(client, auth)
    auth.api_client = client if auth.respond_to?(:api_client=)
    config = Petstore::Client::Configuration.builder.base_url('https://api.example.com').build
    TestableApi.new(client, config, auth)
  end

  def client_credentials_auth
    Petstore::Client::Auth::OAuth::OAuth2ClientCredentialsAuthenticator.new(
      'https://api.example.com', 'cid', 'csecret', 'https://auth.example.com/token', []
    )
  end

  def replay_call(api, content_type: 'application/json', body: nil, auth: nil)
    api.call('POST', '/items', {}, {}, body, ['application/json'], content_type, nil, auth)
  end

  it 'replays a request once with a renewed token after a 401' do
    client = ReplayApiClient.new([401, 200])
    api = replay_api(client, client_credentials_auth)

    replay_call(api, body: { 'a' => 1 })
    _(client.api_requests.map { |r| r[:headers]['Authorization'] }).must_equal ['Bearer tok1', 'Bearer tok2']
    _(client.api_requests.map { |r| r[:body] }.uniq.length).must_equal 1
  end

  it 'refreshes with the kept refresh token when it renews after a 401' do
    client = ReplayApiClient.new([401, 200])
    api = replay_api(client, client_credentials_auth)

    replay_call(api)
    _(client.token_requests.length).must_equal 2
    _(client.token_requests.last).must_include 'grant_type=refresh_token'
    _(client.token_requests.last).must_include 'refresh_token=ref'
  end

  it 'reports a second 401 as Unauthorized after exactly one replay' do
    client = ReplayApiClient.new([401, 401, 200])
    api = replay_api(client, client_credentials_auth)

    assert_raises(Petstore::Client::Errors::UnauthorizedError) { replay_call(api) }
    _(client.api_requests.length).must_equal 2
  end

  it 'does not replay a multipart body after a 401' do
    client = ReplayApiClient.new([401, 200])
    api = replay_api(client, client_credentials_auth)

    assert_raises(Petstore::Client::Errors::UnauthorizedError) do
      replay_call(api, content_type: 'multipart/form-data', body: { 'a' => 'b' })
    end
    _(client.api_requests.length).must_equal 1
  end

  it 'leaves the 401 as it is when the token cannot be renewed' do
    client = ReplayApiClient.new([401, 200], token_statuses: [200, 500, 500])
    api = replay_api(client, client_credentials_auth)

    assert_raises(Petstore::Client::Errors::UnauthorizedError) { replay_call(api) }
    _(client.api_requests.length).must_equal 1
  end

  it 'does not replay for an authenticator without a renewable token' do
    client = ReplayApiClient.new([401, 200])
    api = replay_api(client, TestAuthenticator.new(headers: { 'Authorization' => 'Bearer fixed' }))

    assert_raises(Petstore::Client::Errors::UnauthorizedError) { replay_call(api) }
    _(client.api_requests.length).must_equal 1
  end

  it 'does not replay an unauthenticated operation' do
    client = ReplayApiClient.new([401, 200])
    api = replay_api(client, client_credentials_auth)

    assert_raises(Petstore::Client::Errors::UnauthorizedError) do
      replay_call(api, auth: Petstore::Client::Api::BaseApi::NO_AUTH)
    end
    _(client.api_requests.length).must_equal 1
  end

  it 'does not replay other statuses' do
    client = ReplayApiClient.new([403, 200])
    api = replay_api(client, client_credentials_auth)

    assert_raises(Petstore::Client::Errors::ForbiddenError) { replay_call(api) }
    _(client.api_requests.length).must_equal 1
  end

  # ── Request context on errors ──

  it 'names the method and the path, never the query string, on an API error' do
    response = Petstore::Client::ApiHttpResponse.new(status_code: 404, body: '', headers: {})
    config = Petstore::Client::Configuration.builder.base_url('https://example.test/v1').build
    test_api = TestableApi.new(FixedResponseApiClient.new(response), config)

    err = assert_raises(Petstore::Client::Errors::NotFoundError) do
      test_api.call(:get, '/items/7', { 'token' => 'secret' }, {}, nil, ['application/json'], 'application/json', nil)
    end

    _(err.request_method).must_equal 'GET'
    _(err.request_path).must_equal '/v1/items/7'
    _(err.message).must_include("\nRequest: GET /v1/items/7")
    _(err.message).wont_include('secret')
  end

  it 'names the request on a failure that never reached the server' do
    config = Petstore::Client::Configuration.builder.base_url('https://example.test').build
    test_api = TestableApi.new(RaisingApiClient.new, config)

    err = assert_raises(Petstore::Client::Errors::NetworkError) do
      test_api.call(:post, '/items', {}, {}, nil, ['application/json'], 'application/json', nil)
    end

    _(err.request_method).must_equal 'POST'
    _(err.request_path).must_equal '/items'
    _(err.status_code).must_equal 0
  end

  # ── Per-call request options ──

  it 'sends the headers of the call over the configured default headers' do
    client = CapturingApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost')
                                          .default_headers({ 'X-Trace' => 'client', 'Accept-Language' => 'en' }).build
    test_api = TestableApi.new(client, config)
    options = CallOptions.new(nil, { 'x-trace' => 'call' }, nil)

    test_api.call_for_result(:get, '/items', {}, {}, nil, ['application/json'], 'application/json', nil,
                             request_options: options)

    _(client.captured_headers['x-trace']).must_equal 'call'
    _(client.captured_headers.keys.count { |name| name.casecmp?('x-trace') }).must_equal 1
    _(client.captured_headers['Accept-Language']).must_equal 'en'
  end

  it 'hands the timeout and retry count of the call to the transport' do
    client = RecordingDefaultApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)

    test_api.call_for_result(:get, '/items', {}, {}, nil, ['application/json'], 'application/json', nil,
                             request_options: CallOptions.new(2500, nil, 4))

    _(client.captured_kwargs[:timeout]).must_equal 2500
    _(client.captured_kwargs[:max_retries]).must_equal 4
  end

  it 'rejects a per-call timeout, retry count or header set that is not valid' do
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(RecordingDefaultApiClient.new, config)

    [CallOptions.new(0, nil, nil), CallOptions.new(nil, nil, -1), CallOptions.new(nil, { 'X' => 1 }, nil)].each do |options|
      _(lambda do
        test_api.call_for_result(:get, '/items', {}, {}, nil, ['application/json'], 'application/json', nil,
                                 request_options: options)
      end).must_raise ArgumentError
    end
  end

  it 'leaves the transport values alone when the call sets none' do
    client = RecordingDefaultApiClient.new
    config = Petstore::Client::Configuration.builder.base_url('http://localhost').build
    test_api = TestableApi.new(client, config)

    test_api.call_for_result(:get, '/items', {}, {}, nil, ['application/json'], 'application/json', nil,
                             request_options: CallOptions.new(nil, nil, nil))

    _(client.captured_kwargs.keys).must_equal([:retry_headers])
  end
end
