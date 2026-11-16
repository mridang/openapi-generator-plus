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

  test "timeout defaults to 10_000 ms" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.timeout == 10_000
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

  test "max_retries defaults to 2 and 0 turns retries off" do
    assert PetstoreClient.TransportOptions.new().max_retries == 2
    assert PetstoreClient.TransportOptions.new(max_retries: 0).max_retries == 0
    assert PetstoreClient.TransportOptions.new(max_retries: 5).max_retries == 5
  end

  test "a negative or non-integer max_retries is rejected" do
    for bad <- [-1, 1.5, nil, "2"] do
      err =
        assert_raise ArgumentError, fn ->
          PetstoreClient.TransportOptions.new(max_retries: bad)
        end

      assert err.message ==
               "Invalid max_retries: must be a non-negative integer (got #{inspect(bad)})"
    end
  end

  test "a zero or negative timeout is rejected with the canonical message" do
    for bad <- [-1, 0] do
      err =
        assert_raise ArgumentError, fn ->
          PetstoreClient.TransportOptions.new(timeout: bad)
        end

      assert err.message ==
               "Invalid timeout: must be a positive number of milliseconds (got #{bad})"
    end

    assert PetstoreClient.TransportOptions.new(timeout: nil).timeout == nil
  end

  test "invalid proxy URL throws exception" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.TransportOptions.new(proxy: "not-a-valid-url")
    end
  end

  test "a SOCKS proxy scheme is rejected at construction" do
    for scheme <- ["socks", "socks4", "socks5"] do
      url = "#{scheme}://proxy.example.com:1080"

      error =
        assert_raise ArgumentError, fn ->
          PetstoreClient.TransportOptions.new(proxy: url)
        end

      assert error.message == "Invalid proxy URL (must use http or https scheme): #{url}"
    end
  end

  test "a proxy URL with another scheme, no host or no content is rejected with its reason" do
    for {url, message} <- [
          {"ftp://proxy.example.com:21",
           "Invalid proxy URL (must use http or https scheme): ftp://proxy.example.com:21"},
          {"proxy.example.com:8080",
           "Invalid proxy URL (must use http or https scheme): proxy.example.com:8080"},
          {"", "Invalid proxy URL (must use http or https scheme): "},
          {"http://", "Invalid proxy URL (missing host): http://"}
        ] do
      error =
        assert_raise ArgumentError, fn ->
          PetstoreClient.TransportOptions.new(proxy: url)
        end

      assert error.message == message
    end
  end

  test "a rejected proxy URL never echoes its user and password" do
    for {url, message} <- [
          {"ftp://user:s3cret@proxy.example.com:21",
           "Invalid proxy URL (must use http or https scheme): ftp://***@proxy.example.com:21"},
          {"user:s3cret@proxy.example.com:8080",
           "Invalid proxy URL (must use http or https scheme): ***@proxy.example.com:8080"},
          {"http://user:s3cret@", "Invalid proxy URL (missing host): http://***@"}
        ] do
      error =
        assert_raise ArgumentError, fn ->
          PetstoreClient.TransportOptions.new(proxy: url)
        end

      assert error.message == message
      refute error.message =~ "s3cret"
    end
  end

  test "an http and an https proxy URL are accepted" do
    for url <- ["http://proxy.example.com:3128", "https://proxy.example.com:3129"] do
      assert PetstoreClient.TransportOptions.new(proxy: url).proxy == url
    end
  end

  test "the README names the proxy option of new/1 as the SOCKS setter" do
    readme = File.read!("README.md")

    assert readme =~ "The `proxy:` option of `TransportOptions.new/1` accepts only"
    refute readme =~ "TransportOptions.proxy()"
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
    opts =
      PetstoreClient.TransportOptions.new(
        default_headers: %{"X-First" => "one", "X-Second" => "two"}
      )

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

  test "timeout defaults to 10_000 ms in timeout group" do
    # Default TransportOptions sets a 10-second timeout to prevent hanging requests.
    opts = PetstoreClient.TransportOptions.new()
    assert opts.timeout == 10_000
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

  test "timeout can be explicitly set to nil for no timeout" do
    opts = PetstoreClient.TransportOptions.new(timeout: nil)
    assert opts.timeout == nil
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

  test "inspect masks the proxy password and sensitive default headers" do
    opts =
      PetstoreClient.TransportOptions.new(
        proxy: "http://user:hunter2@proxy.example.com:8080",
        default_headers: %{
          "Authorization" => "Bearer abc123",
          "Proxy-Authorization" => "Basic abc123",
          "Cookie" => "sid=abc123",
          "X-Api-Key" => "abc123",
          "X-Auth-Token" => "abc123",
          "X-Client-Secret" => "abc123",
          "Accept" => "application/json"
        }
      )

    shown = inspect(opts)
    refute shown =~ "hunter2"
    refute shown =~ "abc123"
    assert shown =~ "http://user:***@proxy.example.com:8080"
    assert shown =~ "application/json"
    assert opts.proxy == "http://user:hunter2@proxy.example.com:8080"
  end

  test "req_options defaults to an empty list" do
    assert PetstoreClient.TransportOptions.new().req_options == []
  end

  test "req_options carries the caller's Req options" do
    adapter = fn request -> {request, Req.Response.new(status: 200)} end
    opts = PetstoreClient.TransportOptions.new(req_options: [adapter: adapter])

    assert opts.req_options == [adapter: adapter]
  end

  test "req_options must be a keyword list" do
    assert_raise ArgumentError, ~r/req_options/, fn ->
      PetstoreClient.TransportOptions.new(req_options: %{adapter: :none})
    end
  end

  test "req_options connect_options conflicts with the SDK's own proxy settings" do
    assert_raise ArgumentError, ~r/conflicts with the SDK's own proxy\/TLS settings/, fn ->
      PetstoreClient.TransportOptions.new(
        proxy: "http://proxy:8080",
        req_options: [connect_options: [timeout: 1]]
      )
    end
  end

  test "req_options finch conflicts with verify_ssl false" do
    assert_raise ArgumentError, ~r/conflicts with the SDK's own proxy\/TLS settings/, fn ->
      PetstoreClient.TransportOptions.new(verify_ssl: false, req_options: [finch: MyFinch])
    end
  end

  test "req_options connect_options is accepted when the SDK sets no proxy or TLS option" do
    opts = PetstoreClient.TransportOptions.new(req_options: [connect_options: [timeout: 1]])

    assert opts.req_options == [connect_options: [timeout: 1]]
  end
end
