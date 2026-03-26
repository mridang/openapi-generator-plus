# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'json'
require 'uri'
require 'petstore_client'

class OAuth2ImplicitAuthenticatorTest < Minitest::Test
  def setup
    @host = 'https://api.example.com'
    @client_id = 'my-client-id'
    @authorization_url = 'https://auth.example.com/authorize'
    @scopes = %w[read write]

    @authenticator = PetstoreClient::Auth::OAuth::OAuth2ImplicitAuthenticator.new(
      @host, @client_id, @authorization_url, @scopes
    )
  end

  def test_builds_authorization_url
    url = @authenticator.build_authorization_url('csrf_state_123')
    uri = URI.parse(url)
    params = URI.decode_www_form(uri.query).to_h

    assert_equal 'https', uri.scheme
    assert_equal 'auth.example.com', uri.host
    assert_equal '/authorize', uri.path
    assert_equal 'token', params['response_type']
    assert_equal 'read write', params['scope']
    assert_equal 'csrf_state_123', params['state']
  end

  def test_includes_client_id
    url = @authenticator.build_authorization_url
    uri = URI.parse(url)
    params = URI.decode_www_form(uri.query).to_h

    assert params.key?('client_id')
  end
end
