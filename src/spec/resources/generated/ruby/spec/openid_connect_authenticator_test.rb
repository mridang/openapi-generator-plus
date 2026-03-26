# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'json'
require 'uri'
require 'petstore_client'

class OpenIdConnectAuthenticatorTest < Minitest::Test
  # A mock API client that routes responses based on URL matching.
  class MockApiClient
    attr_reader :calls

    def initialize
      @calls = []
      @route_responses = {}
    end

    # Register a response for a specific URL pattern.
    def stub_url(url, status_code:, body:, headers: { 'Content-Type' => 'application/json' })
      @route_responses[url] ||= []
      @route_responses[url] << PetstoreClient::ApiResponse.new(
        status_code: status_code,
        body: body,
        headers: headers
      )
    end

    def send_request(method, url, headers, body)
      @calls << { method: method, url: url, headers: headers, body: body }

      responses = @route_responses[url]
      raise "No mock response configured for URL: #{url}" if responses.nil? || responses.empty?

      responses.shift
    end
  end

  def setup
    @host = 'https://api.example.com'
    @openid_connect_url = 'https://auth.example.com/.well-known/openid-configuration'
    @client_id = 'my_client_id'
    @client_secret = 'my_client_secret'
    @redirect_uri = 'https://app.example.com/callback'
    @scopes = %w[openid profile]
    @mock_client = MockApiClient.new

    @discovery_document = {
      'authorization_endpoint' => 'https://auth.example.com/authorize',
      'token_endpoint' => 'https://auth.example.com/oauth/token',
      'issuer' => 'https://auth.example.com'
    }

    @authenticator = PetstoreClient::Auth::OAuth::OpenIdConnectAuthenticator.new(
      @host, @openid_connect_url, @client_id, @client_secret,
      @redirect_uri, @scopes
    )
    @authenticator.api_client = @mock_client
  end

  def stub_discovery_response
    @mock_client.stub_url(
      @openid_connect_url,
      status_code: 200,
      body: JSON.generate(@discovery_document)
    )
  end

  def test_builds_authorization_url
    stub_discovery_response

    url = @authenticator.build_authorization_url('csrf_state_123')
    uri = URI.parse(url)
    params = URI.decode_www_form(uri.query).to_h

    # The OIDC authenticator discovers endpoints from the discovery document,
    # then delegates to an OAuth2AuthorizationCodeAuthenticator.
    assert_equal 'auth.example.com', uri.host
    assert_equal '/authorize', uri.path
    assert_equal 'code', params['response_type']
    assert_equal 'my_client_id', params['client_id']
    assert_equal 'https://app.example.com/callback', params['redirect_uri']
    assert_equal 'openid profile', params['scope']
    assert_equal 'csrf_state_123', params['state']
  end

  def test_obtains_token
    stub_discovery_response

    token_url = 'https://auth.example.com/oauth/token'

    # Stub the token exchange request
    @mock_client.stub_url(
      token_url,
      status_code: 200,
      body: JSON.generate({
        'access_token' => 'oidc_access_token',
        'expires_in' => 3600,
        'id_token' => 'oidc_id_token'
      })
    )

    # exchange_code should first discover endpoints (if not cached),
    # then exchange the authorization code for tokens.
    @authenticator.exchange_code('auth_code_xyz')

    # After exchanging the code, auth_headers should return a Bearer token.
    # Stub the refresh/re-fetch call that auth_headers makes.
    @mock_client.stub_url(
      token_url,
      status_code: 200,
      body: JSON.generate({
        'access_token' => 'oidc_access_token',
        'expires_in' => 3600,
        'id_token' => 'oidc_id_token'
      })
    )

    headers = @authenticator.auth_headers
    assert_equal 'Bearer oidc_access_token', headers['Authorization']
  end
end
