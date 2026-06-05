defmodule PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticatorTest do
  use ExUnit.Case, async: true

  defmodule FakeApiClient do
    defstruct [:agent]

    def new(responses) do
      {:ok, agent} = Agent.start_link(fn -> %{responses: responses, last_url: nil, last_body: nil} end)
      %__MODULE__{agent: agent}
    end

    def send_request(%__MODULE__{agent: agent}, _method, url, _headers, body) do
      Agent.get_and_update(agent, fn state ->
        [response | rest] = state.responses
        new_state = %{state | responses: rest, last_url: url, last_body: body}
        {response, new_state}
      end)
    end

    def last_url(%__MODULE__{agent: agent}) do
      Agent.get(agent, & &1.last_url)
    end

    def last_body(%__MODULE__{agent: agent}) do
      Agent.get(agent, & &1.last_body)
    end
  end

  defp create_authenticator do
    PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.new(
      "https://api.example.com",
      "my-client-id",
      "my-client-secret",
      "https://auth.example.com/authorize",
      "https://auth.example.com/token",
      "https://app.example.com/callback",
      ["read", "write"]
    )
  end

  describe "OAuth2AuthorizationCodeAuthenticator" do
    test "builds authorization URL with required params" do
      auth = create_authenticator()

      url = PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.build_authorization_url(auth)

      assert String.starts_with?(url, "https://auth.example.com/authorize?")
      assert String.contains?(url, "response_type=code")
      assert String.contains?(url, "client_id=my-client-id")
      assert String.contains?(url, "redirect_uri=")
      assert String.contains?(url, "scope=read+write") or String.contains?(url, "scope=read%20write")
    end

    test "builds authorization URL with state" do
      auth = create_authenticator()

      url =
        PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.build_authorization_url(auth, "csrf-state-123")

      assert String.contains?(url, "state=csrf-state-123")
    end

    test "exchanges code with correct grant type" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "refresh_token" => "ref1", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.exchange_code(auth, "auth-code-xyz")

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "grant_type=authorization_code")
      assert String.contains?(last_body, "code=auth-code-xyz")
      assert String.contains?(last_body, "client_id=my-client-id")
      assert String.contains?(last_body, "client_secret=my-client-secret")
    end

    test "includes refresh token on refresh" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "refresh_token" => "ref1", "expires_in" => 1})
          },
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.set_api_client(auth, fake_client)

      auth = PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.exchange_code(auth, "auth-code-xyz")
      headers = PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "refresh_token=ref1")
      assert String.contains?(last_body, "grant_type=refresh_token")
      assert headers["Authorization"] == "Bearer tok2"
    end

    test "rejects empty authorization code" do
      auth = create_authenticator()

      assert_raise ArgumentError, fn ->
        PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.exchange_code(auth, "")
      end
    end

    test "rejects whitespace-only authorization code" do
      auth = create_authenticator()

      assert_raise ArgumentError, fn ->
        PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.exchange_code(auth, "   ")
      end
    end

    test "throws before exchange code called" do
      auth = create_authenticator()

      assert_raise RuntimeError, fn ->
        PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.auth_headers(auth)
      end
    end

    test "get_host returns configured host" do
      auth = create_authenticator()

      assert PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.host(auth) == "https://api.example.com"
    end

    test "auth_headers_before_exchange_returns_recoverable_error" do
      auth = create_authenticator()

      # Calling auth_headers before exchange_code is a precondition
      # violation. It must surface as a catchable exception so callers
      # can recover -- not as a process crash.
      caught =
        try do
          PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.auth_headers(auth)
          nil
        rescue
          e in RuntimeError -> e
        end

      assert caught != nil, "expected auth_headers to raise before exchange_code"

      # Caller continues normally after rescuing -- no process crash.
      assert PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.host(auth) ==
               "https://api.example.com"
    end

    test "authorize URL with existing query string uses amp separator" do
      auth =
        PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.new(
          "https://api.example.com",
          "my-client-id",
          "my-client-secret",
          "https://x.auth0.com/authorize?audience=api",
          "https://x.auth0.com/oauth/token",
          "https://app.example.com/callback",
          ["read"]
        )

      url = PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.build_authorization_url(auth)

      assert String.contains?(url, "audience=api")
      assert String.contains?(url, "response_type=code")
      assert length(String.split(url, "?")) - 1 == 1
    end
  end
end
