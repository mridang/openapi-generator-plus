defmodule PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticatorTest do
  use ExUnit.Case, async: true

  defmodule FakeApiClient do
    defstruct [:agent]

    def new(responses) do
      {:ok, agent} =
        Agent.start_link(fn ->
          %{responses: responses, last_url: nil, last_body: nil, last_method: nil, get_count: 0}
        end)

      %__MODULE__{agent: agent}
    end

    # A queued exception is raised instead of answered, to simulate a
    # transport failure.
    def send_request(%__MODULE__{agent: agent}, method, url, _headers, body) do
      response =
        Agent.get_and_update(agent, fn state ->
          [response | rest] = state.responses
          get_count = if method == :get, do: state.get_count + 1, else: state.get_count

          new_state = %{
            state
            | responses: rest,
              last_url: url,
              last_body: body,
              last_method: method,
              get_count: get_count
          }

          {response, new_state}
        end)

      if is_exception(response), do: raise(response), else: response
    end

    def last_url(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_url)
    def last_body(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_body)
    def last_method(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.last_method)
    def get_count(%__MODULE__{agent: agent}), do: Agent.get(agent, & &1.get_count)

    # Queue one more response after the ones given to new/1.
    def push(%__MODULE__{agent: agent}, response) do
      Agent.update(agent, fn state -> %{state | responses: state.responses ++ [response]} end)
    end
  end

  # Records the options a discovery request is sent with, and answers with a valid document.
  defmodule DiscoveryOptionsApiClient do
    defstruct []

    def send_request(%__MODULE__{}, :get, _url, _headers, nil, opts) do
      send(self(), {:discovery_opts, opts})

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }
    end
  end

  defp create_authenticator do
    PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.new(
      "https://api.example.com",
      "https://auth.example.com/.well-known/openid-configuration",
      "my-client-id",
      "my-client-secret",
      "https://app.example.com/callback",
      ["openid", "profile"]
    )
  end

  defp strip_pkce(url) do
    Regex.replace(
      ~r/&code_challenge=[A-Za-z0-9_-]+&code_challenge_method=S256&nonce=[A-Za-z0-9_-]+\z/,
      url,
      ""
    )
  end

  describe "OpenIdConnectAuthenticator" do
    test "bhv authoidc 002 builds authorization URL from discovery" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com",
                "authorization_endpoint" => "https://auth.example.com/authorize",
                "token_endpoint" => "https://auth.example.com/token"
              })
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      url =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(
          auth,
          "my-state"
        )

      assert String.starts_with?(url, "https://auth.example.com/authorize?")
      assert String.contains?(url, "response_type=code")
      assert String.contains?(url, "client_id=my-client-id")
      assert String.contains?(url, "state=my-state")
    end

    test "bhv authoidc 022 openid is always requested, prepended once and de-duplicated case-sensitively" do
      for {given, expected} <- [
            {[], "openid"},
            {["profile"], "openid profile"},
            {["profile", "openid"], "openid profile"},
            {["openid", "openid"], "openid"},
            {["OpenID", "profile"], "openid OpenID profile"}
          ] do
        fake_client =
          FakeApiClient.new([
            %PetstoreClient.ApiHttpResponse{
              status_code: 200,
              body:
                Jason.encode!(%{
                  "issuer" => "https://auth.example.com",
                  "authorization_endpoint" => "https://auth.example.com/authorize",
                  "token_endpoint" => "https://auth.example.com/token"
                })
            }
          ])

        auth =
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.new(
            "https://api.example.com",
            "https://auth.example.com/.well-known/openid-configuration",
            "my-client-id",
            "my-client-secret",
            "https://app.example.com/callback",
            given
          )

        auth =
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

        url = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

        scope =
          url |> URI.parse() |> Map.fetch!(:query) |> URI.decode_query() |> Map.fetch!("scope")

        assert scope == expected
      end
    end

    test "bhv authoidc 018 empty state is omitted" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com",
                "authorization_endpoint" => "https://auth.example.com/authorize",
                "token_endpoint" => "https://auth.example.com/token"
              })
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      url = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth, "")

      assert String.starts_with?(url, "https://auth.example.com/authorize?")
      refute String.contains?(url, "state")
    end

    test "bhv authoidc 010 the discovery document is fetched from the issuer" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com",
                "authorization_endpoint" => "https://auth.example.com/authorize",
                "token_endpoint" => "https://auth.example.com/token"
              })
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

      assert FakeApiClient.last_method(fake_client) == :get

      assert FakeApiClient.last_url(fake_client) ==
               "https://auth.example.com/.well-known/openid-configuration"
    end

    test "bhv authoidc 017 obtains token after code exchange" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com",
                "authorization_endpoint" => "https://auth.example.com/authorize",
                "token_endpoint" => "https://auth.example.com/token"
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "oidc-tok", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")

      last_body = FakeApiClient.last_body(fake_client)
      assert String.contains?(last_body, "grant_type=authorization_code")
      assert String.contains?(last_body, "code=oidc-code")
    end

    test "bhv authoidc 013 get_auth_headers returns Bearer after exchange" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com",
                "authorization_endpoint" => "https://auth.example.com/authorize",
                "token_endpoint" => "https://auth.example.com/token"
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "oidc-tok", "expires_in" => 3600})
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "oidc-tok", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      auth = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
      headers = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.auth_headers(auth)

      assert headers["Authorization"] == "Bearer oidc-tok"
    end

    # set_api_client returns a copy for the client to hold; the caller's own
    # struct must still reach the injected ApiClient, and an exchange done on it
    # must ready the client's copy.
    test "bhv langelixir 014 exchange_code on the caller's copy readies the client's copy" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com",
                "authorization_endpoint" => "https://auth.example.com/authorize",
                "token_endpoint" => "https://auth.example.com/token"
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "oidc-tok", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      client_copy =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      _ = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
      headers = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.auth_headers(client_copy)

      assert headers["Authorization"] == "Bearer oidc-tok"
    end

    test "bhv authoidc 028 throws when no ApiClient injected" do
      auth = create_authenticator()

      err =
        assert_raise RuntimeError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end

      # A wrong call order is not an SDK error.
      refute PetstoreClient.Errors.OpenAPIError.open_api_error?(err)
    end

    # OIDC discovery is an HTTP call like any other: a 404 is a NotFoundError,
    # a transport failure propagates as the NetworkError / NetworkTimeoutError
    # it already is, and an unparseable document is a SerializationError.
    test "bhv authoidc 006 discovery failures are typed" do
      discover = fn response ->
        auth =
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
            create_authenticator(),
            FakeApiClient.new([response])
          )

        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      end

      err =
        assert_raise PetstoreClient.Errors.NotFoundError, fn ->
          discover.(%PetstoreClient.ApiHttpResponse{status_code: 404, body: "missing"})
        end

      assert err.status_code == 404
      assert PetstoreClient.Errors.ClientError.client_error?(err)

      err =
        assert_raise PetstoreClient.Errors.NetworkError, fn ->
          discover.(PetstoreClient.Errors.NetworkError.exception(message: "connection refused"))
        end

      assert err.status_code == 0

      assert_raise PetstoreClient.Errors.NetworkTimeoutError, fn ->
        discover.(PetstoreClient.Errors.NetworkTimeoutError.exception(message: "timed out"))
      end

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        discover.(%PetstoreClient.ApiHttpResponse{status_code: 200, body: "{not json"})
      end
    end

    # oauth-oidc-discovery-no-status-check: a non-2xx discovery response must
    # raise the typed ApiError for its status rather than surfacing as an
    # opaque "invalid JSON" parse error.
    test "bhv authoidc 033 a failed discovery request reports its method and path in the error" do
      fake_client =
        FakeApiClient.new([%PetstoreClient.ApiHttpResponse{status_code: 404, body: "missing"}])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      error =
        assert_raise PetstoreClient.Errors.NotFoundError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end

      assert error.request_method == "GET"
      assert error.request_path == "/.well-known/openid-configuration"
    end

    test "bhv authoidc 027 raises on non-2xx discovery response instead of parsing it as JSON" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 500,
            body: "<html><body>Internal Server Error</body></html>"
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      err =
        assert_raise PetstoreClient.Errors.InternalServerError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end

      assert err.status_code == 500
    end

    # oauth-oidc-missing-endpoint-guard: a discovery document missing the
    # authorization/token endpoint must raise rather than silently building
    # a delegate with empty endpoint URLs.
    test "bhv authoidc 025 raises when discovery document is missing authorization_endpoint" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"token_endpoint" => "https://auth.example.com/token"})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      # An incomplete discovery document is a SerializationError.
      assert_raise PetstoreClient.Errors.SerializationError,
                   ~r/OIDC discovery document is missing 'authorization_endpoint'/,
                   fn ->
                     PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(
                       auth
                     )
                   end
    end

    test "bhv authoidc 026 raises when discovery document is missing token_endpoint" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{"authorization_endpoint" => "https://auth.example.com/authorize"})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      assert_raise PetstoreClient.Errors.SerializationError, ~r/token_endpoint/, fn ->
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      end
    end

    test "bhv authoidc 032 a discovered endpoint that is not https is rejected" do
      document = %{
        "issuer" => "https://auth.example.com",
        "authorization_endpoint" => "https://auth.example.com/authorize",
        "token_endpoint" => "http://auth.example.com/token"
      }

      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{status_code: 200, body: Jason.encode!(document)}
        ])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      assert_raise PetstoreClient.Errors.SerializationError,
                   ~r/'token_endpoint' must be an https URL/,
                   fn ->
                     PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(
                       auth
                     )
                   end
    end

    test "bhv authoidc 005 a plain-http endpoint is refused unless it is loopback" do
      refuse = fn doc, pattern ->
        fake_client =
          FakeApiClient.new([
            %PetstoreClient.ApiHttpResponse{status_code: 200, body: Jason.encode!(doc)}
          ])

        auth =
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
            create_authenticator(),
            fake_client
          )

        assert_raise PetstoreClient.Errors.SerializationError, pattern, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end
      end

      good = %{
        "issuer" => "https://auth.example.com",
        "authorization_endpoint" => "https://auth.example.com/authorize",
        "token_endpoint" => "https://auth.example.com/token"
      }

      refuse.(
        %{good | "token_endpoint" => "http://auth.example.com/token"},
        ~r/'token_endpoint' must be an https URL/
      )

      refuse.(
        %{good | "authorization_endpoint" => "http://auth.example.com/authorize"},
        ~r/'authorization_endpoint' must be an https URL/
      )
    end

    test "bhv authoidc 024 throws when the discovery issuer does not match the discovery url" do
      for doc <- [
            %{
              "issuer" => "https://evil.example.com",
              "authorization_endpoint" => "https://auth.example.com/authorize",
              "token_endpoint" => "https://auth.example.com/token"
            },
            %{
              "authorization_endpoint" => "https://auth.example.com/authorize",
              "token_endpoint" => "https://auth.example.com/token"
            }
          ] do
        fake_client =
          FakeApiClient.new([
            %PetstoreClient.ApiHttpResponse{status_code: 200, body: Jason.encode!(doc)}
          ])

        auth =
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
            create_authenticator(),
            fake_client
          )

        assert_raise PetstoreClient.Errors.SerializationError, ~r/'issuer' does not match/, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end
      end
    end

    test "bhv authoidc 007 a 404 discovery response raises not found" do
      fake_client =
        FakeApiClient.new([%PetstoreClient.ApiHttpResponse{status_code: 404, body: "missing"}])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      err =
        assert_raise PetstoreClient.Errors.NotFoundError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end

      assert err.status_code == 404
    end

    test "bhv misc 070 a discovery document that is not a JSON object keeps no cause and does not repeat the body" do
      fake_client =
        FakeApiClient.new([%PetstoreClient.ApiHttpResponse{status_code: 200, body: "[1, 2]"}])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end

      assert err.message == "OIDC discovery document is not a JSON object"
      assert Map.get(err, :cause) == nil
      refute err.message =~ "[1, 2]"
    end

    test "bhv misc 078 an OpenID Connect authorization URL carries an S256 PKCE challenge and a nonce" do
      discovery =
        Jason.encode!(%{
          "issuer" => "https://auth.example.com",
          "authorization_endpoint" => "https://auth.example.com/authorize",
          "token_endpoint" => "https://auth.example.com/token"
        })

      fake_client =
        FakeApiClient.new([%PetstoreClient.ApiHttpResponse{status_code: 200, body: discovery}])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      url = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

      assert url =~ "code_challenge_method=S256"
      assert url =~ ~r/&nonce=[A-Za-z0-9_-]{43}\z/
    end

    test "bhv authoidc 016 a malformed discovery document raises serialization error" do
      fake_client =
        FakeApiClient.new([%PetstoreClient.ApiHttpResponse{status_code: 200, body: "[1, 2]"}])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      assert_raise PetstoreClient.Errors.SerializationError, ~r/not a JSON object/, fn ->
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      end
    end

    test "bhv authoidc 023 the redacted repr shows the non-secret fields" do
      repr = inspect(create_authenticator())

      assert repr =~ "https://auth.example.com/.well-known/openid-configuration"
      assert repr =~ ~s(client_id: "my-client-id")
      refute repr =~ "my-client-secret"
    end

    test "bhv authoidc 004 a trailing slash on the issuer and a loopback http endpoint are accepted" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com/",
                "authorization_endpoint" => "http://127.0.0.1:8080/authorize",
                "token_endpoint" => "http://localhost:8080/token"
              })
          }
        ])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth) =~
               "http://127.0.0.1:8080/authorize"
    end

    test "bhv authoidc 008 does not send the transport default headers to the discovery endpoint" do
      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          %DiscoveryOptionsApiClient{}
        )

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth) =~
               "https://auth.example.com/authorize"

      assert_received {:discovery_opts, opts}
      assert opts[:no_default_headers] == true
    end

    test "bhv authoidc 014 get_host returns configured host" do
      auth = create_authenticator()

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.host(auth) ==
               "https://api.example.com"
    end

    test "bhv authoidc 015 invalidate_access_token reaches the discovered flow and is a no-op before a code exchange" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client =
        FakeApiClient.new([
          discovery,
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
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.invalidate_access_token(auth) ==
               :ok

      auth = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.auth_headers(auth)[
               "Authorization"
             ] ==
               "Bearer tok1"

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.invalidate_access_token(auth) ==
               :ok

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.auth_headers(auth)[
               "Authorization"
             ] ==
               "Bearer tok2"

      assert FakeApiClient.last_body(fake_client) =~ "grant_type=refresh_token"
    end

    test "bhv authoidc 019 redacts secret" do
      auth = create_authenticator()

      refute inspect(auth) =~ "my-client-secret"
      assert inspect(auth) =~ ~s(client_secret: "***")
    end

    # An expired discovery document is fetched again, but the token state lives
    # on across it: a code exchanged before the expiry is still exchanged after.
    test "bhv authoidc 020 rediscovery after expiry keeps the exchanged tokens" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        headers: %{"Cache-Control" => "max-age=0"},
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client =
        FakeApiClient.new([
          discovery,
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "oidc-tok", "expires_in" => 3600})
          },
          discovery
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      auth = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
      headers = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.auth_headers(auth)

      assert FakeApiClient.get_count(fake_client) == 2
      assert headers["Authorization"] == "Bearer oidc-tok"
    end

    # The PKCE verifier lives in the token manager the rediscovery keeps, so a
    # code exchanged after the discovery document expired still proves the
    # challenge of the authorization URL built before it.
    test "bhv authoidc 021 the PKCE verifier survives a rediscovery and the URL carries a nonce" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        headers: %{"Cache-Control" => "max-age=0"},
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client =
        FakeApiClient.new([
          discovery,
          discovery,
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body: Jason.encode!(%{"access_token" => "oidc-tok", "expires_in" => 3600})
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      params =
        auth
        |> PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url("s")
        |> URI.parse()
        |> Map.fetch!(:query)
        |> URI.decode_query()

      assert params["code_challenge_method"] == "S256"
      assert byte_size(params["nonce"]) == 43

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")

      assert FakeApiClient.get_count(fake_client) == 2

      verifier =
        FakeApiClient.last_body(fake_client) |> URI.decode_query() |> Map.fetch!("code_verifier")

      assert byte_size(verifier) == 43

      assert params["code_challenge"] ==
               Base.url_encode64(:crypto.hash(:sha256, verifier), padding: false)
    end

    # The discovery document is cached for the max-age of its Cache-Control
    # header, and for the default when the header is absent or has no usable
    # max-age.
    test "bhv authoidc 003 discovery is cached when Cache-Control carries a positive max-age or none" do
      for headers <- [
            %{"Cache-Control" => "max-age=3600"},
            %{"cache-control" => "public, MAX-AGE=3600"},
            %{"Cache-Control" => "no-cache"},
            %{"Cache-Control" => "max-age=soon"},
            %{}
          ] do
        discovery = %PetstoreClient.ApiHttpResponse{
          status_code: 200,
          headers: headers,
          body:
            Jason.encode!(%{
              "issuer" => "https://auth.example.com",
              "authorization_endpoint" => "https://auth.example.com/authorize",
              "token_endpoint" => "https://auth.example.com/token"
            })
        }

        fake_client = FakeApiClient.new([discovery, discovery])

        auth = create_authenticator()

        auth =
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

        assert FakeApiClient.get_count(fake_client) == 1,
               "discovery refetched for #{inspect(headers)}"
      end
    end

    test "bhv authoidc 009 a failed rediscovery keeps using the previous discovery document" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        headers: %{"Cache-Control" => "max-age=0"},
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client =
        FakeApiClient.new([
          discovery,
          %PetstoreClient.ApiHttpResponse{status_code: 503, body: "unavailable"}
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      first = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      second = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

      assert strip_pkce(second) == strip_pkce(first)
      assert FakeApiClient.get_count(fake_client) == 2
    end

    test "bhv authoidc 031 a cached discovery document is fetched again once its max-age has elapsed" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        headers: %{"Cache-Control" => "max-age=1"},
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client = FakeApiClient.new([discovery, discovery])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      assert FakeApiClient.get_count(fake_client) == 1

      Process.sleep(1_100)

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      assert FakeApiClient.get_count(fake_client) == 2
    end

    test "bhv authoidc 001 an oversized max-age is clamped to one year" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        headers: %{"Cache-Control" => "max-age=99999999999999"},
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client = FakeApiClient.new([discovery])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      before = System.monotonic_time(:millisecond)

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

      {_delegate, expiry} = Agent.get(auth.discovery_cache, & &1.discovery)
      assert expiry <= before + 31_536_000 * 1000 + 5_000
      assert expiry > before + 31_536_000 * 1000 - 5_000
    end

    test "bhv misc 199 discovery runs again once the cached document's max-age has elapsed and the rediscovered endpoints are used" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        headers: %{"Cache-Control" => "max-age=1"},
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client = FakeApiClient.new([discovery, discovery])
      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      Process.sleep(1_100)
      url = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

      assert FakeApiClient.get_count(fake_client) == 2
      assert url =~ "https://auth.example.com/authorize"
    end

    test "bhv misc 200 a discovery document whose issuer names another server is rejected" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body:
          Jason.encode!(%{
            "issuer" => "https://evil.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client = FakeApiClient.new([discovery])
      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      assert_raise PetstoreClient.Errors.SerializationError, ~r/'issuer' does not match/, fn ->
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      end
    end

    test "bhv misc 201 a failed discovery request reports its request path without the query string" do
      fake_client =
        FakeApiClient.new([%PetstoreClient.ApiHttpResponse{status_code: 404, body: "missing"}])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      error =
        assert_raise PetstoreClient.Errors.NotFoundError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end

      assert error.request_path == "/.well-known/openid-configuration"
      refute error.request_path =~ "?"
    end

    test "bhv authoidc 030 max-age=0 makes every use fetch the discovery document again" do
      discovery = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        headers: %{"Cache-Control" => "max-age=0"},
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client = FakeApiClient.new([discovery, discovery])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

      assert FakeApiClient.get_count(fake_client) == 2
    end

    # The discovery document must be cached: a second build should reuse it
    # rather than issue a second GET to the discovery endpoint.
    test "bhv authoidc 029 concurrent callers on a cold authenticator share one discovery request" do
      document = %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body:
          Jason.encode!(%{
            "issuer" => "https://auth.example.com",
            "authorization_endpoint" => "https://auth.example.com/authorize",
            "token_endpoint" => "https://auth.example.com/token"
          })
      }

      fake_client = FakeApiClient.new(List.duplicate(document, 8))
      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      1..8
      |> Enum.map(fn _ ->
        Task.async(fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        end)
      end)
      |> Enum.each(&Task.await/1)

      assert FakeApiClient.get_count(fake_client) == 1
    end

    test "bhv authoidc 011 fetches discovery document only once" do
      fake_client =
        FakeApiClient.new([
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com",
                "authorization_endpoint" => "https://auth.example.com/authorize",
                "token_endpoint" => "https://auth.example.com/token"
              })
          },
          %PetstoreClient.ApiHttpResponse{
            status_code: 200,
            body:
              Jason.encode!(%{
                "issuer" => "https://auth.example.com",
                "authorization_endpoint" => "https://auth.example.com/authorize",
                "token_endpoint" => "https://auth.example.com/token"
              })
          }
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

      assert FakeApiClient.get_count(fake_client) == 1
    end

    test "bhv auth 011 a discovery response without a usable max-age is cached for the default lifetime" do
      for headers <- [%{}, %{"Cache-Control" => "no-cache"}, %{"Cache-Control" => "max-age=soon"}] do
        discovery = %PetstoreClient.ApiHttpResponse{
          status_code: 200,
          headers: headers,
          body:
            Jason.encode!(%{
              "issuer" => "https://auth.example.com",
              "authorization_endpoint" => "https://auth.example.com/authorize",
              "token_endpoint" => "https://auth.example.com/token"
            })
        }

        fake_client = FakeApiClient.new([discovery, discovery])

        auth = create_authenticator()

        auth =
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth)

        assert FakeApiClient.get_count(fake_client) == 1,
               "discovery refetched for #{inspect(headers)}"
      end
    end

    test "bhv auth 029 after the cached discovery document expires, later calls use the endpoints of the newly fetched document" do
      discovery = fn max_age, endpoint ->
        %PetstoreClient.ApiHttpResponse{
          status_code: 200,
          headers: %{"Cache-Control" => "max-age=#{max_age}"},
          body:
            Jason.encode!(%{
              "issuer" => "https://auth.example.com",
              "authorization_endpoint" => endpoint,
              "token_endpoint" => "https://auth.example.com/token"
            })
        }
      end

      fake_client =
        FakeApiClient.new([
          discovery.(1, "https://auth.example.com/authorize-v1"),
          discovery.(3600, "https://auth.example.com/authorize-v2")
        ])

      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(auth, fake_client)

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth) =~
               "https://auth.example.com/authorize-v1?"

      Process.sleep(1_100)

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth) =~
               "https://auth.example.com/authorize-v2?"
    end

    test "bhv authoidc 034 each authorization url carries a fresh nonce" do
      fake_client = FakeApiClient.new([issuer_discovery()])

      auth =
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
          create_authenticator(),
          fake_client
        )

      first =
        nonce_of(
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth, "s")
        )

      second =
        nonce_of(
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth, "s")
        )

      assert first =~ ~r/\A[A-Za-z0-9_-]{43}\z/
      refute first == second
    end

    test "bhv authoidc 035 an id_token with valid claims is accepted" do
      auth = authenticator_returning(&id_token(claims(&1, %{"aud" => ["other", "my-client-id"]})))

      auth = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")

      assert PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.auth_headers(auth)[
               "Authorization"
             ] == "Bearer oidc-tok"
    end

    test "bhv authoidc 036 an id_token with another nonce is rejected" do
      auth = authenticator_returning(&id_token(claims(&1, %{"nonce" => "replayed"})))

      error =
        assert_raise PetstoreClient.Errors.OAuth2TokenError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
        end

      assert Exception.message(error) ==
               "The id_token nonce does not match the authorization request"
    end

    test "bhv authoidc 037 an id_token from another issuer is rejected" do
      auth =
        authenticator_returning(&id_token(claims(&1, %{"iss" => "https://evil.example.com"})))

      error =
        assert_raise PetstoreClient.Errors.OAuth2TokenError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
        end

      assert Exception.message(error) ==
               "The id_token issuer does not match the discovered issuer"
    end

    test "bhv authoidc 038 an id_token for another audience is rejected" do
      auth = authenticator_returning(&id_token(claims(&1, %{"aud" => "other-client"})))

      error =
        assert_raise PetstoreClient.Errors.OAuth2TokenError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
        end

      assert Exception.message(error) == "The id_token audience does not contain the client id"
    end

    test "bhv authoidc 039 an expired id_token is rejected" do
      expired = System.os_time(:second) - 120
      auth = authenticator_returning(&id_token(claims(&1, %{"exp" => expired})))

      error =
        assert_raise PetstoreClient.Errors.OAuth2TokenError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
        end

      assert Exception.message(error) == "The id_token has expired"
    end

    test "bhv authoidc 040 a malformed id_token is rejected" do
      auth = authenticator_returning(fn _nonce -> "not-a-jwt" end)

      error =
        assert_raise PetstoreClient.Errors.OAuth2TokenError, fn ->
          PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
        end

      assert Exception.message(error) == "The id_token is malformed"
    end

    test "bhv authoidc 041 a rejected id_token leaves no session" do
      auth = authenticator_returning(&id_token(claims(&1, %{"nonce" => "replayed"})))

      assert_raise PetstoreClient.Errors.OAuth2TokenError, fn ->
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.exchange_code(auth, "oidc-code")
      end

      assert_raise RuntimeError, "Must call exchange_code() before making API requests", fn ->
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.auth_headers(auth)
      end
    end
  end

  defp issuer_discovery do
    %PetstoreClient.ApiHttpResponse{
      status_code: 200,
      body:
        Jason.encode!(%{
          "issuer" => "https://auth.example.com",
          "authorization_endpoint" => "https://auth.example.com/authorize",
          "token_endpoint" => "https://auth.example.com/token"
        })
    }
  end

  defp nonce_of(url),
    do: url |> URI.parse() |> Map.fetch!(:query) |> URI.decode_query() |> Map.fetch!("nonce")

  # An unsigned compact JWT whose payload is the given claims.
  defp id_token(claims) do
    Base.url_encode64(~s({"alg":"none"}), padding: false) <>
      "." <> Base.url_encode64(Jason.encode!(claims), padding: false) <> ".sig"
  end

  # Claims that pass every check for the given nonce, with the overrides applied.
  defp claims(nonce, overrides) do
    Map.merge(
      %{
        "iss" => "https://auth.example.com",
        "aud" => "my-client-id",
        "exp" => System.os_time(:second) + 3600,
        "nonce" => nonce
      },
      overrides
    )
  end

  # An authenticator that has built an authorization URL and whose token
  # endpoint answers with the id_token built from that URL's nonce.
  defp authenticator_returning(id_token_for_nonce) do
    fake_client = FakeApiClient.new([issuer_discovery()])

    auth =
      PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
        create_authenticator(),
        fake_client
      )

    nonce =
      nonce_of(
        PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(auth, "s")
      )

    FakeApiClient.push(fake_client, %PetstoreClient.ApiHttpResponse{
      status_code: 200,
      body:
        Jason.encode!(%{
          "access_token" => "oidc-tok",
          "expires_in" => 3600,
          "id_token" => id_token_for_nonce.(nonce)
        })
    })

    auth
  end
end
