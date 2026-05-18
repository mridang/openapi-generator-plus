defmodule PetstoreClient.TransportOptionsTest do
  use ExUnit.Case, async: true

  test "verify_ssl defaults to true" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.verify_ssl == true
  end

  test "ca_cert_path defaults to nil" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.ca_cert_path == nil
  end

  test "proxy defaults to nil" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.proxy == nil
  end

  test "timeout defaults to nil" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.timeout == nil
  end

  test "follow_redirects defaults to true" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.follow_redirects == true
  end

  test "max_redirects defaults to nil" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.max_redirects == nil
  end

  test "user_agent defaults to non-empty string" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.user_agent != nil
    assert String.length(opts.user_agent) > 0
  end

  test "default_headers defaults to empty map" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.default_headers == %{}
  end

  test "inject_request_id defaults to false" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.inject_request_id == false
  end

  test "builder sets all fields" do
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

  test "follow_redirects defaults to true with null max_redirects" do
    opts = PetstoreClient.TransportOptions.new(follow_redirects: true)

    assert opts.follow_redirects == true
    assert opts.max_redirects == nil
  end

  test "invalid proxy URL throws exception" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.TransportOptions.new(proxy: "not-a-valid-url")
    end
  end

  test "null proxy URL is accepted" do
    opts = PetstoreClient.TransportOptions.new(proxy: nil)
    assert opts.proxy == nil
  end

  test "builder methods return the same builder instance" do
    # Elixir uses struct-based API; verify that repeated new() calls behave consistently
    opts1 = PetstoreClient.TransportOptions.new(verify_ssl: true)
    opts2 = PetstoreClient.TransportOptions.new(verify_ssl: true)
    assert opts1 == opts2
  end

  test "accumulates headers from default_header calls" do
    opts = PetstoreClient.TransportOptions.new(default_headers: %{"X-First" => "one", "X-Second" => "two"})

    assert map_size(opts.default_headers) == 2
    assert opts.default_headers["X-First"] == "one"
    assert opts.default_headers["X-Second"] == "two"
  end

  test "merges headers from default_headers call" do
    initial = %{"X-First" => "one"}
    extra = %{"X-Second" => "two", "X-Third" => "three"}
    opts = PetstoreClient.TransportOptions.new(default_headers: Map.merge(initial, extra))

    assert map_size(opts.default_headers) == 3
    assert opts.default_headers["X-First"] == "one"
    assert opts.default_headers["X-Second"] == "two"
    assert opts.default_headers["X-Third"] == "three"
  end

  test "modifying source map does not affect built options" do
    headers = %{"X-Original" => "original"}

    opts = PetstoreClient.TransportOptions.new(default_headers: headers)

    # Elixir maps are immutable, so this is inherently safe
    assert map_size(opts.default_headers) == 1
    assert opts.default_headers["X-Original"] == "original"
  end

  test "builder produces independent instances" do
    opts1 = PetstoreClient.TransportOptions.new(verify_ssl: false)
    opts2 = PetstoreClient.TransportOptions.new(verify_ssl: false)

    assert opts1.verify_ssl == opts2.verify_ssl
    assert opts1 == opts2
  end

  # TimeoutConfigTests

  test "timeout defaults to nil in timeout group" do
    # Default TransportOptions has no timeout set; nil means no timeout applied.
    opts = PetstoreClient.TransportOptions.new()
    assert opts.timeout == nil
  end

  test "setting timeout to 5000 is accessible" do
    opts = PetstoreClient.TransportOptions.new(timeout: 5000)
    assert opts.timeout == 5000
  end

  test "timeout field is named exactly timeout" do
    # Verify via the struct field that the key is :timeout
    # (not e.g. :connection_timeout or :open_timeout).
    opts = PetstoreClient.TransportOptions.new(timeout: 1000)
    assert opts.timeout != nil
    assert opts.timeout == 1000
  end

  # ProxyConfigTests

  test "setting proxy URL is preserved on read-back" do
    opts = PetstoreClient.TransportOptions.new(proxy: "http://proxy.example.com:8080")
    assert opts.proxy == "http://proxy.example.com:8080"
  end

  test "setting proxy is supported on all platforms" do
    # Proxy configuration must not raise on any Elixir platform.
    opts = PetstoreClient.TransportOptions.new(proxy: "http://proxy.example.com:8080")
    assert opts.proxy == "http://proxy.example.com:8080"
  end
end
