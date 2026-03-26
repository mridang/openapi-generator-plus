# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'json'
require 'petstore_client'

# A concrete subclass so we can exercise the protected helpers on BaseApi.
class TestableApi < PetstoreClient::Api::BaseApi
  # Expose build_query_string for testing.
  def query_string(params)
    send(:build_query_string, params)
  end
end

class BaseApiUnitTest < Minitest::Test
  def setup
    @config = PetstoreClient::Configuration.new(
      base_url: 'https://petstore.example.com/api/v3',
      default_headers: {}
    )
    @api = TestableApi.new(nil, @config)
  end

  def test_expands_array_query_params
    qs = @api.query_string({ 'tags' => %w[a b] })
    assert_equal 'tags=a&tags=b', qs
  end

  def test_serializes_boolean_query_params
    qs = @api.query_string({ 'active' => true })
    assert_equal 'active=true', qs
  end

  def test_serializes_number_query_params
    qs = @api.query_string({ 'limit' => 10 })
    assert_equal 'limit=10', qs
  end

  def test_handles_empty_query_params
    qs = @api.query_string({})
    assert_equal '', qs
  end
end
