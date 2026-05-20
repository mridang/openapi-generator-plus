defmodule PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticatorTest do
  use ExUnit.Case, async: true

  defmodule FakeApiClient do
    defstruct [:agent]

    def new(responses) do
      {:ok, agent} = Agent.start_link(fn -> %{responses: responses, last_url: nil, last_body: nil, last_headers: nil} end)
      %__MODULE__{agent: agent}
    end

    def send_request(%__MODULE__{agent: agent}, _method, url, headers, body) do
      Agent.get_and_update(agent, fn state ->
        [response | rest] = state.responses
        new_state = %{state | responses: rest, last_url: url, last_body: body, last_headers: headers}
        {response, new_state}
      end)
    end

    def last_url(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_url)
    def last_body(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_body)
    def last_headers(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_headers)
  end

  defp create_authenticator do
    PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
      "https://api.example.com",
      "my-client-id",
      "my-client-secret",
      "https://auth.example.com/token",
      ["read", "write"]
    )
  end

  describe "OAuth2ClientCredentialsAuthenticator" do
    test "sends client credentials grant type" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "grant_type=client_credentials")
    end

    test "sends client id and secret" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "client_id=my-client-id")
      assert String.contains?(last_body, "client_secret=my-client-secret")
    end

    test "sends scopes" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "scope=read+write") or String.contains?(last_body, "scope=read%20write")
    end

    test "returns authorization bearer header" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok-abc", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.set_api_client(auth, fake_client)

      headers = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.auth_headers(auth)

      assert headers["Authorization"] == "Bearer tok-abc"
    end

    test "sends request to token URL" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.auth_headers(auth)

      assert FakeApiClient.last_url(fake_client) == "https://auth.example.com/token"
    end

    test "get_host returns configured host" do
      auth = create_authenticator()

      assert PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.host(auth) == "https://api.example.com"
    end

    test "basic auth URL-encodes client id and secret" do
      # Gap R: RFC 6749 §2.3.1 — when using client_secret_basic, both
      # client_id and client_secret MUST be application/x-www-form-
      # urlencoded BEFORE being joined with ':' and base64-encoded.
      # Verifies a client_id with `+` and a secret with `&` are encoded
      # (not raw) before the colon-join + base64.
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "at", "expires_in" => 3600})
        }
      ])

      auth = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
        "https://api.example.com",
        "id+with/special",
        "secret&with=stuff",
        "https://auth.example.com/token",
        ["read"],
        client_auth_method: :basic
      )
      auth = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.auth_headers(auth)

      headers = FakeApiClient.last_headers(fake_client)
      auth_header = headers["Authorization"]
      assert auth_header != nil
      assert String.starts_with?(auth_header, "Basic ")
      encoded = String.slice(auth_header, String.length("Basic ")..-1//1)
      decoded = Base.decode64!(encoded)
      # Expected: form-urlencoded id ':' form-urlencoded secret
      assert decoded == "id%2Bwith%2Fspecial:secret%26with%3Dstuff"
    end
  end
end
