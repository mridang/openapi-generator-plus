defmodule PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticatorTest do
  use ExUnit.Case, async: true

  defmodule FakeApiClient do
    defstruct [:responses, :last_url, :last_body]

    def new(responses) do
      {:ok, agent} = Agent.start_link(fn -> %{responses: responses, last_url: nil, last_body: nil} end)
      agent
    end

    def send_request(agent, _method, url, _headers, body) do
      Agent.get_and_update(agent, fn state ->
        [response | rest] = state.responses
        new_state = %{state | responses: rest, last_url: url, last_body: body}
        {response, new_state}
      end)
    end

    def last_url(agent), do: Agent.get(agent, & &1.last_url)
    def last_body(agent), do: Agent.get(agent, & &1.last_body)
  end

  defp create_authenticator do
    PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
      "https://api.example.com",
      "my-client-id",
      "my-client-secret",
      "https://auth.example.com/token",
      "testuser",
      "testpass",
      ["read", "write"]
    )
  end

  describe "OAuth2PasswordAuthenticator" do
    test "sends grant_type=password" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "grant_type=password")
    end

    test "sends username and password" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "username=testuser")
      assert String.contains?(last_body, "password=testpass")
    end

    test "sends client_id and client_secret" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "client_id=my-client-id")
      assert String.contains?(last_body, "client_secret=my-client-secret")
    end

    test "returns Bearer Authorization header" do
      fake_client = FakeApiClient.new([
        %PetstoreClient.ApiResponse{
          status_code: 200,
          body: Jason.encode!(%{"access_token" => "tok-pwd", "expires_in" => 3600})
        }
      ])

      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      headers = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      assert headers["Authorization"] == "Bearer tok-pwd"
    end

    test "uses refresh_token on subsequent calls" do
      fake_client = FakeApiClient.new([
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
      auth = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      # First call uses password grant
      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      # Second call should use refresh_token grant since token is expired
      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "grant_type=refresh_token")
      assert String.contains?(last_body, "refresh_token=ref1")
    end

    test "get_host returns configured host" do
      auth = create_authenticator()

      assert PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.host(auth) == "https://api.example.com"
    end
  end
end
