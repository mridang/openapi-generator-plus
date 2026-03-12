# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'minitest/pride'
require 'petstore_client'

# Configure the client to use the API_BASE_URL environment variable
PetstoreClient.configure do |config|
  config.base_url = ENV['API_BASE_URL'] || 'http://localhost:4010'
  config.default_headers['Authorization'] = 'Bearer test-token'
end
