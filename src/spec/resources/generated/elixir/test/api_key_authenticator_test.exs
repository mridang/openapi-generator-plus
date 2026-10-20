defmodule PetstoreClient.Auth.ApiKeyAuthenticatorTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.Auth.ApiKeyAuthenticator

  describe "ApiKeyAuthenticator" do
    test "valid header key is emitted as a header" do
      auth = ApiKeyAuthenticator.new("https://api.example.com", "X-Api-Key", "abc123", :header)
      headers = ApiKeyAuthenticator.auth_headers(auth)
      assert headers["X-Api-Key"] == "abc123"
    end

    test "valid query key is emitted as a query param" do
      auth = ApiKeyAuthenticator.new("https://api.example.com", "api_key", "abc123", :query)
      params = ApiKeyAuthenticator.query_params(auth)
      assert params["api_key"] == "abc123"
    end

    test "valid cookie key is emitted as a cookie param" do
      auth = ApiKeyAuthenticator.new("https://api.example.com", "session", "abc123", :cookie)
      cookies = ApiKeyAuthenticator.cookie_params(auth)
      assert cookies["session"] == "abc123"
    end

    test "rejects an empty key for every location at construction" do
      assert_raise ArgumentError, fn ->
        ApiKeyAuthenticator.new("https://api.example.com", "X-Api-Key", "", :header)
      end

      assert_raise ArgumentError, fn ->
        ApiKeyAuthenticator.new("https://api.example.com", "api_key", "", :query)
      end

      assert_raise ArgumentError, fn ->
        ApiKeyAuthenticator.new("https://api.example.com", "session", "", :cookie)
      end
    end

    test "rejects a whitespace-only key at construction" do
      assert_raise ArgumentError, fn ->
        ApiKeyAuthenticator.new("https://api.example.com", "X-Api-Key", "   ", :header)
      end
    end

    # CR/LF/NUL are forbidden control characters in ALL locations.
    test "rejects CR/LF/NUL keys for query and cookie at construction" do
      assert_raise ArgumentError, fn ->
        ApiKeyAuthenticator.new("https://api.example.com", "api_key", "abc\r\n", :query)
      end

      assert_raise ArgumentError, fn ->
        ApiKeyAuthenticator.new("https://api.example.com", "session", "abc\n", :cookie)
      end

      assert_raise ArgumentError, fn ->
        ApiKeyAuthenticator.new("https://api.example.com", "X-Api-Key", "abc\rdef", :header)
      end
    end

    # HEADER location must reject anything outside printable ASCII + TAB
    # (RFC 7230 §3.2.6); non-header locations accept arbitrary chars.
    test "header location rejects non-ASCII while query accepts it" do
      assert_raise ArgumentError, fn ->
        ApiKeyAuthenticator.new("https://api.example.com", "X-Api-Key", "kéy", :header)
      end

      query_auth =
        ApiKeyAuthenticator.new("https://api.example.com", "api_key", "kéy", :query)

      assert ApiKeyAuthenticator.query_params(query_auth) == %{"api_key" => "kéy"}
    end

    test "rejects each invalid key with its own message" do
      assert_raise ArgumentError,
                   "API key value for 'X-Api-Key' must not be empty",
                   fn ->
                     ApiKeyAuthenticator.new("https://api.example.com", "X-Api-Key", " ", :header)
                   end

      assert_raise ArgumentError,
                   "API key value for 'api_key' contains forbidden control characters (CR/LF/NUL)",
                   fn ->
                     ApiKeyAuthenticator.new("https://api.example.com", "api_key", "a\0b", :query)
                   end

      assert_raise ArgumentError,
                   "API key for header 'X-Api-Key' must contain only printable ASCII characters (RFC 7230 §3.2.6)",
                   fn ->
                     ApiKeyAuthenticator.new(
                       "https://api.example.com",
                       "X-Api-Key",
                       "kéy",
                       :header
                     )
                   end
    end

    test "a key emits only at its own location" do
      for location <- [:header, :query, :cookie] do
        auth = ApiKeyAuthenticator.new("https://api.example.com", "k", "abc123", location)

        assert ApiKeyAuthenticator.auth_headers(auth) ==
                 if(location == :header, do: %{"k" => "abc123"}, else: %{})

        assert ApiKeyAuthenticator.query_params(auth) ==
                 if(location == :query, do: %{"k" => "abc123"}, else: %{})

        assert ApiKeyAuthenticator.cookie_params(auth) ==
                 if(location == :cookie, do: %{"k" => "abc123"}, else: %{})
      end
    end

    # The authenticators generated for the spec's API key schemes fix the key
    # name and the location the spec declares.
    test "scheme authenticators send their key under the declared name and location" do
      header =
        PetstoreClient.Auth.ApiKeyHeaderAuthenticator.new("https://api.example.com", "abc123")

      assert ApiKeyAuthenticator.auth_headers(header) == %{"X-API-Key" => "abc123"}
      assert ApiKeyAuthenticator.query_params(header) == %{}

      internal =
        PetstoreClient.Auth.InternalApiKeyAuthenticator.new("https://api.example.com", "abc123")

      assert ApiKeyAuthenticator.auth_headers(internal) == %{"X-Internal-Key" => "abc123"}

      query =
        PetstoreClient.Auth.ApiKeyQueryAuthenticator.new("https://api.example.com", "abc123")

      assert ApiKeyAuthenticator.query_params(query) == %{"api_key" => "abc123"}
      assert ApiKeyAuthenticator.auth_headers(query) == %{}

      cookie =
        PetstoreClient.Auth.SessionCookieAuthenticator.new("https://api.example.com", "abc123")

      assert ApiKeyAuthenticator.cookie_params(cookie) == %{"SESSION_ID" => "abc123"}
      assert ApiKeyAuthenticator.auth_headers(cookie) == %{}
    end

    test "scheme authenticators validate eagerly and mask the key" do
      for module <- [
            PetstoreClient.Auth.ApiKeyHeaderAuthenticator,
            PetstoreClient.Auth.InternalApiKeyAuthenticator,
            PetstoreClient.Auth.ApiKeyQueryAuthenticator,
            PetstoreClient.Auth.SessionCookieAuthenticator
          ] do
        assert_raise ArgumentError, fn -> module.new("https://api.example.com", "") end

        auth = module.new("https://api.example.com", "super-secret-key")
        refute inspect(auth) =~ "super-secret-key"
        assert inspect(auth) =~ ~s(api_key: "***")
      end
    end

    test "scheme authenticators for Basic and Bearer delegate to the base authenticators" do
      basic =
        PetstoreClient.Auth.AdminBasicAuthenticator.new(
          "https://api.example.com",
          "alice",
          "s3cret"
        )

      assert PetstoreClient.Auth.BasicAuthenticator.auth_headers(basic) == %{
               "Authorization" => "Basic YWxpY2U6czNjcmV0"
             }

      refute inspect(basic) =~ "s3cret"

      bearer =
        PetstoreClient.Auth.PetStoreBearerAuthenticator.new("https://api.example.com", "xyz")

      assert PetstoreClient.Auth.BearerAuthenticator.auth_headers(bearer) == %{
               "Authorization" => "Bearer xyz"
             }

      refute inspect(bearer) =~ "xyz"

      assert_raise ArgumentError, fn ->
        PetstoreClient.Auth.AdminBasicAuthenticator.new(
          "https://api.example.com",
          "ali:ce",
          "s3cret"
        )
      end

      assert_raise ArgumentError, fn ->
        PetstoreClient.Auth.PetStoreBearerAuthenticator.new("https://api.example.com", "")
      end
    end

    # authenticator-secret-in-default-string-repr: the key must never appear
    # in the authenticator's default Inspect representation.
    test "default inspect/1 redacts the API key" do
      auth =
        ApiKeyAuthenticator.new(
          "https://api.example.com",
          "X-Api-Key",
          "super-secret-key",
          :header
        )

      refute inspect(auth) =~ "super-secret-key"
      assert inspect(auth) =~ ~s(api_key: "***")
    end
  end
end
