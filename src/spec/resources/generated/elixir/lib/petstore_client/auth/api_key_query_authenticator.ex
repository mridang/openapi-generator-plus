defmodule PetstoreClient.Auth.ApiKeyQueryAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.ApiKeyAuthenticator)}`.
  """

  @doc """
  Creates a new `ApiKeyQueryAuthenticator` authenticator.
  """
  def new(host, api_key) do
    PetstoreClient.Auth.ApiKeyAuthenticator.new(host, "api_key", api_key, :query)
  end
end
