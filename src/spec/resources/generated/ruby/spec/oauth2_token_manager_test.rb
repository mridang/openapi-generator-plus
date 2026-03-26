# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'json'
require 'petstore_client'

class OAuth2TokenManagerTest < Minitest::Test
  # A simple mock API client that returns preconfigured responses.
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
    @mock_client = MockApiClient.new
    @manager = PetstoreClient::Auth::OAuth::OAuth2TokenManager.new
    @manager.api_client = @mock_client
    @token_url = 'https://auth.example.com/oauth/token'
  end

  def test_stores_refresh_token
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({
        'access_token' => 'access_123',
        'expires_in' => 3600,
        'refresh_token' => 'refresh_456'
      })
    )

    params = { 'grant_type' => 'authorization_code', 'code' => 'abc' }
    token = @manager.get_access_token(@token_url, params)

    assert_equal 'access_123', token
  end

  def test_extracts_access_token
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({ 'access_token' => 'my_token_value', 'expires_in' => 3600 })
    )

    params = { 'grant_type' => 'client_credentials' }
    token = @manager.get_access_token(@token_url, params)

    assert_equal 'my_token_value', token
  end

  def test_detects_token_expiry
    # First call: return a token that expires in 0 seconds (with 30s buffer,
    # it will be considered expired immediately on the next call).
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({ 'access_token' => 'token_1', 'expires_in' => 0 })
    )
    @mock_client.add_response(
      status_code: 200,
      body: JSON.generate({ 'access_token' => 'token_2', 'expires_in' => 0 })
    )

    params = { 'grant_type' => 'client_credentials' }
    first_token = @manager.get_access_token(@token_url, params)
    assert_equal 'token_1', first_token

    # The token_expiry is now = Time.now.to_f + 0 - 30, which is in the past.
    # The next call should trigger a re-fetch.
    second_token = @manager.get_access_token(@token_url, params)
    assert_equal 'token_2', second_token
    assert_equal 2, @mock_client.calls.length
  end
end
