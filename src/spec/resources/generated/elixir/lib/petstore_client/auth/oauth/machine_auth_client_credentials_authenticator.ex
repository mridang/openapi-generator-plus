defmodule PetstoreClient.Auth.OAuth.MachineAuthClientCredentialsAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator)}`.
  """

  @doc """
  Creates a new `MachineAuthClientCredentialsAuthenticator` authenticator.
  """
  def new(host, client_id, client_secret) do
    PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(host, client_id, client_secret, "https://auth.example.com/oauth/token", [])
  end
end
