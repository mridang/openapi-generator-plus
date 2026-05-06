defmodule PetstoreClient.Auth.PetStoreBearerAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.BearerAuthenticator)}`.
  """

  @doc """
  Creates a new `PetStoreBearerAuthenticator` authenticator.
  """
  def new(host, token) do
    PetstoreClient.Auth.BearerAuthenticator.new(host, token)
  end
end
