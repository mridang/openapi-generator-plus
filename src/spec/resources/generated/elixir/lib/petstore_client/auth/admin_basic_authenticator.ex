defmodule PetstoreClient.Auth.AdminBasicAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.BasicAuthenticator)}`.
  """

  @doc """
  Creates a new `AdminBasicAuthenticator` authenticator.
  """
  def new(host, username, password) do
    PetstoreClient.Auth.BasicAuthenticator.new(host, username, password)
  end
end
