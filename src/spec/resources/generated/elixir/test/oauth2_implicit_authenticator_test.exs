defmodule PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticatorTest do
  use ExUnit.Case, async: true

  defp create_authenticator do
    PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.new(
      "https://api.example.com",
      "my-client-id",
      "https://auth.example.com/authorize",
      ["read", "write"]
    )
  end

  describe "OAuth2ImplicitAuthenticator" do
    test "scheme authenticator keeps the declared scopes in the authorization URL" do
      auth =
        PetstoreClient.Auth.OAuth.BrowserAuthImplicitAuthenticator.new(
          "https://api.example.com",
          "my-client-id"
        )

      assert auth.scopes == ["read"]

      url = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.build_authorization_url(auth)

      assert String.contains?(url, "scope=read")
    end

    test "builds authorization URL with response type token" do
      auth = create_authenticator()

      url = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.build_authorization_url(auth)

      assert String.starts_with?(url, "https://auth.example.com/authorize?")
      assert String.contains?(url, "response_type=token")
    end

    test "builds authorization URL with client id" do
      auth = create_authenticator()

      url = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.build_authorization_url(auth)

      assert String.contains?(url, "client_id=my-client-id")
    end

    test "builds authorization URL with scopes" do
      auth = create_authenticator()

      url = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.build_authorization_url(auth)

      assert String.contains?(url, "scope=read%20write")
      refute String.contains?(url, "scope=read+write")
    end

    test "builds authorization URL with state" do
      auth = create_authenticator()

      url =
        PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.build_authorization_url(
          auth,
          "my-state"
        )

      assert String.contains?(url, "state=my-state")
    end

    test "get_auth_headers returns Bearer after set_access_token" do
      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.set_access_token(
          auth,
          "implicit-tok"
        )

      headers = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.auth_headers(auth)

      assert headers["Authorization"] == "Bearer implicit-tok"
    end

    test "throws when access token not set" do
      auth = create_authenticator()

      assert_raise RuntimeError, ~r/Must set the access token before making API requests/, fn ->
        PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.auth_headers(auth)
      end
    end

    test "set_access_token rejects a token that is not printable ASCII" do
      auth = create_authenticator()

      for token <- ["tok\r\nX-Injected: 1", "tok\nX", "tok\rX", "tok\0X", "t\u00F6k", "tok\u007F"] do
        assert_raise ArgumentError,
                     "Access token must contain only printable ASCII characters (RFC 7230 §3.2.6)",
                     fn ->
                       PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.set_access_token(
                         auth,
                         token
                       )
                     end
      end
    end

    test "authorization URL percent-encodes * ! ' ( ) and keeps ~ literal" do
      auth =
        PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.new(
          "https://api.example.com",
          "id~*!'()",
          "https://auth.example.com/authorize",
          ["read", "a*b(c)"]
        )

      url =
        PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.build_authorization_url(
          auth,
          "s t'a!te"
        )

      assert String.contains?(url, "client_id=id~%2A%21%27%28%29")
      assert String.contains?(url, "scope=read%20a%2Ab%28c%29")
      assert String.contains?(url, "state=s%20t%27a%21te")
    end

    test "get_host returns configured host" do
      auth = create_authenticator()

      assert PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.host(auth) ==
               "https://api.example.com"
    end

    test "redacts the secret" do
      auth = create_authenticator()

      auth =
        PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.set_access_token(
          auth,
          "implicit-tok"
        )

      refute inspect(auth) =~ "implicit-tok"
      assert inspect(auth) =~ ~s(access_token: "***")
    end
  end
end
