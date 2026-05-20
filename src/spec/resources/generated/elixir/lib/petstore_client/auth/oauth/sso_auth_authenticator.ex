defmodule PetstoreClient.Auth.OAuth.SsoAuthAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator)}`.
  """

  @doc """
  Creates a new `SsoAuthAuthenticator` authenticator.
  """
  def new(host, client_id, client_secret, redirect_uri) do
    PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.new(
      host,
      "https://auth.example.com/.well-known/openid-configuration",
      client_id,
      client_secret,
      redirect_uri,
      []
    )
  end
end
