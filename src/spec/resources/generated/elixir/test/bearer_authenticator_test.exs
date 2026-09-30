defmodule PetstoreClient.Auth.BearerAuthenticatorTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.Auth.BearerAuthenticator

  describe "BearerAuthenticator" do
    test "raw token is prefixed with Bearer" do
      auth = BearerAuthenticator.new("https://api.example.com", "xyz")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer xyz"
    end

    test "already-prefixed token is not doubled" do
      auth = BearerAuthenticator.new("https://api.example.com", "Bearer xyz")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer xyz"
    end

    test "lowercase bearer prefix is stripped" do
      auth = BearerAuthenticator.new("https://api.example.com", "bearer xyz")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer xyz"
    end

    test "mixed-case bearer prefix is stripped" do
      auth = BearerAuthenticator.new("https://api.example.com", "BeArEr xyz")
      headers = BearerAuthenticator.auth_headers(auth)
      assert headers["Authorization"] == "Bearer xyz"
    end

    # bearer-no-empty-token-guard: an empty/whitespace token would emit a
    # bare "Authorization: Bearer " header, sending the request
    # unauthenticated, so new/2 must reject it.
    test "rejects an empty token at construction" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "")
      end
    end

    test "rejects a whitespace-only token at construction" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "   ")
      end
    end

    # RFC 7230 §3.2.6 — field-value is HTAB / SP / VCHAR / obs-text. Reject
    # CR/LF (header injection) and non-ASCII (silent UTF-8 mangling).
    test "rejects CR/LF and non-ASCII tokens at construction" do
      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "tok\r\nInjected: yes")
      end

      assert_raise ArgumentError, fn ->
        BearerAuthenticator.new("https://api.example.com", "ñoño")
      end
    end

    # authenticator-secret-in-default-string-repr: the token must never
    # appear in the authenticator's default Inspect representation.
    test "default inspect/1 redacts the token but keeps the host" do
      auth = BearerAuthenticator.new("https://api.example.com", "super-secret-token")

      refute inspect(auth) =~ "super-secret-token"
      assert inspect(auth) =~ "api.example.com"
    end
  end
end
