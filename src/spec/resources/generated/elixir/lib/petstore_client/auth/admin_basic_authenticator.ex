# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
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
