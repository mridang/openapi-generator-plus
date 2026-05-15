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

      assert String.contains?(url, "scope=read+write") or String.contains?(url, "scope=read%20write")
    end

    test "builds authorization URL with state" do
      auth = create_authenticator()

      url = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.build_authorization_url(auth, "my-state")

      assert String.contains?(url, "state=my-state")
    end

    test "get_auth_headers returns Bearer after set_access_token" do
      auth = create_authenticator()
      auth = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.set_access_token(auth, "implicit-tok")

      headers = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.auth_headers(auth)

      assert headers["Authorization"] == "Bearer implicit-tok"
    end

    test "throws when access token not set" do
      auth = create_authenticator()

      assert_raise RuntimeError, fn ->
        PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.auth_headers(auth)
      end
    end

    test "get_host returns configured host" do
      auth = create_authenticator()

      assert PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.host(auth) == "https://api.example.com"
    end
  end
end
