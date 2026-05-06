defmodule PetstoreClient.Auth.SessionCookieAuthenticator do
  @moduledoc """
  Scheme-specific authenticator generated from the OpenAPI security scheme.
  Delegates to `#{inspect(PetstoreClient.Auth.ApiKeyAuthenticator)}`.
  """

  @doc """
  Creates a new `SessionCookieAuthenticator` authenticator.
  """
  def new(host, api_key) do
    PetstoreClient.Auth.ApiKeyAuthenticator.new(host, "SESSION_ID", api_key, :cookie)
  end
end
