# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
defmodule PetstoreClient.Auth.OAuth.UserAuthAuthorizationCodeAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator)}`.
  """

  @doc """
  Creates a new `UserAuthAuthorizationCodeAuthenticator` authenticator.
  """
  def new(host, client_id, client_secret, redirect_uri) do
    PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.new(
      host,
      client_id,
      client_secret,
      "https://auth.example.com/authorize",
      "https://auth.example.com/oauth/token",
      redirect_uri,
      [],
      "https://auth.example.com/oauth/refresh"
    )
  end
end
