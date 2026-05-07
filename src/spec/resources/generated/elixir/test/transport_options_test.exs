defmodule PetstoreClient.TransportOptionsTest do
  use ExUnit.Case, async: true

  test "new produces correct defaults" do
    opts = PetstoreClient.TransportOptions.new()

    assert opts.verify_ssl == true
    assert opts.ca_cert_path == nil
    assert opts.proxy == nil
    assert opts.timeout == nil
    assert opts.follow_redirects == true
    assert opts.max_redirects == nil
    assert opts.user_agent == "petstore_client/1.0.0 (elixir)"
    assert opts.default_headers == %{}
    assert opts.inject_request_id == false
  end

  test "new sets all fields" do
    opts =
      PetstoreClient.TransportOptions.new(
        verify_ssl: false,
        ca_cert_path: "/path/to/ca.pem",
        proxy: "http://proxy:8080",
        timeout: 5000,
        follow_redirects: false,
        max_redirects: 3,
        user_agent: "TestAgent/1.0",
        default_headers: %{"X-Custom" => "value"},
        inject_request_id: true
      )

    assert opts.verify_ssl == false
    assert opts.ca_cert_path == "/path/to/ca.pem"
    assert opts.proxy == "http://proxy:8080"
    assert opts.timeout == 5000
    assert opts.follow_redirects == false
    assert opts.max_redirects == 3
    assert opts.user_agent == "TestAgent/1.0"
    assert opts.default_headers == %{"X-Custom" => "value"}
    assert opts.inject_request_id == true
  end

  test "follow_redirects defaults to true with nil max_redirects" do
    opts = PetstoreClient.TransportOptions.new(follow_redirects: true)

    assert opts.follow_redirects == true
    assert opts.max_redirects == nil
  end

  test "raises on invalid proxy URL" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.TransportOptions.new(proxy: "not-a-valid-url")
    end
  end

  test "nil proxy is accepted" do
    opts = PetstoreClient.TransportOptions.new(proxy: nil)
    assert opts.proxy == nil
  end

  test "valid proxy URL is accepted" do
    opts = PetstoreClient.TransportOptions.new(proxy: "http://proxy:3128")
    assert opts.proxy == "http://proxy:3128"
  end

  test "multiple default headers" do
    opts = PetstoreClient.TransportOptions.new(default_headers: %{"X-First" => "one", "X-Second" => "two"})

    assert map_size(opts.default_headers) == 2
    assert opts.default_headers["X-First"] == "one"
    assert opts.default_headers["X-Second"] == "two"
  end

  test "default_headers is an independent copy" do
    headers = %{"X-Original" => "original"}

    opts = PetstoreClient.TransportOptions.new(default_headers: headers)

    # Modifying the original map should not affect the stored headers
    # (Elixir maps are immutable, so this is inherently safe)
    assert map_size(opts.default_headers) == 1
    assert opts.default_headers["X-Original"] == "original"
  end

  test "new produces independent instances" do
    opts1 = PetstoreClient.TransportOptions.new(verify_ssl: false)
    opts2 = PetstoreClient.TransportOptions.new(verify_ssl: false)

    assert opts1.verify_ssl == opts2.verify_ssl
    # Elixir structs are value types, but different references
    assert opts1 == opts2
  end
end
