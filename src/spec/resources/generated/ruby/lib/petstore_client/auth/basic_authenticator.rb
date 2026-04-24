# frozen_string_literal: true

require 'base64'

module PetstoreClient
  module Auth
    # Authenticator for HTTP Basic authentication.
    class BasicAuthenticator < BaseAuthenticator
      attr_reader :host

      # @param host [String] API base URL
      # @param username [String]
      # @param password [String]
      def initialize(host, username, password)
        super()
        @host = host
        @auth_header = "Basic #{Base64.strict_encode64("#{username}:#{password}")}"
      end

      # @return [Hash{String => String}]
      def auth_headers
        { 'Authorization' => @auth_header }
      end
    end
  end
end
