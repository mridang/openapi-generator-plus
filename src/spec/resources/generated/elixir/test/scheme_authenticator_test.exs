defmodule PetstoreClient.SchemeAuthenticatorTest do
  use ExUnit.Case, async: false

  @host "https://api.example.com"

  # The authenticator of each security scheme and, for each credential it takes,
  # the argument name and the environment variable it falls back to.
  @schemes [
    {PetstoreClient.Auth.PetStoreBasicAuthenticator,
     [
       {"username", "SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME"},
       {"password", "SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD"}
     ]},
    {PetstoreClient.Auth.AdminBasicAuthenticator,
     [
       {"username", "SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME"},
       {"password", "SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD"}
     ]},
    {PetstoreClient.Auth.PetStoreBearerAuthenticator,
     [{"token", "SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN"}]},
    {PetstoreClient.Auth.ServiceTokenAuthenticator,
     [{"token", "SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN"}]},
    {PetstoreClient.Auth.ApiKeyHeaderAuthenticator,
     [{"api_key", "SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY"}]},
    {PetstoreClient.Auth.InternalApiKeyAuthenticator,
     [{"api_key", "SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY"}]},
    {PetstoreClient.Auth.ApiKeyQueryAuthenticator,
     [{"api_key", "SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY"}]},
    {PetstoreClient.Auth.SessionCookieAuthenticator,
     [{"api_key", "SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY"}]},
    {PetstoreClient.Auth.OAuth.MachineAuthClientCredentialsAuthenticator,
     [
       {"client_id", "SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID"},
       {"client_secret", "SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET"}
     ]},
    {PetstoreClient.Auth.OAuth.LegacyAuthPasswordAuthenticator,
     [
       {"client_id", "SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID"},
       {"client_secret", "SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET"},
       {"username", "SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME"},
       {"password", "SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD"}
     ]},
    {PetstoreClient.Auth.OAuth.UserAuthAuthorizationCodeAuthenticator,
     [
       {"client_id", "SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID"},
       {"client_secret", "SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET"},
       {"redirect_uri", "SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI"}
     ]},
    {PetstoreClient.Auth.OAuth.BrowserAuthImplicitAuthenticator,
     [{"client_id", "SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID"}]},
    {PetstoreClient.Auth.OAuth.SsoAuthAuthenticator,
     [
       {"client_id", "SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID"},
       {"client_secret", "SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET"},
       {"redirect_uri", "SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI"}
     ]}
  ]

  # The basic schemes are the ones whose credentials are exactly a username and a password.
  @basic_schemes for {module, [{"username", user_env}, {"password", pass_env}]} <- @schemes,
                     do: {module, user_env, pass_env}

  setup do
    names = for {_module, vars} <- @schemes, {_arg, env} <- vars, do: env
    saved = Map.new(names, fn name -> {name, System.get_env(name)} end)
    Enum.each(names, &System.delete_env/1)

    on_exit(fn ->
      Enum.each(saved, fn
        {name, nil} -> System.delete_env(name)
        {name, value} -> System.put_env(name, value)
      end)
    end)

    :ok
  end

  defp build(module, args), do: apply(module, :new, args)

  # Records the URL a discovery request is sent to, and answers with a valid document.
  defmodule DiscoveryRecorder do
    defstruct []

    def send_request(%__MODULE__{}, :get, url, _headers, nil) do
      send(self(), {:discovered, url})

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

  test "bhv authoidc 012 an OpenID Connect scheme wrapper discovers from its spec url and sends the openid scope" do
    for {module, vars} <- @schemes do
      auth = build(module, [@host | Enum.map(vars, fn {arg, _env} -> "explicit-" <> arg end)])

      case auth do
        %{__struct__: PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator} ->
          authenticator =
            PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.set_api_client(
              auth,
              %DiscoveryRecorder{}
            )

          url =
            PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.build_authorization_url(
              authenticator
            )

          assert_received {:discovered, discovered_url}
          assert discovered_url == auth.openid_connect_url
          assert url =~ "scope=openid"

        _other ->
          :ok
      end
    end
  end

  for {module, vars} <- @schemes do
    describe "#{inspect(module)}" do
      @module module
      @vars vars

      test "bhv authscheme 001 builds from its environment variables alone" do
        Enum.each(@vars, fn {arg, env} -> System.put_env(env, "from-env-" <> arg) end)

        auth = build(@module, [@host])

        for {arg, _env} <- @vars do
          assert Map.fetch!(auth, String.to_atom(arg)) == "from-env-" <> arg
        end
      end

      test "an argument that is passed wins over its environment variable" do
        Enum.each(@vars, fn {arg, env} -> System.put_env(env, "from-env-" <> arg) end)
        [{first, _env} | rest] = @vars

        auth = build(@module, [@host, "explicit-" <> first | Enum.map(rest, fn _ -> nil end)])

        assert Map.fetch!(auth, String.to_atom(first)) == "explicit-" <> first

        for {arg, _env} <- rest do
          assert Map.fetch!(auth, String.to_atom(arg)) == "from-env-" <> arg
        end
      end

      test "bhv authscheme 002 explicit arguments need no environment" do
        args = Enum.map(@vars, fn {arg, _env} -> "explicit-" <> arg end)

        auth = build(@module, [@host | args])

        for {arg, _env} <- @vars do
          assert Map.fetch!(auth, String.to_atom(arg)) == "explicit-" <> arg
        end
      end

      test "the environment is read once, when the authenticator is built" do
        Enum.each(@vars, fn {arg, env} -> System.put_env(env, "first-" <> arg) end)

        auth = build(@module, [@host])
        Enum.each(@vars, fn {arg, env} -> System.put_env(env, "second-" <> arg) end)

        for {arg, _env} <- @vars do
          assert Map.fetch!(auth, String.to_atom(arg)) == "first-" <> arg
        end
      end

      test "a credential found nowhere raises and names its variable" do
        for {arg, env} <- @vars do
          Enum.each(@vars, fn {other, other_env} ->
            System.put_env(other_env, "from-env-" <> other)
          end)

          System.delete_env(env)

          assert_raise ArgumentError, ~r/'#{arg}'.*#{env}/, fn ->
            build(@module, [@host])
          end
        end
      end

      test "an empty environment variable counts as unset" do
        for {arg, env} <- @vars do
          Enum.each(@vars, fn {other, other_env} ->
            System.put_env(other_env, "from-env-" <> other)
          end)

          System.put_env(env, "")

          assert_raise ArgumentError, ~r/'#{arg}'.*#{env}/, fn ->
            build(@module, [@host])
          end
        end
      end
    end
  end

  describe "generated basic scheme wrappers" do
    test "bhv authbasic 002 generated scheme wrapper emits a basic header and masks the password" do
      refute @basic_schemes == []

      for {module, user_env, pass_env} <- @basic_schemes do
        System.put_env(user_env, "from-env-user")
        System.put_env(pass_env, "from-env-pass")

        auth = apply(module, :new, [@host])
        expected = "Basic " <> Base.encode64("from-env-user:from-env-pass")

        assert PetstoreClient.Auth.BasicAuthenticator.auth_headers(auth)["Authorization"] ==
                 expected

        refute inspect(auth) =~ "from-env-pass"
      end
    end
  end
end
