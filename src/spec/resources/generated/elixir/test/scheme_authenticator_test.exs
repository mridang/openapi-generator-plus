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
    {PetstoreClient.Auth.ArchiveDigestAuthenticator,
     [
       {"username", "SWAGGER_PETSTORE_OPEN_API_3_0_ARCHIVE_DIGEST_USERNAME"},
       {"password", "SWAGGER_PETSTORE_OPEN_API_3_0_ARCHIVE_DIGEST_PASSWORD"}
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
    {PetstoreClient.Auth.OAuth.DeviceAuthDeviceAuthorizationAuthenticator,
     [{"client_id", "SWAGGER_PETSTORE_OPEN_API_3_0_DEVICE_AUTH_CLIENT_ID"}]},
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

  @bearer_schemes for {module, [{"token", env}]} <- @schemes, do: {module, env}

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

  # An empty variable counts as unset: building the authenticator fails and names each variable.
  defp assert_empty_variables_are_named(module) do
    {^module, vars} = List.keyfind!(@schemes, module, 0)

    for {arg, env} <- vars do
      Enum.each(vars, fn {other, other_env} -> System.put_env(other_env, "from-env-" <> other) end)

      System.put_env(env, "")

      assert_raise ArgumentError, ~r/'#{arg}'.*#{env}/, fn ->
        build(module, [@host])
      end
    end
  end

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

  # Runs the body once per scheme with a clean environment; the scheme is named in any failure.
  defp each_scheme(fun) do
    for {module, vars} <- @schemes do
      Enum.each(@schemes, fn {_m, vs} ->
        Enum.each(vs, fn {_a, env} -> System.delete_env(env) end)
      end)

      try do
        fun.(module, vars)
      rescue
        error in [ExUnit.AssertionError] ->
          reraise %{error | message: "#{inspect(module)}: " <> error.message}, __STACKTRACE__
      end
    end
  end

  test "bhv authscheme 001 builds from its environment variables alone" do
    each_scheme(fn module, vars ->
      Enum.each(vars, fn {arg, env} -> System.put_env(env, "from-env-" <> arg) end)

      auth = build(module, [@host])

      for {arg, _env} <- vars do
        assert Map.fetch!(auth, String.to_atom(arg)) == "from-env-" <> arg
      end
    end)
  end

  test "bhv authscheme 009 an argument that is passed wins over its environment variable" do
    each_scheme(fn module, vars ->
      Enum.each(vars, fn {arg, env} -> System.put_env(env, "from-env-" <> arg) end)
      [{first, _env} | rest] = vars

      auth = build(module, [@host, "explicit-" <> first | Enum.map(rest, fn _ -> nil end)])

      assert Map.fetch!(auth, String.to_atom(first)) == "explicit-" <> first

      for {arg, _env} <- rest do
        assert Map.fetch!(auth, String.to_atom(arg)) == "from-env-" <> arg
      end
    end)
  end

  test "bhv authscheme 010 an empty environment variable is treated as unset and fails naming the variable" do
    each_scheme(fn module, vars ->
      [{_arg, env} | _] = vars
      System.put_env(env, "")

      error = assert_raise ArgumentError, fn -> build(module, [@host]) end

      assert Exception.message(error) =~ env
    end)
  end

  test "bhv authscheme 002 explicit arguments need no environment" do
    each_scheme(fn module, vars ->
      args = Enum.map(vars, fn {arg, _env} -> "explicit-" <> arg end)

      auth = build(module, [@host | args])

      for {arg, _env} <- vars do
        assert Map.fetch!(auth, String.to_atom(arg)) == "explicit-" <> arg
      end
    end)
  end

  test "bhv lifecycle 025 the environment is read once, when the authenticator is built" do
    each_scheme(fn module, vars ->
      Enum.each(vars, fn {arg, env} -> System.put_env(env, "first-" <> arg) end)

      auth = build(module, [@host])
      Enum.each(vars, fn {arg, env} -> System.put_env(env, "second-" <> arg) end)

      for {arg, _env} <- vars do
        assert Map.fetch!(auth, String.to_atom(arg)) == "first-" <> arg
      end
    end)
  end

  test "bhv langelixir 044 a credential found nowhere raises and names its variable" do
    each_scheme(fn module, vars ->
      for {arg, env} <- vars do
        Enum.each(vars, fn {other, other_env} ->
          System.put_env(other_env, "from-env-" <> other)
        end)

        System.delete_env(env)

        assert_raise ArgumentError, ~r/'#{arg}'.*#{env}/, fn ->
          build(module, [@host])
        end
      end
    end)
  end

  test "bhv authbasic 012 an empty environment variable counts as unset" do
    each_scheme(fn module, vars ->
      for {arg, env} <- vars do
        Enum.each(vars, fn {other, other_env} ->
          System.put_env(other_env, "from-env-" <> other)
        end)

        System.put_env(env, "")

        assert_raise ArgumentError, ~r/'#{arg}'.*#{env}/, fn ->
          build(module, [@host])
        end
      end
    end)
  end

  test "bhv misc 073 the generated module exists for every security scheme the spec declares" do
    for module <- [
          PetstoreClient.Auth.PetStoreBasicAuthenticator,
          PetstoreClient.Auth.AdminBasicAuthenticator,
          PetstoreClient.Auth.ArchiveDigestAuthenticator,
          PetstoreClient.Auth.PetStoreBearerAuthenticator,
          PetstoreClient.Auth.ServiceTokenAuthenticator,
          PetstoreClient.Auth.ApiKeyHeaderAuthenticator,
          PetstoreClient.Auth.InternalApiKeyAuthenticator,
          PetstoreClient.Auth.ApiKeyQueryAuthenticator,
          PetstoreClient.Auth.SessionCookieAuthenticator,
          PetstoreClient.Auth.OAuth.MachineAuthClientCredentialsAuthenticator,
          PetstoreClient.Auth.OAuth.LegacyAuthPasswordAuthenticator,
          PetstoreClient.Auth.OAuth.UserAuthAuthorizationCodeAuthenticator,
          PetstoreClient.Auth.OAuth.BrowserAuthImplicitAuthenticator,
          PetstoreClient.Auth.OAuth.DeviceAuthDeviceAuthorizationAuthenticator,
          PetstoreClient.Auth.OAuth.SsoAuthAuthenticator
        ] do
      assert Code.ensure_loaded?(module)
    end
  end

  test "bhv authscheme 003 a scheme authenticator fails at construction naming the variable when its environment variable is empty" do
    Enum.each(@schemes, fn {module, _vars} -> assert_empty_variables_are_named(module) end)
  end

  test "bhv authscheme 004 the AdminBasic scheme authenticator treats an empty environment variable as unset and names it" do
    assert_empty_variables_are_named(PetstoreClient.Auth.AdminBasicAuthenticator)
  end

  test "bhv authscheme 005 the ApiKeyHeader scheme authenticator treats an empty environment variable as unset and names it" do
    assert_empty_variables_are_named(PetstoreClient.Auth.ApiKeyHeaderAuthenticator)
  end

  test "bhv authscheme 006 the ApiKeyQuery scheme authenticator treats an empty environment variable as unset and names it" do
    assert_empty_variables_are_named(PetstoreClient.Auth.ApiKeyQueryAuthenticator)
  end

  test "bhv authscheme 007 the SessionCookie scheme authenticator treats an empty environment variable as unset and names it" do
    assert_empty_variables_are_named(PetstoreClient.Auth.SessionCookieAuthenticator)
  end

  test "bhv authscheme 008 the SsoAuth scheme authenticator treats an empty environment variable as unset and names it" do
    assert_empty_variables_are_named(PetstoreClient.Auth.OAuth.SsoAuthAuthenticator)
  end

  test "bhv authscheme 700 a scheme that declares oauth2MetadataUrl exposes that URL as a constant on its generated per-scheme authenticator" do
    assert PetstoreClient.Auth.OAuth.MachineAuthClientCredentialsAuthenticator.metadata_url() ==
             "https://auth.example.com/.well-known/oauth-authorization-server"
  end

  describe "generated basic scheme wrappers" do
    test "bhv authbasic 019 generated basic scheme wrappers emit the same Basic header as the base authenticator" do
      refute @basic_schemes == []

      for {module, user_env, pass_env} <- @basic_schemes do
        System.put_env(user_env, "from-env-user")
        System.put_env(pass_env, "from-env-pass")

        wrapper = apply(module, :new, [@host])
        base = PetstoreClient.Auth.BasicAuthenticator.new(@host, "from-env-user", "from-env-pass")

        assert PetstoreClient.Auth.BasicAuthenticator.auth_headers(wrapper) ==
                 PetstoreClient.Auth.BasicAuthenticator.auth_headers(base)
      end
    end

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

  describe "generated bearer scheme wrappers" do
    test "bhv authbearer 023 generated bearer scheme wrappers emit a Bearer header" do
      refute @bearer_schemes == []

      for {module, env} <- @bearer_schemes do
        System.put_env(env, "from-env-token")

        wrapper = apply(module, :new, [@host])

        assert PetstoreClient.Auth.BearerAuthenticator.auth_headers(wrapper) ==
                 %{"Authorization" => "Bearer from-env-token"}
      end
    end
  end
end
