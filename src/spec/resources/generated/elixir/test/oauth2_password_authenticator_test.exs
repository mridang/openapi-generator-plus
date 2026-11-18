defmodule PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticatorTest do
  use ExUnit.Case, async: true

  defmodule FakeApiClient do
    defstruct [:agent]

    def new(responses) do
      {:ok, agent} =
        Agent.start_link(fn ->
          %{responses: responses, last_url: nil, last_body: nil, last_headers: nil, requests: []}
        end)

      %__MODULE__{agent: agent}
    end

    def send_request(%__MODULE__{agent: agent}, _method, url, headers, body) do
      Agent.get_and_update(agent, fn state ->
        [response | rest] = state.responses

        new_state = %{
          state
          | responses: rest,
            last_url: url,
            last_body: body,
            last_headers: headers,
            requests: state.requests ++ [{url, body}]
        }

        {response, new_state}
      end)
    end

    def last_url(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_url)
    def last_body(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_body)
    def requests(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.requests)
    def last_headers(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_headers)
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
    test "bhv authscheme 014 an empty environment variable for the password scheme credential is treated as unset and fails naming the variable" do
      name = "SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME"
      on_exit(fn -> System.delete_env(name) end)
      System.put_env(name, "")

      assert_raise ArgumentError,
                   "Missing the credential 'username': pass it, or set the environment variable #{name}",
                   fn ->
                     PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
                       "https://api.example.com",
                       "my-client-id",
                       "my-client-secret",
                       "https://auth.example.com/token",
                       nil,
                       "testpass",
                       ["read", "write"]
                     )
                   end
    end

    test "bhv authoauthtwopassword 012 scheme authenticator keeps the declared scopes" do
      auth =
        PetstoreClient.Auth.OAuth.LegacyAuthPasswordAuthenticator.new(
          "https://api.example.com",
          "my-client-id",
          "my-client-secret",
          "alice",
          "s3cret"
        )

      assert auth.scopes == ["read"]
    end

    # The authenticator generated for the spec's password scheme passes its
    # token and refresh URLs to the flow in the right places.
    test "scheme authenticator keeps the credentials and the declared URLs" do
      auth =
        PetstoreClient.Auth.OAuth.LegacyAuthPasswordAuthenticator.new(
          "https://api.example.com",
          "my-client-id",
          "my-client-secret",
          "alice",
          "s3cret"
        )

      assert auth.username == "alice"
      assert auth.password == "s3cret"
      assert auth.token_url == "https://auth.example.com/oauth/token"
      assert auth.refresh_url == "https://auth.example.com/oauth/refresh"
    end

    test "bhv authoauthtwopassword 014 sends password grant type" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "grant_type=password")
    end

    test "bhv authoauthtwopassword 016 sends username and password" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "username=testuser")
      assert String.contains?(last_body, "password=testpass")
    end

    test "bhv authoauthtwopassword 013 sends client id and secret" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "client_id=my-client-id")
      assert String.contains?(last_body, "client_secret=my-client-secret")
    end

    test "bhv authoauthtwopassword 011 returns authorization bearer header" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok-pwd", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      headers = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      assert headers["Authorization"] == "Bearer tok-pwd"
    end

    test "bhv authoauthtwopassword 019 uses refresh token on subsequent calls" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 0
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      # First call uses password grant
      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      # Second call should use refresh_token grant since token is expired
      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "grant_type=refresh_token")
      assert String.contains?(last_body, "refresh_token=ref1")
    end

    test "bhv authoauthtwopassword 009 refresh sends the client credentials in the body" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 0
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      assert URI.decode_query(FakeApiClient.last_body(fake_client)) == %{
               "grant_type" => "refresh_token",
               "refresh_token" => "ref1",
               "client_id" => "my-client-id",
               "client_secret" => "my-client-secret"
             }
    end

    test "bhv authoauthtwopassword 008 refresh with basic client authentication keeps the credentials out of the body" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 0
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
          "https://api.example.com",
          "my-client-id",
          "my-client-secret",
          "https://auth.example.com/token",
          "testuser",
          "testpass",
          ["read"],
          client_auth_method: :basic
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      assert URI.decode_query(FakeApiClient.last_body(fake_client)) == %{
               "grant_type" => "refresh_token",
               "refresh_token" => "ref1"
             }

      "Basic " <> encoded = FakeApiClient.last_headers(fake_client)["Authorization"]
      assert Base.decode64!(encoded) == "my-client-id:my-client-secret"
    end

    test "bhv authoauthtwopassword 022 the generated scheme wrapper sends the first grant to the token URL and the refresh grant to the refresh URL" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 0
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.LegacyAuthPasswordAuthenticator.new(
          "https://api.example.com",
          "my-client-id",
          "my-client-secret",
          "alice",
          "s3cret"
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      assert FakeApiClient.last_url(fake_client) == "https://auth.example.com/oauth/token"

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      assert FakeApiClient.last_url(fake_client) == "https://auth.example.com/oauth/refresh"
    end

    test "bhv authoauthtwopassword 017 the first grant goes to the token URL and the refresh to the refresh URL" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 0
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
          "https://api.example.com",
          "my-client-id",
          "my-client-secret",
          "https://auth.example.com/token",
          "testuser",
          "testpass",
          ["read"],
          refresh_url: "https://auth.example.com/refresh"
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      assert FakeApiClient.last_url(fake_client) == "https://auth.example.com/token"

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      assert FakeApiClient.last_url(fake_client) == "https://auth.example.com/refresh"
    end

    test "bhv authoauthtwopassword 006 password sends the refresh grant to the refresh url and falls back to the token url" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 0
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
          "https://api.example.com",
          "my-client-id",
          "my-client-secret",
          "https://auth.example.com/token",
          "testuser",
          "testpass",
          ["read"]
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      assert FakeApiClient.last_url(fake_client) == "https://auth.example.com/token"
      assert String.contains?(FakeApiClient.last_body(fake_client), "grant_type=refresh_token")
    end

    test "bhv authoauthtwopassword 010 a rejected refresh re-runs the password grant at the token URL" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 0
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 400,
            body: Jason.encode!(%{"error" => "invalid_grant"})
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok3", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
          "https://api.example.com",
          "my-client-id",
          "my-client-secret",
          "https://auth.example.com/token",
          "testuser",
          "testpass",
          ["read"],
          refresh_url: "https://auth.example.com/refresh"
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      headers = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      assert headers["Authorization"] == "Bearer tok3"

      assert [_first, {refresh_url, refresh}, {retry_url, retry}] =
               FakeApiClient.requests(fake_client)

      assert refresh_url == "https://auth.example.com/refresh"
      assert URI.decode_query(refresh)["grant_type"] == "refresh_token"
      assert retry_url == "https://auth.example.com/token"
      assert URI.decode_query(retry)["grant_type"] == "password"
    end

    test "bhv authoauthtwopassword 021 password grant and refresh bodies are in the fixed order" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 0
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)
      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      assert [{_, grant}, {_, refresh}] = FakeApiClient.requests(fake_client)

      assert grant ==
               "grant_type=password&username=testuser&password=testpass" <>
                 "&client_id=my-client-id&client_secret=my-client-secret&scope=read+write"

      assert refresh ==
               "grant_type=refresh_token&refresh_token=ref1&client_id=my-client-id&client_secret=my-client-secret"
    end

    test "bhv authoauthtwopassword 015 the password grant sends scopes with + for the space and percent-encodes * ! ' ( )" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
          "https://api.example.com",
          "id~*!'()",
          "my-client-secret",
          "https://auth.example.com/token",
          "testuser",
          "testpass",
          ["read", "write"]
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "scope=read+write")
      assert String.contains?(last_body, "client_id=id~%2A%21%27%28%29")
    end

    test "bhv authoauthtwopassword 005 keeps ~ literal and percent-encodes * ! ' ( ) in the token request body" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "at", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
          "https://api.example.com",
          "id~*!'()",
          "secret~*!'()",
          "https://auth.example.com/token",
          "user~*!'()",
          "pass~*!'()",
          ["read"]
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "client_id=id~%2A%21%27%28%29")
      assert String.contains?(last_body, "client_secret=secret~%2A%21%27%28%29")
      assert String.contains?(last_body, "password=pass~%2A%21%27%28%29")
    end

    test "bhv authoauthtwopassword 001 basic auth percent-encodes * ! ' ( ) and keeps ~ literal" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "at", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
          "https://api.example.com",
          "id~*!'()",
          "secret~*!'()",
          "https://auth.example.com/token",
          "testuser",
          "testpass",
          ["read"],
          client_auth_method: :basic
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      "Basic " <> encoded = FakeApiClient.last_headers(fake_client)["Authorization"]
      assert Base.decode64!(encoded) == "id~%2A%21%27%28%29:secret~%2A%21%27%28%29"
    end

    test "bhv authoauthtwopassword 003 get_host returns configured host" do
      auth = create_authenticator()

      assert PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.host(auth) ==
               "https://api.example.com"
    end

    test "bhv authoauthtwopassword 002 basic auth URL-encodes client id and secret" do
      # Gap R: RFC 6749 §2.3.1 — when using client_secret_basic, both
      # client_id and client_secret MUST be application/x-www-form-
      # urlencoded BEFORE being joined with ':' and base64-encoded.
      # Verifies a client_id with `+` and a secret with `&` are encoded
      # (not raw) before the colon-join + base64.
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "at", "expires_in" => 3600})
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
          "https://api.example.com",
          "id+with/special",
          "secret&with=stuff",
          "https://auth.example.com/token",
          "testuser",
          "testpass",
          ["read"],
          client_auth_method: :basic
        )

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      headers = FakeApiClient.last_headers(fake_client)
      auth_header = headers["Authorization"]
      assert auth_header != nil
      assert String.starts_with?(auth_header, "Basic ")
      encoded = String.slice(auth_header, String.length("Basic ")..-1//1)
      decoded = Base.decode64!(encoded)
      # Expected: form-urlencoded id ':' form-urlencoded secret
      assert decoded == "id%2Bwith%2Fspecial:secret%26with%3Dstuff"
    end

    test "bhv authoauthtwopassword 004 invalidate_access_token drops the access token and keeps the refresh token" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "access_token" => "tok1",
                "refresh_token" => "ref1",
                "expires_in" => 3600
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok2", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      assert PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)[
               "Authorization"
             ] ==
               "Bearer tok1"

      assert PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.invalidate_access_token(auth) ==
               :ok

      assert PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)[
               "Authorization"
             ] ==
               "Bearer tok2"

      assert [{_, first}, {_, second}] = FakeApiClient.requests(fake_client)
      assert first =~ "grant_type=password"
      assert second =~ "grant_type=refresh_token"
      assert second =~ "refresh_token=ref1"
    end

    test "bhv authoauthtwopassword 007 redacts secret" do
      auth = create_authenticator()

      refute inspect(auth) =~ "my-client-secret"
      refute inspect(auth) =~ "testpass"
      assert inspect(auth) =~ ~s(client_secret: "***")
      assert inspect(auth) =~ ~s(password: "***")
    end

    test "bhv authoauthtwopassword 018 the redacted repr shows the non-secret fields" do
      shown = create_authenticator() |> inspect()

      assert shown =~ ~s(client_id: "my-client-id")
      assert shown =~ ~s(token_url: "https://auth.example.com/token")
      assert shown =~ ~s(username: "testuser")
    end

    test "bhv auth 051 the password grant posts to the declared token URL with the declared scopes" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "tok1", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.auth_headers(auth)

      assert FakeApiClient.last_url(fake_client) == "https://auth.example.com/token"
      assert String.contains?(FakeApiClient.last_body(fake_client), "grant_type=password")
      assert String.contains?(FakeApiClient.last_body(fake_client), "scope=read+write")
    end
  end
end
