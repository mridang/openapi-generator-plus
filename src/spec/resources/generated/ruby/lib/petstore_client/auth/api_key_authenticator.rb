# frozen_string_literal: true

module PetstoreClient
  module Auth
    # Authenticator for API key authentication.
    class ApiKeyAuthenticator < BaseAuthenticator
      attr_reader :host

      # @param host [String] API base URL
      # @param key_param_name [String] name of the key parameter
      # @param api_key [String] the API key value
      # @param location [Symbol] :header, :query, or :cookie
      def initialize(host, key_param_name, api_key, location)
        super()
        @host = host
        @key_param_name = key_param_name
        @api_key = api_key
        @location = location
      end

      # @return [Hash{String => String}]
      def auth_headers
        result = {} # : Hash[String, String]
        result[@key_param_name] = @api_key if @location == ApiKeyLocation::HEADER
        result
      end

      # @return [Hash{String => String}]
      def query_params
        result = {} # : Hash[String, String]
        result[@key_param_name] = @api_key if @location == ApiKeyLocation::QUERY
        result
      end

      # @return [Hash{String => String}]
      def cookie_params
        result = {} # : Hash[String, String]
        result[@key_param_name] = @api_key if @location == ApiKeyLocation::COOKIE
        result
      end
    end
  end
end
