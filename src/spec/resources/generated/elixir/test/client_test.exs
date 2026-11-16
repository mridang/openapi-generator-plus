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

  test "construct with a custom Req adapter in the transport options" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")
    adapter = fn request -> {request, Req.Response.new(status: 200)} end
    transport = PetstoreClient.TransportOptions.new(req_options: [adapter: adapter])

    client = PetstoreClient.Client.new(authenticator, transport)

    assert %PetstoreClient.Client{} = client
  end

  test "close releases the transport and the authenticator's token manager" do
    authenticator =
      PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
        "/api/v3",
        "id",
        "secret",
        "https://auth.example.com/token",
        []
      )

    client = PetstoreClient.Client.new(authenticator)
    manager = authenticator.token_manager
    assert Process.alive?(manager)

    assert PetstoreClient.Client.close(client) == :ok
    refute Process.alive?(manager)
    assert PetstoreClient.Client.close(client) == :ok

    err =
      assert_raise RuntimeError, fn ->
        PetstoreClient.DefaultApiClient.send_request(
          client.api_client,
          :get,
          "http://127.0.0.1:1/never",
          %{},
          nil
        )
      end

    assert err.message == "ApiClient has been closed and can no longer send requests"
  end

  test "close works for an authenticator that starts nothing" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")
    client = PetstoreClient.Client.new(authenticator)

    assert PetstoreClient.Client.close(client) == :ok
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
