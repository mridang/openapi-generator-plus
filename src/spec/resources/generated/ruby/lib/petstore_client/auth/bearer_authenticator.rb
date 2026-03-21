# frozen_string_literal: true

module PetstoreClient
  module Auth
    # Authenticator for HTTP Bearer token authentication.
    class BearerAuthenticator < Authenticator
      attr_reader :host

      # @param host [String] API base URL
      # @param token [String] Bearer token
      def initialize(host, token)
        super()
        @host = host
        @token = token
      end

      # @return [Hash{String => String}]
      def auth_headers
        { 'Authorization' => "Bearer #{@token}" }
      end
    end
  end
end
