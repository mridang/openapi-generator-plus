defmodule PetstoreClient.Auth.BearerAuthenticatorTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.Auth.BearerAuthenticator

  describe "BearerAuthenticator" do
    test "bhv authbearer 009 raw token is prefixed with Bearer" do
      auth = BearerAuthenticator.new("https://api.example.com", "xyz")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer xyz"
    end

    test "bhv authbearer 003 already-prefixed token is not doubled" do
      auth = BearerAuthenticator.new("https://api.example.com", "Bearer xyz")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer xyz"
    end

    test "bhv authbearer 007 lowercase bearer prefix is stripped" do
      auth = BearerAuthenticator.new("https://api.example.com", "bearer xyz")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer xyz"
    end

    test "bhv authbearer 008 mixed-case bearer prefix is stripped" do
      auth = BearerAuthenticator.new("https://api.example.com", "BeArEr xyz")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer xyz"
    end

    # bearer-no-empty-token-guard: an empty/whitespace token would emit a
    # bare "Authorization: Bearer " header, sending the request
    # unauthenticated, so new/2 must reject it.
    test "bhv authbearer 005 rejects an empty token at construction" do
      assert_raise ArgumentError, ~r/Bearer token must not be empty/, fn ->
        BearerAuthenticator.new("https://api.example.com", "")
      end
    end

    test "bhv authbearer 017 rejects a whitespace-only token at construction" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "   ")
      end
    end

    # RFC 7230 §3.2.6 — field-value is HTAB / SP / VCHAR / obs-text. Reject
    # CR/LF (header injection) and non-ASCII (silent UTF-8 mangling).
    test "bhv authbearer 012 rejects CR/LF and non-ASCII tokens at construction" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "tok\r\nInjected: yes")
      end

      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "ñoño")
      end
    end

    test "bhv authbearer 016 a token of Unicode spaces is not blank: the printable ASCII rule rejects it" do
      error =
        assert_raise ArgumentError, fn ->
          BearerAuthenticator.new("https://api.example.com", "\u00A0\u2003")
        end

      assert error.message ==
               "Bearer token must contain only printable ASCII characters (RFC 7230 §3.2.6)"
    end

    test "bhv authbearer 015 a token that merely begins with the word bearer is kept whole" do
      auth = BearerAuthenticator.new("https://api.example.com", "bearerxyz")
      assert BearerAuthenticator.auth_headers(auth)["Authorization"] == "Bearer bearerxyz"
    end

    test "bhv authbearer 004 a doubled bearer prefix is stripped only once" do
      auth = BearerAuthenticator.new("https://api.example.com", "Bearer Bearer xyz")
      assert BearerAuthenticator.auth_headers(auth)["Authorization"] == "Bearer Bearer xyz"
    end

    test "bhv authbearer 010 a nil token is read once from the environment and a passed token wins" do
      name = "SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"
      System.put_env(name, "from-env")
      on_exit(fn -> System.delete_env(name) end)

      auth = BearerAuthenticator.new("https://api.example.com", nil)
      assert BearerAuthenticator.auth_headers(auth)["Authorization"] == "Bearer from-env"

      System.put_env(name, "changed")
      assert BearerAuthenticator.auth_headers(auth)["Authorization"] == "Bearer from-env"

      explicit = BearerAuthenticator.new("https://api.example.com", "xyz")
      assert BearerAuthenticator.auth_headers(explicit)["Authorization"] == "Bearer xyz"
    end

    test "bhv authbearer 018 an explicit token wins over the environment" do
      name = "SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"
      System.put_env(name, "env-token")
      on_exit(fn -> System.delete_env(name) end)

      auth = BearerAuthenticator.new("https://api.example.com", "explicit-token")
      assert BearerAuthenticator.auth_headers(auth)["Authorization"] == "Bearer explicit-token"
    end

    test "bhv authscheme 011 an empty environment variable for the service token scheme credential is treated as unset and fails naming the variable" do
      name = "SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"
      on_exit(fn -> System.delete_env(name) end)
      message = "Missing the credential 'token': pass it, or set the environment variable #{name}"

      System.put_env(name, "")

      assert_raise ArgumentError, message, fn ->
        BearerAuthenticator.new("https://api.example.com", nil)
      end
    end

    test "bhv lifecycle 013 an empty or absent environment variable fails when no token is passed" do
      name = "SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"
      on_exit(fn -> System.delete_env(name) end)
      message = "Missing the credential 'token': pass it, or set the environment variable #{name}"

      System.put_env(name, "")

      assert_raise ArgumentError, message, fn ->
        BearerAuthenticator.new("https://api.example.com", nil)
      end

      System.delete_env(name)

      assert_raise ArgumentError, message, fn ->
        BearerAuthenticator.new("https://api.example.com", nil)
      end
    end

    test "bhv lifecycle 014 reads the token from the environment when none is passed" do
      name = "SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"
      System.put_env(name, "env-token")
      on_exit(fn -> System.delete_env(name) end)

      auth = BearerAuthenticator.new("https://api.example.com", nil)

      assert BearerAuthenticator.auth_headers(auth)["Authorization"] == "Bearer env-token"
    end

    test "bhv lifecycle 016 a token provider that fails stops the request before it is sent" do
      auth =
        BearerAuthenticator.new("https://api.example.com", fn -> raise "token provider failed" end)

      assert_raise RuntimeError, "token provider failed", fn ->
        BearerAuthenticator.auth_headers(auth)
      end
    end

    test "bhv authbearer 014 a zero-argument function supplies the token for every request" do
      {:ok, counter} = Agent.start_link(fn -> 0 end)

      provider = fn ->
        "t" <> Integer.to_string(Agent.get_and_update(counter, fn n -> {n + 1, n + 1} end))
      end

      auth = BearerAuthenticator.new("https://api.example.com", provider)

      assert BearerAuthenticator.auth_headers(auth)["Authorization"] == "Bearer t1"
      assert BearerAuthenticator.auth_headers(auth)["Authorization"] == "Bearer t2"
      refute inspect(auth) =~ "#Function"
    end

    test "bhv authbearer 002 a token the function returns is checked like a passed one" do
      auth = BearerAuthenticator.new("https://api.example.com", fn -> "" end)

      assert_raise ArgumentError, ~r/Bearer token must not be empty/, fn ->
        BearerAuthenticator.auth_headers(auth)
      end
    end

    # authenticator-secret-in-default-string-repr: the token must never
    # appear in the authenticator's default Inspect representation.
    test "bhv authbearer 013 the redacted representation shows the host and masks the token" do
      auth = BearerAuthenticator.new("https://api.example.com", "super-secret-token")

      assert inspect(auth) =~ ~s(host: "https://api.example.com")
      refute inspect(auth) =~ "super-secret-token"
    end

    test "bhv authbearer 001 the redacted representation names the authenticator and masks the token" do
      auth = BearerAuthenticator.new("https://api.example.com", "super-secret-token")

      assert inspect(auth) =~ "PetstoreClient.Auth.BearerAuthenticator"
      refute inspect(auth) =~ "super-secret-token"
    end

    test "bhv authbearer 006 a generated scheme wrapper emits a bearer header and masks the token" do
      wrapper =
        PetstoreClient.Auth.PetStoreBearerAuthenticator.new("https://api.example.com", "xyz")

      assert BearerAuthenticator.auth_headers(wrapper) == %{"Authorization" => "Bearer xyz"}
      refute inspect(wrapper) =~ "xyz"

      assert_raise ArgumentError, fn ->
        PetstoreClient.Auth.PetStoreBearerAuthenticator.new("https://api.example.com", "")
      end
    end

    test "bhv authbearer 011 default inspect/1 redacts the token but keeps the host" do
      auth = BearerAuthenticator.new("https://api.example.com", "super-secret-token")

      refute inspect(auth) =~ "super-secret-token"
      assert inspect(auth) =~ "api.example.com"
      assert inspect(auth) =~ ~s(token: "***")
    end
  end
end
