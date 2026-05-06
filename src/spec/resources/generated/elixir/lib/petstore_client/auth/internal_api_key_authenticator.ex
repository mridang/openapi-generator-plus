defmodule PetstoreClient.Auth.InternalApiKeyAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.ApiKeyAuthenticator)}`.
  """

  @doc """
  Creates a new `InternalApiKeyAuthenticator` authenticator.
  """
  def new(host, api_key) do
    PetstoreClient.Auth.ApiKeyAuthenticator.new(host, "X-Internal-Key", api_key, :header)
  end
end
