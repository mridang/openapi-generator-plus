defmodule PetstoreClient.Auth.BasicAuthenticatorTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.Auth.ApiKeyAuthenticator
  alias PetstoreClient.Auth.BasicAuthenticator
  alias PetstoreClient.Auth.BearerAuthenticator

  describe "BasicAuthenticator" do
    test "bhv auth 036 a username containing a colon is rejected at construction" do
      assert_raise ArgumentError, ~r/must not contain ':'/, fn ->
        BasicAuthenticator.new("https://api.example.com", "us:er", "s3cret")
      end
    end

    test "bhv misc 193 the Basic scheme authenticator sends the same header as the base Basic authenticator and rejects a missing username" do
      auth = BasicAuthenticator.new("https://api.example.com", "alice", "s3cret")
      assert BasicAuthenticator.auth_headers(auth)["Authorization"] == "Basic YWxpY2U6czNjcmV0"

      assert_raise ArgumentError, fn ->
        BasicAuthenticator.new("https://api.example.com", nil, "s3cret")
      end
    end

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

    test "bhv misc 068 an empty string passed for a credential counts as not passed" do
      user = "SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME"
      pass = "SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD"
      System.put_env(user, "alice")
      System.put_env(pass, "from-env")

      on_exit(fn ->
        System.delete_env(user)
        System.delete_env(pass)
      end)

      auth = BasicAuthenticator.new("https://api.example.com", "", "")

      assert BasicAuthenticator.auth_headers(auth)["Authorization"] ==
               "Basic " <> Base.encode64("alice:from-env")
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

    test "bhv langelixir 046 rejects each invalid credential with its own message" do
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

    test "bhv errors 081 a credential refused at construction names whether the username or the password is at fault" do
      assert_raise ArgumentError, ~r/^Basic auth username must not contain/, fn ->
        PetstoreClient.Auth.BasicAuthenticator.new("https://api.example.com", "ali:ce", "s3cret")
      end

      assert_raise ArgumentError, ~r/^Basic auth password must not contain/, fn ->
        PetstoreClient.Auth.BasicAuthenticator.new("https://api.example.com", "alice", "s3c\rret")
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
    test "bhv langelixir 047 an empty token is rejected with an error" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "")
      end
    end

    test "bhv authbearer 027 a bearer authenticator rejects a whitespace-only token" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "   ")
      end
    end
  end

  # authenticator-secret-in-default-string-repr: the secret must never
  # appear in the authenticator's default Inspect representation.
  describe "secret redaction in default inspect" do
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

  test "bhv misc 017 a construction error for rejected credentials never includes the rejected value" do
    error =
      assert_raise ArgumentError, fn ->
        PetstoreClient.Auth.BasicAuthenticator.new(
          "https://api.example.com",
          "alice",
          "sec\rret-9"
        )
      end

    refute Exception.message(error) =~ "sec"
    refute Exception.message(error) =~ "ret-9"
  end

  describe "DigestAuthenticator" do
    alias PetstoreClient.Auth.DigestAuthenticator

    # The example of RFC 7616 section 3.9.1.
    @rfc_nonce "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v"
    @rfc_opaque "FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS"
    @rfc_cnonce "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ"

    defp rfc_digest do
      auth = DigestAuthenticator.new("https://api.example.com", "Mufasa", "Circle of Life")
      %{auth | cnonce_source: fn -> @rfc_cnonce end}
    end

    defp rfc_challenge(algorithm) do
      ~s(Digest realm="http-auth@example.org", qop="auth, auth-int", algorithm=#{algorithm}, ) <>
        ~s(nonce="#{@rfc_nonce}", opaque="#{@rfc_opaque}")
    end

    defp rfc_header(algorithm, response) do
      ~s(Digest username="Mufasa", realm="http-auth@example.org", nonce="#{@rfc_nonce}", ) <>
        ~s(uri="/dir/index.html", algorithm=#{algorithm}, response="#{response}", qop=auth, nc=00000001, ) <>
        ~s(cnonce="#{@rfc_cnonce}", opaque="#{@rfc_opaque}")
    end

    defp hex(algorithm, text), do: algorithm |> :crypto.hash(text) |> Base.encode16(case: :lower)

    test "bhv authdigest 700 the RFC 7616 MD5 example challenge is answered with the exact header" do
      auth = rfc_digest()

      assert DigestAuthenticator.authorization(auth, "GET", "/dir/index.html") == nil
      assert DigestAuthenticator.accept_challenge(auth, rfc_challenge("MD5"))

      assert DigestAuthenticator.authorization(auth, "GET", "/dir/index.html") ==
               rfc_header("MD5", "8ca523f5e9506fed4657c9700eebdbec")

      assert DigestAuthenticator.auth_headers(auth) == %{}
    end

    test "bhv authdigest 701 the RFC 7616 SHA-256 example is answered and a -sess variant hashes the first value with the nonces" do
      auth = rfc_digest()
      assert DigestAuthenticator.accept_challenge(auth, rfc_challenge("SHA-256"))

      assert DigestAuthenticator.authorization(auth, "GET", "/dir/index.html") ==
               rfc_header(
                 "SHA-256",
                 "753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1"
               )

      for {algorithm, hash} <- [{"MD5-sess", :md5}, {"SHA-256-sess", :sha256}] do
        sess = rfc_digest()
        assert DigestAuthenticator.accept_challenge(sess, rfc_challenge(algorithm))

        first =
          hex(
            hash,
            "#{hex(hash, "Mufasa:http-auth@example.org:Circle of Life")}:#{@rfc_nonce}:#{@rfc_cnonce}"
          )

        expected =
          hex(
            hash,
            "#{first}:#{@rfc_nonce}:00000001:#{@rfc_cnonce}:auth:#{hex(hash, "GET:/dir/index.html")}"
          )

        assert DigestAuthenticator.authorization(sess, "GET", "/dir/index.html") ==
                 rfc_header(algorithm, expected)
      end
    end

    test "bhv authdigest 704 a challenge without qop is answered in the RFC 2069 form, echoing only what was sent" do
      auth = rfc_digest()

      assert DigestAuthenticator.accept_challenge(auth, [
               ~s(Basic realm="other"),
               ~S(digest realm="r\"1", nonce="n1")
             ])

      first = hex(:md5, ~s(Mufasa:r"1:Circle of Life))
      expected = hex(:md5, "#{first}:n1:#{hex(:md5, "GET:/a?b=c")}")

      assert DigestAuthenticator.authorization(auth, "GET", "/a?b=c") ==
               ~S(Digest username="Mufasa", realm="r\"1", nonce="n1", uri="/a?b=c", response=") <>
                 expected <> ~s(")
    end

    test "bhv authdigest 705 a challenge offering only auth-int or naming an unknown algorithm is not accepted" do
      auth = rfc_digest()

      refute DigestAuthenticator.accept_challenge(
               auth,
               ~s(Digest realm="r", nonce="n", qop="auth-int")
             )

      refute DigestAuthenticator.accept_challenge(
               auth,
               ~s(Digest realm="r", nonce="n", qop="auth", algorithm=SHA-512-256)
             )

      assert DigestAuthenticator.authorization(auth, "GET", "/") == nil
    end

    test "bhv authdigest 706 unusable credentials are refused at construction and the password is masked" do
      for {username, secret, message} <- [
            {"", "pw", "Digest username must not be empty"},
            {"al\nice", "pw", "Digest credentials must not contain a line break or NUL"},
            {"alice", "p\rw", "Digest credentials must not contain a line break or NUL"},
            {"alice", "p\0w", "Digest credentials must not contain a line break or NUL"},
            {~s(al"ice), "pw", "Digest username must not contain a double quote"}
          ] do
        error =
          assert_raise ArgumentError, fn ->
            DigestAuthenticator.new("https://api.example.com", username, secret)
          end

        assert Exception.message(error) == message
      end

      shown = inspect(rfc_digest())
      assert shown =~ "***"
      refute shown =~ "Circle of Life"
    end
  end
end
