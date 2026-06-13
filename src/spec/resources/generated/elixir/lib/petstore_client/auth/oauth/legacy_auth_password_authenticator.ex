# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
defmodule PetstoreClient.Auth.OAuth.LegacyAuthPasswordAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator)}`.
  """

  @doc """
  Creates a new `LegacyAuthPasswordAuthenticator` authenticator.
  """
  def new(host, client_id, client_secret, username, password) do
    PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
      host,
      client_id,
      client_secret,
      "https://auth.example.com/oauth/token",
      "https://auth.example.com/oauth/refresh",
      username,
      password,
      []
    )
  end
end
