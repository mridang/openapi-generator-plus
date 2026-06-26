# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
defmodule PetstoreClient.ClientTest do
  use ExUnit.Case, async: true

  test "construct with authenticator only" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")

    client = PetstoreClient.Client.new(authenticator)

    assert %PetstoreClient.Client{} = client
  end

  test "construct with authenticator and nil transport options" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")

    client = PetstoreClient.Client.new(authenticator, nil)

    assert %PetstoreClient.Client{} = client
  end

  test "construct with authenticator and transport options" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")
    transport = PetstoreClient.TransportOptions.new()

    client = PetstoreClient.Client.new(authenticator, transport)

    assert %PetstoreClient.Client{} = client
  end

  test "BearerAuthenticator rejects CR/LF and non-ASCII (RFC 7230 §3.2.6)" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "tok\r\nInjected: yes")
    end

    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "ñoño")
    end
  end

  test "BearerAuthenticator rejects an empty or whitespace token" do
    # bearer-no-empty-token-guard: an empty/whitespace token would emit a
    # bare "Authorization: Bearer " header, sending the request
    # unauthenticated, so new/2 must reject it.
    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "")
    end

    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "   ")
    end
  end

  test "ApiKeyAuthenticator :header rejects CR/LF and non-ASCII (RFC 7230 §3.2.6)" do
    # HEADER location must reject anything outside printable ASCII + TAB
    # to prevent header injection (\r\n) and silent UTF-8 mangling that
    # varies per HTTP lib.
    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.ApiKeyAuthenticator.new(
        "/api/v3",
        "X-Api-Key",
        "abc\r\nInjected: yes",
        :header
      )
    end

    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.ApiKeyAuthenticator.new("/api/v3", "X-Api-Key", "kéy", :header)
    end

    # Non-header locations accept arbitrary chars.
    query_auth = PetstoreClient.Auth.ApiKeyAuthenticator.new("/api/v3", "api_key", "kéy", :query)

    assert PetstoreClient.Auth.ApiKeyAuthenticator.query_params(query_auth) == %{
             "api_key" => "kéy"
           }
  end

  test "API groups are accessible" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")

    client = PetstoreClient.Client.new(authenticator)

    assert client.pet != nil
    assert client.store != nil
  end
end
