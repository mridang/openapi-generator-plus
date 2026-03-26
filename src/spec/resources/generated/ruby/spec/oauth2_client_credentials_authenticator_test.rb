# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'json'
require 'uri'
require 'petstore_client'

class OAuth2ClientCredentialsAuthenticatorTest < Minitest::Test
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
    @token_url = 'https://auth.example.com/oauth/token'
    @scopes = %w[read write]
    @mock_client = MockApiClient.new

    @authenticator = PetstoreClient::Auth::OAuth::OAuth2ClientCredentialsAuthenticator.new(
      @host, @client_id, @client_secret, @token_url, @scopes
    )
    @authenticator.api_client = @mock_client
  end

  def test_sends_grant_type
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({ 'access_token' => 'cc_token', 'expires_in' => 3600 })
    )

    headers = @authenticator.auth_headers

    assert_equal 'Bearer cc_token', headers['Authorization']

    # Verify the request was made correctly
    call = @mock_client.calls.first
    assert_equal :post, call[:method]
    assert_equal @token_url, call[:url]

    parsed_body = URI.decode_www_form(call[:body]).to_h
    assert_equal 'client_credentials', parsed_body['grant_type']
  end

  def test_sends_client_credentials
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({ 'access_token' => 'cc_token', 'expires_in' => 3600 })
    )

    @authenticator.auth_headers

    call = @mock_client.calls.first
    parsed_body = URI.decode_www_form(call[:body]).to_h
    assert_equal 'my_client_id', parsed_body['client_id']
    assert_equal 'my_client_secret', parsed_body['client_secret']
    assert_equal 'read write', parsed_body['scope']
  end
end
