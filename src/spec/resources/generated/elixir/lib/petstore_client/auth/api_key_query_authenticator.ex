# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
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
