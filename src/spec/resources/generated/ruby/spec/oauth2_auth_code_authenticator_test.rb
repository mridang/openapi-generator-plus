# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'json'
require 'uri'
require 'petstore_client'

class OAuth2AuthCodeAuthenticatorTest < Minitest::Test
  # A simple mock API client that captures requests and returns preconfigured responses.
  class MockApiClient
    attr_reader :calls

    def initialize
      @calls = []
      @responses = []
    end

    def add_response(status_code:, body:, headers: { 'Content-Type' => 'application/json' })
      @responses << PetstoreClient::ApiResponse.new(
        status_code: status_code,
        body: body,
        headers: headers
      )
    end

    def send_request(method, url, headers, body)
      @calls << { method: method, url: url, headers: headers, body: body }
      raise 'No more mock responses configured' if @responses.empty?

      @responses.shift
    end
  end

  def setup
    @host = 'https://api.example.com'
    @client_id = 'my_client_id'
    @client_secret = 'my_client_secret'
    @authorization_url = 'https://auth.example.com/authorize'
    @token_url = 'https://auth.example.com/oauth/token'
    @redirect_uri = 'https://app.example.com/callback'
    @scopes = %w[read write]
    @mock_client = MockApiClient.new

    @authenticator = PetstoreClient::Auth::OAuth::OAuth2AuthorizationCodeAuthenticator.new(
      @host, @client_id, @client_secret,
      @authorization_url, @token_url, @redirect_uri, @scopes
    )
    @authenticator.api_client = @mock_client
  end

  def test_builds_authorization_url
    url = @authenticator.build_authorization_url('csrf_state_123')
    uri = URI.parse(url)
    params = URI.decode_www_form(uri.query).to_h

    assert_equal 'https', uri.scheme
    assert_equal 'auth.example.com', uri.host
    assert_equal '/authorize', uri.path
    assert_equal 'code', params['response_type']
    assert_equal 'my_client_id', params['client_id']
    assert_equal 'https://app.example.com/callback', params['redirect_uri']
    assert_equal 'read write', params['scope']
    assert_equal 'csrf_state_123', params['state']
  end

  def test_exchanges_code_for_token
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({ 'access_token' => 'access_123', 'expires_in' => 3600 })
    )

    @authenticator.exchange_code('auth_code_xyz')

    # Verify the request was made correctly
    call = @mock_client.calls.first
    assert_equal :post, call[:method]
    assert_equal @token_url, call[:url]
    assert_equal 'application/x-www-form-urlencoded', call[:headers]['Content-Type']

    parsed_body = URI.decode_www_form(call[:body]).to_h
    assert_equal 'authorization_code', parsed_body['grant_type']
    assert_equal 'auth_code_xyz', parsed_body['code']
    assert_equal @client_id, parsed_body['client_id']
    assert_equal @client_secret, parsed_body['client_secret']
    assert_equal @redirect_uri, parsed_body['redirect_uri']
  end

  def test_auth_headers_after_exchange
    # First exchange the code
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({
        'access_token' => 'access_123',
        'expires_in' => 3600,
        'refresh_token' => 'refresh_456'
      })
    )
    @authenticator.exchange_code('auth_code_xyz')

    # Now call auth_headers which should attempt a refresh.
    # Add another response for the refresh request.
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({
        'access_token' => 'refreshed_token',
        'expires_in' => 3600
      })
    )

    headers = @authenticator.auth_headers
    assert headers.key?('Authorization')
    assert headers['Authorization'].start_with?('Bearer ')
  end
end
