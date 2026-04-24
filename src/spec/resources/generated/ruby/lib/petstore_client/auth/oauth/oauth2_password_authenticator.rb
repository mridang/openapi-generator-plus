# frozen_string_literal: true

module PetstoreClient
  module Auth
    module OAuth
      # Authenticator for OAuth2 resource owner password credentials flow.
      #
      # Includes {HttpAwareAuthenticator} so that token exchange requests
      # use the shared {ApiClient} with the same transport configuration
      # (proxy, TLS, timeouts) as regular API calls.
      class OAuth2PasswordAuthenticator < BaseAuthenticator
        include HttpAwareAuthenticator

        # @return [String]
        attr_reader :host

        # Create a new password authenticator.
        #
        # @param host [String] API base URL
        # @param client_id [String] OAuth2 client ID
        # @param client_secret [String] OAuth2 client secret
        # @param token_url [String] token endpoint URL
        # @param username [String] resource owner username
        # @param password [String] resource owner password
        # @param scopes [Array<String>] requested scopes
        # @param refresh_url [String, nil] refresh endpoint URL (defaults to token_url)
        # rubocop:disable Metrics/ParameterLists
        def initialize(host, client_id, client_secret, token_url, username, password, scopes, refresh_url: nil)
          super()
          @host = host
          @client_id = client_id
          @client_secret = client_secret
          @token_url = token_url
          @refresh_url = refresh_url || token_url
          @username = username
          @password = password
          @scopes = scopes.freeze
          @token_manager = OAuth2TokenManager.new
        end
        # rubocop:enable Metrics/ParameterLists

        # Inject the shared API client for making token requests.
        #
        # @param client [ApiClient] the shared API client instance
        # @return [void]
        def api_client=(client)
          @token_manager.api_client = client
        end

        # @return [Hash{String => String}]
        def auth_headers # rubocop:disable Metrics/MethodLength
          if @token_manager.refresh_token
            params = {
              'grant_type' => 'refresh_token',
              'refresh_token' => @token_manager.refresh_token
            }
            token = @token_manager.get_access_token(@refresh_url, params)
          else
            params = {
              'grant_type' => 'password',
              'client_id' => @client_id,
              'client_secret' => @client_secret,
              'username' => @username,
              'password' => @password
            }
            params['scope'] = @scopes.join(' ') unless @scopes.empty?
            token = @token_manager.get_access_token(@token_url, params)
          end
          { 'Authorization' => "Bearer #{token}" }
        end
      end
    end
  end
end
