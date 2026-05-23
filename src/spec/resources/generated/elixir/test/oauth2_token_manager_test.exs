defmodule PetstoreClient.Auth.OAuth.OAuth2TokenManagerTest do
  use ExUnit.Case, async: true

  defmodule FakeApiClient do
    defstruct [:agent]

    def new(responses) do
      {:ok, agent} = Agent.start_link(fn -> %{responses: responses, last_url: nil, last_body: nil, call_count: 0} end)
      %__MODULE__{agent: agent}
    end

    def send_request(%__MODULE__{agent: agent}, _method, url, _headers, body) do
      Agent.get_and_update(agent, fn state ->
        [response | rest] = state.responses

        new_state = %{
          state
          | responses: rest,
            last_url: url,
            last_body: body,
            call_count: state.call_count + 1
        }

        {response, new_state}
      end)
    end

    def last_url(%__MODULE__{agent: agent}) do
      Agent.get(agent, & &1.last_url)
    end

    def last_body(%__MODULE__{agent: agent}) do
      Agent.get(agent, & &1.last_body)
    end

    def call_count(%__MODULE__{agent: agent}) do
      Agent.get(agent, & &1.call_count)
    end
  end

  defmodule CountingApiClient do
    @moduledoc """
    Always returns the same access-token response, but counts calls. Used to
    assert single-flight refresh under concurrent load.
    """

    defstruct [:agent]

    def new do
      {:ok, agent} = Agent.start_link(fn -> 0 end)
      %__MODULE__{agent: agent}
    end

    def send_request(%__MODULE__{agent: agent}, _method, _url, _headers, _body) do
      Agent.update(agent, &(&1 + 1))
      # Simulate a slow OP so concurrent callers genuinely race.
      Process.sleep(50)

      %PetstoreClient.ApiResponse{
        status_code: 200,
        body: Jason.encode!(%{"access_token" => "shared-tok", "expires_in" => 3600})
      }
    end

    def call_count(%__MODULE__{agent: agent}) do
      Agent.get(agent, & &1)
    end
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

    test "invalidate_access_token forces refetch" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
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
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.invalidate_access_token(manager)
      second = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)

      assert first == "tok1"
      assert second == "tok2"
      assert FakeApiClient.call_count(fake_client) == 2
    end

    test "throws when no ApiClient injected" do
      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()

      assert_raise RuntimeError, fn ->
        PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(
          manager,
          "https://auth.example.com/token",
          %{"grant_type" => "client_credentials"}
        )
      end
    end

    test "10 concurrent get_access_token calls hit token endpoint exactly once" do
      counting_client = CountingApiClient.new()

      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_api_client(manager, counting_client)

      params = %{"grant_type" => "client_credentials"}
      token_url = "https://auth.example.com/token"

      tokens =
        1..10
        |> Enum.map(fn _ ->
          Task.async(fn ->
            PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)
          end)
        end)
        |> Enum.map(&Task.await(&1, 5_000))

      assert Enum.all?(tokens, &(&1 == "shared-tok"))
      assert CountingApiClient.call_count(counting_client) == 1
    end

    test "expires_in short-lived token does not storm" do
      # Gap CM: short-lived token (expires_in < buffer) must produce exactly
      # ONE network call from a single get_access_token invocation.
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "short", "expires_in" => 10})
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

      assert token == "short"
      assert FakeApiClient.call_count(fake_client) == 1
    end

    test "expires_in long-lived token applies full buffer" do
      # Gap CM: long-lived token gets full 30s buffer; second call uses cache.
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "long", "expires_in" => 3600})
          }
        ])

      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_api_client(manager, fake_client)

      params = %{"grant_type" => "client_credentials"}
      token_url = "https://auth.example.com/token"

      first = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)
      second = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)

      assert first == "long"
      assert second == "long"
      assert FakeApiClient.call_count(fake_client) == 1
    end

    test "expires_in exactly buffer returns zero buffer" do
      # Gap CM: expires_in == 30 collapses expiry to now, forcing refetch.
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "edge1", "expires_in" => 30})
          },
          %PetstoreClient.ApiResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "edge2", "expires_in" => 30})
          }
        ])

      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_api_client(manager, fake_client)

      params = %{"grant_type" => "client_credentials"}
      token_url = "https://auth.example.com/token"

      first = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)
      second = PetstoreClient.Auth.OAuth.OAuth2TokenManager.get_access_token(manager, token_url, params)

      assert first == "edge1"
      assert second == "edge2"
      assert FakeApiClient.call_count(fake_client) == 2
    end

    test "throws when token request fails" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiResponse{
            status_code: 401,
            body: Jason.encode!(%{"error" => "invalid_client"})
          }
        ])

      {:ok, manager} = PetstoreClient.Auth.OAuth.OAuth2TokenManager.start_link()
      PetstoreClient.Auth.OAuth.OAuth2TokenManager.set_api_client(manager, fake_client)

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
