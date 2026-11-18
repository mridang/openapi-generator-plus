defmodule PetstoreClient.Auth.BasicAuthenticatorTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.Auth.ApiKeyAuthenticator
  alias PetstoreClient.Auth.BasicAuthenticator
  alias PetstoreClient.Auth.BearerAuthenticator

  describe "BasicAuthenticator" do
    test "bhv authbasic 011 valid credentials produce basic header" do
      auth = BasicAuthenticator.new("https://api.example.com", "alice", "s3cret")
      headers = BasicAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Basic YWxpY2U6czNjcmV0"
    end

    test "bhv authbasic 001 nil credentials are read once from the environment and passed ones win" do
      user = "SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME"
      pass = "SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD"
      System.put_env(user, "alice")
      System.put_env(pass, "from-env")

      on_exit(fn ->
        System.delete_env(user)
        System.delete_env(pass)
      end)

      auth = BasicAuthenticator.new("https://api.example.com", nil, nil)

      assert BasicAuthenticator.auth_headers(auth)["Authorization"] ==
               "Basic " <> Base.encode64("alice:from-env")

      System.put_env(pass, "changed")

      assert BasicAuthenticator.auth_headers(auth)["Authorization"] ==
               "Basic " <> Base.encode64("alice:from-env")

      mixed = BasicAuthenticator.new("https://api.example.com", "bob", nil)

      assert BasicAuthenticator.auth_headers(mixed)["Authorization"] ==
               "Basic " <> Base.encode64("bob:changed")
    end

    test "bhv authbasic 013 explicit credentials win over the environment" do
      user = "SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME"
      pass = "SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD"
      System.put_env(user, "env-user")
      System.put_env(pass, "env-pass")

      on_exit(fn ->
        System.delete_env(user)
        System.delete_env(pass)
      end)

      auth = BasicAuthenticator.new("https://api.example.com", "alice", "explicit-pass")

      assert BasicAuthenticator.auth_headers(auth)["Authorization"] ==
               "Basic " <> Base.encode64("alice:explicit-pass")
    end

    test "bhv authbasic 003 an empty or absent environment variable fails when the authenticator is built" do
      user = "SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME"
      pass = "SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD"

      on_exit(fn ->
        System.delete_env(user)
        System.delete_env(pass)
      end)

      System.put_env(user, "")
      System.delete_env(pass)

      assert_raise ArgumentError,
                   "Missing the credential 'username': pass it, or set the environment variable #{user}",
                   fn -> BasicAuthenticator.new("https://api.example.com", nil, "p") end

      assert_raise ArgumentError,
                   "Missing the credential 'password': pass it, or set the environment variable #{pass}",
                   fn -> BasicAuthenticator.new("https://api.example.com", "alice", nil) end
    end

    test "bhv authbasic 004 non-ASCII credentials are encoded as UTF-8" do
      auth = BasicAuthenticator.new("https://api.example.com", "ünïcødé", "pässwörd")
      headers = BasicAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Basic w7xuw69jw7hkw6k6cMOkc3N3w7ZyZA=="
    end

    test "bhv authbasic 018 a password may contain a colon" do
      auth = BasicAuthenticator.new("https://api.example.com", "alice", "s3:cret")

      assert BasicAuthenticator.auth_headers(auth)["Authorization"] ==
               "Basic " <> Base.encode64("alice:s3:cret")
    end

    test "rejects each invalid credential with its own message" do
      assert_raise ArgumentError,
                   "Basic auth username must not contain CR, LF, or NUL characters",
                   fn ->
                     BasicAuthenticator.new("https://api.example.com", "ali\nce", "s3cret")
                   end

      assert_raise ArgumentError,
                   "Basic auth username must not contain ':' (RFC 7617 §2)",
                   fn -> BasicAuthenticator.new("https://api.example.com", "ali:ce", "s3cret") end

      assert_raise ArgumentError,
                   "Basic auth password must not contain CR, LF, or NUL characters",
                   fn ->
                     BasicAuthenticator.new("https://api.example.com", "alice", "s3c\rret")
                   end
    end

    test "bhv authbasic 009 rejects username with crlf at construction" do
      assert_raise ArgumentError, fn ->
        BasicAuthenticator.new("https://api.example.com", "alice\r\n", "s3cret")
      end
    end

    test "bhv authbasic 007 rejects password with nul at construction" do
      assert_raise ArgumentError, fn ->
        BasicAuthenticator.new("https://api.example.com", "alice", "s3c\0ret")
      end
    end

    test "bhv authbasic 008 rejects username with colon at construction" do
      assert_raise ArgumentError, fn ->
        BasicAuthenticator.new("https://api.example.com", "ali:ce", "s3cret")
      end
    end

    test "bhv authbasic 006 redacts secret" do
      auth = BasicAuthenticator.new("https://api.example.com", "alice", "s3cret")

      refute inspect(auth) =~ "s3cret"
      assert inspect(auth) =~ "***"
    end
  end

  # bearer-no-empty-token-guard: an empty/whitespace Bearer token must be
  # rejected at construction, not silently emit `Authorization: Bearer `.
  describe "BearerAuthenticator empty-token guard" do
    test "valid token produces bearer header" do
      auth = BearerAuthenticator.new("https://api.example.com", "abc123")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer abc123"
    end

    test "rejects empty token" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "")
      end
    end

    test "bhv authbearer 027 rejects whitespace-only token" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "   ")
      end
    end
  end

  # authenticator-secret-in-default-string-repr: the secret must never
  # appear in the authenticator's default Inspect representation.
  describe "secret redaction in default inspect" do
    test "bearer token is not leaked by inspect/1" do
      auth =
        BearerAuthenticator.new("https://api.example.com", "super-secret-token")

      refute inspect(auth) =~ "super-secret-token"
      assert inspect(auth) =~ "api.example.com"
    end

    test "bhv authbasic 005 basic auth password is not leaked by inspect/1" do
      auth =
        BasicAuthenticator.new("https://api.example.com", "alice", "super-secret-pw")

      refute inspect(auth) =~ "super-secret-pw"
      assert inspect(auth) =~ "alice"
      assert inspect(auth) =~ ~s(password: "***")
    end

    test "bhv authbasic 020 a basic authenticator masks the password in its debug output" do
      secret = Enum.join(["super", "secret", "pw"], "-")
      auth = BasicAuthenticator.new("https://api.example.com", "alice", secret)

      refute inspect(auth) =~ secret
      assert inspect(auth) =~ ~s(password: "***")
    end

    test "bhv authbasic 010 basic auth repr shows the host and username and masks only the password" do
      auth = BasicAuthenticator.new("https://api.example.com", "alice", "super-secret-pw")

      assert inspect(auth) =~ ~s(host: "https://api.example.com")
      assert inspect(auth) =~ ~s(username: "alice")
      assert inspect(auth) =~ ~s(password: "***")
    end

    test "bhv authapikey 026 api key is not leaked by inspect/1" do
      auth =
        ApiKeyAuthenticator.new(
          "https://api.example.com",
          "X-Api-Key",
          "super-secret-key",
          :header
        )

      refute inspect(auth) =~ "super-secret-key"
      assert inspect(auth) =~ ~s(key_param_name: "X-Api-Key")
      assert inspect(auth) =~ "location: :header"
    end

    test "bhv auth 024 each credential field takes its passed value or else its own environment variable" do
      user = "SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME"
      pass = "SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD"
      System.put_env(user, "env-user")
      System.put_env(pass, "env-pass")

      on_exit(fn ->
        System.delete_env(user)
        System.delete_env(pass)
      end)

      only_password = BasicAuthenticator.new("https://api.example.com", nil, "explicit-pass")

      assert BasicAuthenticator.auth_headers(only_password)["Authorization"] ==
               "Basic " <> Base.encode64("env-user:explicit-pass")

      only_user = BasicAuthenticator.new("https://api.example.com", "alice", nil)

      assert BasicAuthenticator.auth_headers(only_user)["Authorization"] ==
               "Basic " <> Base.encode64("alice:env-pass")
    end
  end
end
