defmodule PetstoreClient.TransportOptionsTest do
  use ExUnit.Case, async: true

  test "bhv options 043 verify_ssl defaults to true" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.verify_ssl == true
  end

  test "bhv options 018 ca_cert_path defaults to null" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.ca_cert_path == nil
  end

  test "bhv misc 051 an option the transport cannot apply is refused rather than ignored" do
    assert_raise KeyError, fn -> PetstoreClient.TransportOptions.new(no_such_option: 1) end
  end

  test "bhv options 052 a proxy URL carrying credentials is accepted" do
    credentials = "user:" <> "pass"
    proxy = "http://" <> credentials <> "@proxy.example.com:8080"

    opts = PetstoreClient.TransportOptions.new(proxy: proxy)

    assert opts.proxy == proxy

    assert PetstoreClient.DefaultApiClient.proxy_auth_header(
             PetstoreClient.DefaultApiClient.new(opts)
           ) ==
             "Basic " <> Base.encode64(credentials)
  end

  test "bhv options 032 proxy defaults to null" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.proxy == nil
  end

  test "bhv options 038 timeout defaults to 10_000 ms" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.timeout == 10_000
  end

  test "bhv options 020 follow_redirects defaults to true" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.follow_redirects == true
  end

  test "bhv options 025 max_redirects defaults to null" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.max_redirects == nil
  end

  test "bhv options 041 user_agent defaults to non empty string" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.user_agent != ""
    assert opts.user_agent == "petstore_client/1.0.0 (elixir)"
  end

  test "bhv options 019 default_headers defaults to empty map" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.default_headers == %{}
  end

  test "bhv options 022 inject_request_id defaults to false" do
    opts = PetstoreClient.TransportOptions.new()
    assert opts.inject_request_id == false
  end

  test "bhv options 017 builder sets all fields" do
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

  test "bhv options 021 follow_redirects defaults to true with null max_redirects" do
    opts = PetstoreClient.TransportOptions.new(follow_redirects: true)

    assert opts.follow_redirects == true
    assert opts.max_redirects == nil
  end

  test "bhv options 027 max_retries given to new/1 reaches the client, and 0 disables retries" do
    client =
      PetstoreClient.DefaultApiClient.new(PetstoreClient.TransportOptions.new(max_retries: 0))

    assert client.transport_options.max_retries == 0
  end

  test "bhv options 010 a proxy URL with an unterminated IPv6 literal is rejected with its reason" do
    error =
      assert_raise ArgumentError, fn ->
        PetstoreClient.TransportOptions.new(proxy: "http://[::1:3128")
      end

    assert error.message == "Invalid proxy URL (unterminated IPv6 literal): http://[::1:3128"
  end

  test "bhv options 031 a proxy URL is rejected with the exact message for its reason" do
    for {url, message} <- [
          {"proxy.example.com:8080",
           "Invalid proxy URL (must use http or https scheme): proxy.example.com:8080"},
          {"http://", "Invalid proxy URL (missing host): http://"}
        ] do
      error =
        assert_raise ArgumentError, fn ->
          PetstoreClient.TransportOptions.new(proxy: url)
        end

      assert error.message == message
    end
  end

  test "bhv options 042 user_agent defaults to the package name, its version and the language" do
    user_agent = PetstoreClient.TransportOptions.new().user_agent

    assert String.starts_with?(user_agent, "petstore_client/")
    assert String.ends_with?(user_agent, " (elixir)")
  end

  test "bhv options 026 max_retries defaults to 2 and 0 turns retries off" do
    assert PetstoreClient.TransportOptions.new().max_retries == 2
    assert PetstoreClient.TransportOptions.new(max_retries: 0).max_retries == 0
    assert PetstoreClient.TransportOptions.new(max_retries: 5).max_retries == 5
  end

  test "bhv options 007 a negative or non-integer max_retries is rejected" do
    for bad <- [-1, 1.5, nil, "2"] do
      err =
        assert_raise ArgumentError, fn ->
          PetstoreClient.TransportOptions.new(max_retries: bad)
        end

      assert err.message ==
               "Invalid max_retries: must be a non-negative integer (got #{inspect(bad)})"
    end
  end

  test "bhv options 045 a negative max_redirects is rejected at configuration" do
    assert_raise ArgumentError,
                 "Invalid max_redirects: must be a non-negative integer (got -1)",
                 fn -> PetstoreClient.TransportOptions.new(max_redirects: -1) end
  end

  test "bhv options 046 constructing transport options directly with a non-http proxy scheme is rejected" do
    assert_raise ArgumentError, ~r/must use http or https scheme/, fn ->
      PetstoreClient.TransportOptions.new(proxy: "socks5://proxy.example.com:1080")
    end
  end

  test "bhv options 047 a negative timeout is rejected at configuration" do
    assert_raise ArgumentError,
                 "Invalid timeout: must be a positive number of milliseconds (got -5)",
                 fn -> PetstoreClient.TransportOptions.new(timeout: -5) end
  end

  test "bhv options 051 a proxy URL with a scheme other than http or https is rejected" do
    assert_raise ArgumentError,
                 "Invalid proxy URL (must use http or https scheme): ftp://proxy.example.com:21",
                 fn ->
                   PetstoreClient.TransportOptions.new(proxy: "ftp://proxy.example.com:21")
                 end
  end

  test "bhv options 033 a zero or negative timeout is rejected with the canonical message" do
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

  test "bhv options 024 invalid proxy URL throws exception" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.TransportOptions.new(proxy: "not-a-valid-url")
    end
  end

  test "bhv options 068 a SOCKS proxy scheme is rejected at construction" do
    for scheme <- ["socks", "socks4", "socks5"] do
      url = "#{scheme}://proxy.example.com:1080"

      error =
        assert_raise ArgumentError, fn ->
          PetstoreClient.TransportOptions.new(proxy: url)
        end

      assert error.message == "Invalid proxy URL (must use http or https scheme): #{url}"
    end
  end

  test "bhv options 011 a proxy URL with another scheme, no host or no content is rejected with its reason" do
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

  test "bhv options 008 a rejected proxy URL never echoes its user and password" do
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

  test "bhv options 014 an http and an https proxy URL are accepted" do
    for url <- ["http://proxy.example.com:3128", "https://proxy.example.com:3129"] do
      assert PetstoreClient.TransportOptions.new(proxy: url).proxy == url
    end
  end

  test "bhv options 009 a proxy URL with an IPv6 literal host is accepted and kept verbatim" do
    assert PetstoreClient.TransportOptions.new(proxy: "http://[::1]:3128").proxy ==
             "http://[::1]:3128"
  end

  test "bhv options 044 the README names the proxy option of new/1 as the SOCKS setter" do
    readme = File.read!("README.md")

    assert readme =~ "The `proxy:` option of `TransportOptions.new/1` accepts only"
    refute readme =~ "TransportOptions.proxy()"
  end

  test "bhv options 030 null proxy URL is accepted" do
    opts = PetstoreClient.TransportOptions.new(proxy: nil)
    assert opts.proxy == nil
  end

  test "bhv options 015 builder methods return the same builder instance" do
    # Elixir uses struct-based API; verify that repeated new() calls behave consistently
    opts1 = PetstoreClient.TransportOptions.new(verify_ssl: true)
    opts2 = PetstoreClient.TransportOptions.new(verify_ssl: true)
    assert opts1 == opts2
  end

  test "bhv options 012 accumulates headers from default_header calls" do
    opts =
      PetstoreClient.TransportOptions.new(
        default_headers: %{"X-First" => "one", "X-Second" => "two"}
      )

    assert map_size(opts.default_headers) == 2
    assert opts.default_headers["X-First"] == "one"
    assert opts.default_headers["X-Second"] == "two"
  end

  test "bhv options 028 merges headers from default_headers call" do
    initial = %{"X-First" => "one"}
    extra = %{"X-Second" => "two", "X-Third" => "three"}
    opts = PetstoreClient.TransportOptions.new(default_headers: Map.merge(initial, extra))

    assert map_size(opts.default_headers) == 3
    assert opts.default_headers["X-First"] == "one"
    assert opts.default_headers["X-Second"] == "two"
    assert opts.default_headers["X-Third"] == "three"
  end

  test "bhv options 029 modifying source map does not affect built options" do
    headers = %{"X-Original" => "original"}

    opts = PetstoreClient.TransportOptions.new(default_headers: headers)

    # Elixir maps are immutable, so this is inherently safe
    assert map_size(opts.default_headers) == 1
    assert opts.default_headers["X-Original"] == "original"
  end

  test "bhv options 016 builder produces independent instances" do
    opts1 = PetstoreClient.TransportOptions.new(verify_ssl: false)
    opts2 = PetstoreClient.TransportOptions.new(verify_ssl: false)

    assert opts1.verify_ssl == opts2.verify_ssl
    assert opts1 == opts2
  end

  # TimeoutConfigTests

  test "bhv options 039 timeout defaults to 10_000 ms in timeout group" do
    # Default TransportOptions sets a 10-second timeout to prevent hanging requests.
    opts = PetstoreClient.TransportOptions.new()
    assert opts.timeout == 10_000
  end

  test "bhv options 036 setting timeout to 5000 is accessible" do
    opts = PetstoreClient.TransportOptions.new(timeout: 5000)
    assert opts.timeout == 5000
  end

  test "bhv options 040 timeout field is named exactly timeout" do
    # Verify via the struct field that the key is :timeout
    # (not e.g. :connection_timeout or :open_timeout).
    opts = PetstoreClient.TransportOptions.new(timeout: 1000)
    assert opts.timeout != nil
    assert opts.timeout == 1000
  end

  test "bhv options 037 timeout can be explicitly set to nil for no timeout" do
    opts = PetstoreClient.TransportOptions.new(timeout: nil)
    assert opts.timeout == nil
  end

  # ProxyConfigTests

  test "bhv options 035 setting proxy URL is preserved on read-back" do
    opts = PetstoreClient.TransportOptions.new(proxy: "http://proxy.example.com:8080")
    assert opts.proxy == "http://proxy.example.com:8080"
  end

  test "bhv options 034 setting proxy is supported on all platforms" do
    # Proxy configuration must not raise on any Elixir platform.
    opts = PetstoreClient.TransportOptions.new(proxy: "http://proxy.example.com:8080")
    assert opts.proxy == "http://proxy.example.com:8080"
  end

  test "bhv options 023 inspect masks the proxy password and sensitive default headers" do
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

  test "bhv options 058 the injected HTTP client is none unless supplied and a supplied one is kept as the same instance" do
    adapter = fn request -> {request, Req.Response.new(status: 200)} end

    assert PetstoreClient.TransportOptions.new().req_options == []

    built = PetstoreClient.TransportOptions.new(req_options: [adapter: adapter])
    assert Keyword.fetch!(built.req_options, :adapter) === adapter
  end

  test "bhv options 059 a caller-supplied transport hook is unset unless given and is kept unchanged when supplied" do
    options = [
      adapter: fn request -> {request, Req.Response.new(status: 200)} end,
      receive_timeout: 1_000
    ]

    assert PetstoreClient.TransportOptions.new().req_options == []
    assert PetstoreClient.TransportOptions.new(req_options: options).req_options == options
  end

  test "bhv options 060 the builder entry point carries the values set on it and the defaults of a direct build" do
    built = PetstoreClient.TransportOptions.new(timeout: 5_000, max_retries: 4)

    assert built.timeout == 5_000
    assert built.max_retries == 4
    assert PetstoreClient.TransportOptions.new() == %PetstoreClient.TransportOptions{}
  end

  test "bhv options 062 an options object reports the values it was built with and unset fields read back as unset" do
    built = PetstoreClient.TransportOptions.new(verify_ssl: false, max_retries: 4)

    assert built.verify_ssl == false
    assert built.max_retries == 4
    assert built.proxy == nil
    assert built.ca_cert_path == nil
    assert built.req_options == []
  end

  test "bhv options 063 a built options object is unchanged by an update to a copy" do
    built = PetstoreClient.TransportOptions.new(timeout: 5_000)
    updated = %{built | timeout: 1_000, max_retries: 9}

    assert built.timeout == 5_000
    assert built.max_retries == 2
    assert updated.timeout == 1_000
  end

  test "bhv options 064 the printable form of transport options shows the configured maximum number of retries" do
    shown = inspect(PetstoreClient.TransportOptions.new(max_retries: 7))

    assert shown =~ "max_retries: 7"
  end

  test "bhv options 065 the printable form of transport options never shows a proxy password or caller-supplied hook values" do
    opts =
      PetstoreClient.TransportOptions.new(
        proxy: "http://user:s3cret-pass@proxy.example.com:8080",
        req_options: [
          adapter: fn request -> {request, Req.Response.new(status: 200)} end,
          auth: {:bearer, "hook-token-value"}
        ]
      )

    shown = inspect(opts)
    refute shown =~ "s3cret-pass"
    refute shown =~ "hook-token-value"
    assert shown =~ "adapter:"
  end

  test "bhv options 066 the printable form of transport options shows a proxy URL with no credentials exactly as configured" do
    shown = inspect(PetstoreClient.TransportOptions.new(proxy: "http://proxy.example.com:3128"))
    assert shown =~ ~s(proxy: "http://proxy.example.com:3128")
  end

  test "bhv options 067 building transport options directly with a valid proxy keeps it, and with no proxy the proxy is unset" do
    proxy = "http://proxy.example.com:8080"

    direct = %PetstoreClient.TransportOptions{proxy: proxy}
    assert direct.proxy == proxy
    assert PetstoreClient.TransportOptions.new(proxy: proxy).proxy == proxy
    assert PetstoreClient.TransportOptions.new().proxy == nil
  end

  test "bhv redirects 045 supplying req_options that follow redirects on their own is refused with an error" do
    assert_raise ArgumentError, ~r/redirect/, fn ->
      PetstoreClient.TransportOptions.new(req_options: [redirect: true])
    end
  end

  test "bhv langelixir 009 req_options defaults to an empty list" do
    assert PetstoreClient.TransportOptions.new().req_options == []
  end

  test "bhv langelixir 010 req_options carries the caller's Req options" do
    adapter = fn request -> {request, Req.Response.new(status: 200)} end
    opts = PetstoreClient.TransportOptions.new(req_options: [adapter: adapter])

    assert opts.req_options == [adapter: adapter]
  end

  test "bhv langelixir 011 req_options must be a keyword list" do
    assert_raise ArgumentError, ~r/req_options/, fn ->
      PetstoreClient.TransportOptions.new(req_options: %{adapter: :none})
    end
  end

  test "bhv misc 175 credential-bearing header names are recognised in any letter case and ordinary names are not" do
    redacted =
      PetstoreClient.TransportOptions.redact_headers(%{
        "X-API-KEY" => "k1",
        "authorization" => "Bearer t",
        "Accept" => "application/json"
      })

    assert redacted["X-API-KEY"] == "***"
    assert redacted["authorization"] == "***"
    assert redacted["Accept"] == "application/json"
  end

  test "bhv misc 186 a non-keyword HTTP connection value and a ready-made connection with a CA setting are refused when the options are built" do
    assert_raise ArgumentError, ~r/must be a keyword list/, fn ->
      PetstoreClient.TransportOptions.new(req_options: "not-a-connection")
    end

    assert_raise ArgumentError, fn ->
      PetstoreClient.TransportOptions.new(
        ca_cert_path: "/nonexistent/ca.pem",
        req_options: [connect_options: [timeout: 1]]
      )
    end
  end

  test "bhv transport 078 req_options connect_options conflicts with the SDK's own proxy settings" do
    assert_raise ArgumentError, ~r/conflicts with the SDK's own proxy\/TLS settings/, fn ->
      PetstoreClient.TransportOptions.new(
        proxy: "http://proxy:8080",
        req_options: [connect_options: [timeout: 1]]
      )
    end
  end

  test "bhv transport 079 turning off verification with a supplied connect_options client setting fails at construction" do
    assert_raise ArgumentError, ~r/conflicts with the SDK's own proxy\/TLS settings/, fn ->
      PetstoreClient.TransportOptions.new(
        verify_ssl: false,
        req_options: [connect_options: [timeout: 1]]
      )
    end
  end

  test "bhv transport 080 a proxy the supplied finch pool cannot apply fails at construction" do
    assert_raise ArgumentError, ~r/conflicts with the SDK's own proxy\/TLS settings/, fn ->
      PetstoreClient.TransportOptions.new(
        proxy: "http://proxy:8080",
        req_options: [finch: MyFinch]
      )
    end
  end

  test "bhv lifecycle 041 req_options finch conflicts with verify_ssl false" do
    assert_raise ArgumentError, ~r/conflicts with the SDK's own proxy\/TLS settings/, fn ->
      PetstoreClient.TransportOptions.new(verify_ssl: false, req_options: [finch: MyFinch])
    end
  end

  test "bhv misc 042 a supplied connection with verification turned off is refused and the error names verify_ssl" do
    assert_raise ArgumentError, ~r/verify_ssl/, fn ->
      PetstoreClient.TransportOptions.new(verify_ssl: false, req_options: [finch: MyFinch])
    end
  end

  test "bhv lifecycle 024 a supplied client conflicts with the SDK's proxy and TLS settings" do
    assert_raise ArgumentError, ~r/conflicts with the SDK's own proxy\/TLS settings/, fn ->
      PetstoreClient.TransportOptions.new(
        proxy: "http://proxy:8080",
        req_options: [finch: MyFinch]
      )
    end

    assert_raise ArgumentError, ~r/conflicts with the SDK's own proxy\/TLS settings/, fn ->
      PetstoreClient.TransportOptions.new(
        verify_ssl: false,
        req_options: [connect_options: [timeout: 1]]
      )
    end
  end

  test "bhv langelixir 012 req_options connect_options is accepted when the SDK sets no proxy or TLS option" do
    opts = PetstoreClient.TransportOptions.new(req_options: [connect_options: [timeout: 1]])

    assert opts.req_options == [connect_options: [timeout: 1]]
  end
end
