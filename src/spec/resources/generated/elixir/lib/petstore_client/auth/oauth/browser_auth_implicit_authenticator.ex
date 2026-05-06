defmodule PetstoreClient.Auth.OAuth.BrowserAuthImplicitAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator)}`.
  """

  @doc """
  Creates a new `BrowserAuthImplicitAuthenticator` authenticator.
  """
  def new(host, client_id) do
    PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.new(host, client_id, "https://auth.example.com/authorize", [])
  end
end
