# frozen_string_literal: true

require 'minitest/autorun'
require 'json'
require 'petstore_client'

class TestableApi < PetstoreClient::Api::BaseApi
  def call(method, path, query_params, header_params, body,
           accepts, content_type, return_type, auth = nil)
    invoke_api(method, path, query_params, header_params, body,
               accepts, content_type, return_type, auth)
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
      _(err.code).must_equal status
      _(err.response_body).wont_be_nil
      _(err.response_body).wont_be_empty
    end
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
end
