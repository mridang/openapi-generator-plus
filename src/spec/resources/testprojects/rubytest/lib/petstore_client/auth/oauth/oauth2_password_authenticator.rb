# frozen_string_literal: true

module PetstoreClient
  module Auth
    module OAuth
      # Authenticator for OAuth2 resource owner password credentials flow.
      class OAuth2PasswordAuthenticator < Authenticator
        attr_reader :host

        # rubocop:disable Metrics/ParameterLists
        def initialize(host, client_id, client_secret, token_url, username, password, scopes)
          super()
          @host = host
          @client_id = client_id
          @client_secret = client_secret
          @token_url = token_url
          @username = username
          @password = password
          @scopes = scopes.freeze
          @token_manager = OAuth2TokenManager.new
        end
        # rubocop:enable Metrics/ParameterLists

        def auth_headers
          params = {
            'grant_type' => 'password',
            'client_id' => @client_id,
            'client_secret' => @client_secret,
            'username' => @username,
            'password' => @password
          }
          params['scope'] = @scopes.join(' ') unless @scopes.empty?
          token = @token_manager.get_access_token(@token_url, params)
          { 'Authorization' => "Bearer #{token}" }
        end
      end
    end
  end
end
