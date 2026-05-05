defmodule PetstoreClient.Auth.OAuth.OAuth2TokenManagerTest do
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

  describe "OAuth2TokenManager" do
    test "extracts access token from response" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok123", "expires_in" => 3600})
          }
        ])

      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_api_client(manager, fake_client)

      token =
        PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(
          manager,
          "https://auth.example.com/token",
          %{"grant_type" => "client_credentials"}
        )

      assert token == "tok123"
    end

    test "stores refresh token" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "refresh_token" => "ref1", "expires_in" => 3600})
          }
        ])

      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_api_client(manager, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(
        manager,
        "https://auth.example.com/token",
        %{"grant_type" => "authorization_code"}
      )

      assert PetstoreClient.Auth.OAuth.OAuth2TokenManager.refresh_token(manager) == "ref1"
    end

    test "returns cached token when not expired" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
          }
        ])

      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_api_client(manager, fake_client)

      params = %{"grant_type" => "client_credentials"}
      token_url = "https://auth.example.com/token"

      first = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)
      second = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)

      assert first == "tok1"
      assert second == "tok1"
    end

    test "refetches token when expired" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 1})
          },
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_api_client(manager, fake_client)

      params = %{"grant_type" => "client_credentials"}
      token_url = "https://auth.example.com/token"

      first = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)
      second = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)

      assert first == "tok1"
      assert second == "tok2"
    end

    test "set_access_token bypasses endpoint" do
      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_access_token(manager, "manual-token")

      token =
        PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(
          manager,
          "https://auth.example.com/token",
          %{}
        )

      assert token == "manual-token"
    end

    test "raises when no ApiClient injected" do
      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()

      assert_raise RuntimeError, fn ->
        PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(
          manager,
          "https://auth.example.com/token",
          %{"grant_type" => "client_credentials"}
        )
      end
    end
  end
end
