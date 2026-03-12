# frozen_string_literal: true

module PetstoreClient
  module Auth
    module OAuth
      # Authenticator for OAuth2 implicit flow.
      class OAuth2ImplicitAuthenticator < Authenticator
        attr_reader :host
        attr_writer :access_token

        def initialize(host, authorization_url, scopes)
          super()
          @host = host
          @authorization_url = authorization_url
          @scopes = scopes.freeze
          @access_token = nil
        end

        def build_authorization_url(state = nil)
          params = { 'response_type' => 'token' }
          params['scope'] = @scopes.join(' ') unless @scopes.empty?
          params['state'] = state if state
          "#{@authorization_url}?#{URI.encode_www_form(params)}"
        end

        def auth_headers
          raise 'Must set access_token before making API requests' if @access_token.nil?

          { 'Authorization' => "Bearer #{@access_token}" }
        end
      end
    end
  end
end
