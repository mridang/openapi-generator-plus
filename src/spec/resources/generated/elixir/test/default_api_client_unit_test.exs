defmodule PetstoreClient.DefaultApiClientUnitTest do
  use ExUnit.Case, async: true

  # ── Helpers ────────────────────────────────────────────────────────────────

  # Starts a TCP server that handles ONE request and returns the given response.
  # Returns the base URL.
  defp start_server(status, content_type, body, extra_headers \\ []) do
    {:ok, listen_socket} =
      :gen_tcp.listen(0, [:binary, packet: :raw, active: false, reuseaddr: true])

    {:ok, port} = :inet.port(listen_socket)
    base_url = "http://127.0.0.1:#{port}"
    caller = self()

    spawn(fn ->
      case :gen_tcp.accept(listen_socket, 5000) do
        {:ok, socket} ->
          # Drain the incoming request
          :gen_tcp.recv(socket, 0, 2000)

          status_text = if status == 200, do: "OK", else: to_string(status)
          extra = Enum.map_join(extra_headers, "", fn {k, v} -> "#{k}: #{v}\r\n" end)

          response =
            "HTTP/1.1 #{status} #{status_text}\r\n" <>
              "Content-Type: #{content_type}\r\n" <>
              "Content-Length: #{byte_size(body)}\r\n" <>
              "Connection: close\r\n" <>
              extra <>
              "\r\n" <>
              body

          :gen_tcp.send(socket, response)
          :gen_tcp.close(socket)

        _ ->
          :ok
      end

      :gen_tcp.close(listen_socket)
      send(caller, :server_done)
    end)

    # Small delay to ensure socket is listening
    Process.sleep(20)
    base_url
  end

  # Starts a TCP server that captures request headers and sends them back as a
  # JSON object (lowercase keys). Returns {base_url, port}.
  defp start_header_capture_server do
    {:ok, listen_socket} =
      :gen_tcp.listen(0, [:binary, packet: :raw, active: false, reuseaddr: true])

    {:ok, port} = :inet.port(listen_socket)
    caller = self()

    spawn(fn ->
      case :gen_tcp.accept(listen_socket, 5000) do
        {:ok, socket} ->
          {:ok, data} = :gen_tcp.recv(socket, 0, 5000)
          headers = parse_request_headers(data)
          send(caller, {:captured_headers, headers})

          body = Jason.encode!(headers)

          response =
            "HTTP/1.1 200 OK\r\n" <>
              "Content-Type: application/json\r\n" <>
              "Content-Length: #{byte_size(body)}\r\n" <>
              "Connection: close\r\n" <>
              "\r\n" <>
              body

          :gen_tcp.send(socket, response)
          :gen_tcp.close(socket)

        _ ->
          send(caller, {:captured_headers, %{}})
      end

      :gen_tcp.close(listen_socket)
    end)

    Process.sleep(20)
    {"http://127.0.0.1:#{port}", port}
  end

  # Parses the raw HTTP request text and returns headers as a lowercase map.
  defp parse_request_headers(request_text) do
    request_text
    |> String.split("\r\n")
    |> Enum.drop(1)
    |> Enum.take_while(fn line -> line != "" end)
    |> Enum.reduce(%{}, fn line, acc ->
      case String.split(line, ": ", parts: 2) do
        [key, value] -> Map.put(acc, String.downcase(key), value)
        _ -> acc
      end
    end)
  end

  # ── Basic HTTP tests ────────────────────────────────────────────────────────

  test "bhv transport 054 sends GET request and returns response" do
    base_url = start_server(200, "application/json", ~s({"method":"GET"}))
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/echo", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "GET")
  end

  test "bhv transport 040 a custom adapter in req_options receives the request with the SDK defaults on top" do
    test_pid = self()

    adapter = fn request ->
      send(test_pid, {:adapter_request, request})

      {request,
       Req.Response.new(
         status: 200,
         body: ~s({"ok":true}),
         headers: [{"content-type", "application/json"}]
       )}
    end

    transport =
      PetstoreClient.TransportOptions.new(
        req_options: [adapter: adapter, retry: :transient],
        user_agent: "Custom/1.0"
      )

    client = PetstoreClient.DefaultApiClient.new(transport)

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "http://custom.invalid/echo",
        %{},
        nil
      )

    assert response.status_code == 200
    assert_receive {:adapter_request, request}
    assert request.url.host == "custom.invalid"
    assert Req.Request.get_header(request, "user-agent") == ["Custom/1.0"]
    assert request.options.retry == false
    assert request.options.redirect == false
  end

  # F1078: close is graceful. A request admitted before close finishes (and the
  # follow-up requests it makes, a token fetch or a retry, still go out); one
  # that starts after close raises the canonical error; the release of what the
  # SDK started is claimed by exactly one caller, once nothing is in flight.
  test "bhv lifecycle 019 close lets an admitted request finish, refuses a new one and lets one caller claim the release" do
    adapter = fn request -> {request, Req.Response.new(status: 200, body: "")} end
    transport = PetstoreClient.TransportOptions.new(req_options: [adapter: adapter])
    client = PetstoreClient.DefaultApiClient.new(transport)

    refute PetstoreClient.DefaultApiClient.claim_release(client)

    assert PetstoreClient.DefaultApiClient.begin_request(client) == :ok
    assert PetstoreClient.DefaultApiClient.close(client) == :ok
    assert PetstoreClient.DefaultApiClient.close(client) == :ok
    refute PetstoreClient.DefaultApiClient.claim_release(client)

    assert %{status_code: 200} =
             PetstoreClient.DefaultApiClient.send_request(
               client,
               :get,
               "http://custom.invalid/follow-up",
               %{},
               nil
             )

    error =
      assert_raise RuntimeError, fn -> PetstoreClient.DefaultApiClient.begin_request(client) end

    assert error.message == "ApiClient has been closed and can no longer send requests"

    assert PetstoreClient.DefaultApiClient.finish_request(client) == :ok
    assert PetstoreClient.DefaultApiClient.claim_release(client)
    refute PetstoreClient.DefaultApiClient.claim_release(client)

    assert_raise RuntimeError, fn ->
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "http://custom.invalid/after",
        %{},
        nil
      )
    end
  end

  test "bhv lifecycle 022 the release is not claimable while any of several admitted requests is in flight" do
    client = PetstoreClient.DefaultApiClient.new()

    assert PetstoreClient.DefaultApiClient.begin_request(client) == :ok
    assert PetstoreClient.DefaultApiClient.begin_request(client) == :ok
    assert PetstoreClient.DefaultApiClient.close(client) == :ok

    assert PetstoreClient.DefaultApiClient.finish_request(client) == :ok
    refute PetstoreClient.DefaultApiClient.claim_release(client)

    assert PetstoreClient.DefaultApiClient.finish_request(client) == :ok
    assert PetstoreClient.DefaultApiClient.claim_release(client)
  end

  test "bhv transport 028 a deadline handed to send_request replaces the transport timeout for that call" do
    adapter = fn request ->
      Process.sleep(500)
      {request, Req.Response.new(status: 200, body: "")}
    end

    transport =
      PetstoreClient.TransportOptions.new(req_options: [adapter: adapter], timeout: 60_000)

    client = PetstoreClient.DefaultApiClient.new(transport)
    deadline = System.monotonic_time(:millisecond) + 100

    assert_raise PetstoreClient.Errors.NetworkTimeoutError, fn ->
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "http://custom.invalid/echo",
        %{},
        nil,
        deadline: deadline
      )
    end
  end

  test "bhv serializationwire 052 sends POST with JSON body" do
    base_url = start_server(200, "application/json", ~s({"method":"POST","body":"key"}))
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :post,
        "#{base_url}/echo",
        %{"Content-Type" => "application/json"},
        ~s({"key":"value"})
      )

    assert response.status_code == 200
    assert String.contains?(response.body, "POST")
    assert String.contains?(response.body, "key")
  end

  test "bhv transport 051 returns response headers" do
    base_url = start_server(200, "application/json", "ok", [{"X-Test-Header", "test-value"}])
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/echo", %{}, nil)

    assert response.status_code == 200

    header_value =
      response.headers
      |> Enum.find(fn {k, _} -> String.downcase(k) == "x-test-header" end)
      |> case do
        {_, v} -> v
        nil -> nil
      end

    assert header_value == "test-value"
  end

  test "bhv transport 050 returns non-2xx status code" do
    base_url = start_server(404, "text/plain", "not found")
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "#{base_url}/not-found",
        %{},
        nil
      )

    assert response.status_code == 404
    assert response.body == "not found"
  end

  test "bhv transport 055 sends PUT request" do
    base_url = start_server(200, "application/json", ~s({"method":"PUT"}))
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :put,
        "#{base_url}/echo",
        %{},
        "update"
      )

    assert response.status_code == 200
    assert String.contains?(response.body, "PUT")
  end

  test "bhv transport 053 sends DELETE request" do
    base_url = start_server(200, "application/json", ~s({"method":"DELETE"}))
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(client, :delete, "#{base_url}/echo", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "DELETE")
  end

  test "bhv serializationwire 051 returns JSON body for vendor JSON content type" do
    base_url = start_server(200, "application/vnd.api+json", ~s({"format":"vendor"}))
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "#{base_url}/vendor-json",
        %{},
        nil
      )

    assert response.status_code == 200
    assert String.contains?(response.body, "vendor")
  end

  test "bhv transport 044 joins multi-value response headers" do
    # Manually build a TCP server that sends two X-Custom-Value headers
    {:ok, listen_socket} =
      :gen_tcp.listen(0, [:binary, packet: :raw, active: false, reuseaddr: true])

    {:ok, port} = :inet.port(listen_socket)

    spawn(fn ->
      {:ok, socket} = :gen_tcp.accept(listen_socket, 5000)
      :gen_tcp.recv(socket, 0, 2000)

      response =
        "HTTP/1.1 200 OK\r\n" <>
          "X-Custom-Value: val1\r\n" <>
          "X-Custom-Value: val2\r\n" <>
          "Content-Length: 2\r\n" <>
          "Connection: close\r\n" <>
          "\r\n" <>
          "ok"

      :gen_tcp.send(socket, response)
      :gen_tcp.close(socket)
      :gen_tcp.close(listen_socket)
    end)

    Process.sleep(20)

    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "http://127.0.0.1:#{port}/multi-header",
        %{},
        nil
      )

    assert response.status_code == 200

    header_value =
      response.headers
      |> Enum.find(fn {k, _} -> String.downcase(k) == "x-custom-value" end)
      |> case do
        {_, v} -> v
        nil -> nil
      end

    assert header_value != nil
    assert String.contains?(header_value, "val1") and String.contains?(header_value, "val2")
  end

  # ── Header injection tests ──────────────────────────────────────────────────

  test "bhv transport 041 injects custom User-Agent header" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(user_agent: "MyApp/1.0")
    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    assert headers["user-agent"] == "MyApp/1.0"
  end

  test "bhv transport 042 injects default User-Agent when not explicitly set" do
    {base_url, _port} = start_header_capture_server()
    client = PetstoreClient.DefaultApiClient.new()
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    assert headers["user-agent"] == "petstore_client/1.0.0 (elixir)"
  end

  test "bhv transport 043 injects X-Request-ID when enabled" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(inject_request_id: true)
    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    assert headers["x-request-id"] != nil
    assert String.length(headers["x-request-id"]) > 0
  end

  test "bhv transport 036 does not inject X-Request-ID when disabled" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(inject_request_id: false)
    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    refute Map.has_key?(headers, "x-request-id")
  end

  test "bhv options 006 does not override caller-provided X-Request-ID" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(inject_request_id: true)
    client = PetstoreClient.DefaultApiClient.new(transport)

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      "#{base_url}/test",
      %{"X-Request-ID" => "caller-id"},
      nil
    )

    assert_receive {:captured_headers, headers}, 5000
    assert headers["x-request-id"] == "caller-id"
  end

  test "bhv transport 047 a caller header replaces a default header of another casing" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(default_headers: %{"accept" => "text/plain"})
    client = PetstoreClient.DefaultApiClient.new(transport)

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      "#{base_url}/test",
      %{"Accept" => "application/json"},
      nil
    )

    assert_receive {:captured_headers, headers}, 5000
    assert headers["accept"] == "application/json"
  end

  test "bhv transport 026 a caller's lower-case accept-encoding suppresses the SDK's own" do
    {base_url, _port} = start_header_capture_server()
    client = PetstoreClient.DefaultApiClient.new()

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      "#{base_url}/test",
      %{"accept-encoding" => "identity"},
      nil
    )

    assert_receive {:captured_headers, headers}, 5000
    assert headers["accept-encoding"] == "identity"
  end

  test "bhv redirects 016 no_default_headers keeps the transport default headers off the call" do
    {base_url, _port} = start_header_capture_server()

    transport =
      PetstoreClient.TransportOptions.new(default_headers: %{"X-Api-Key" => "for-the-api"})

    client = PetstoreClient.DefaultApiClient.new(transport)

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      "#{base_url}/test",
      %{},
      nil,
      no_default_headers: true
    )

    assert_receive {:captured_headers, headers}, 5000
    refute Map.has_key?(headers, "x-api-key")
  end

  test "bhv transport 038 generates unique X-Request-ID per request" do
    # Need two separate header capture servers for two requests
    {base_url1, _} = start_header_capture_server()
    {base_url2, _} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(inject_request_id: true)
    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url1}/test", %{}, nil)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url2}/test", %{}, nil)
    assert_receive {:captured_headers, headers1}, 5000
    assert_receive {:captured_headers, headers2}, 5000
    assert headers1["x-request-id"] != headers2["x-request-id"]
  end

  test "bhv transport 039 includes transport-level default headers" do
    {base_url, _port} = start_header_capture_server()

    transport =
      PetstoreClient.TransportOptions.new(default_headers: %{"X-Custom" => "custom-value"})

    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    assert headers["x-custom"] == "custom-value"
  end

  test "bhv options 005 caller headers override transport default headers" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(default_headers: %{"Accept" => "text/plain"})
    client = PetstoreClient.DefaultApiClient.new(transport)

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      "#{base_url}/test",
      %{"Accept" => "application/json"},
      nil
    )

    assert_receive {:captured_headers, headers}, 5000
    assert headers["accept"] == "application/json"
  end

  # ── Redirect guard tests (3.1 / 3.2 / 3.3) ─────────────────────────────────

  # Issues a single 3xx with a Location: header then closes. Designed for one
  # connection only — callers that need to follow the redirect through must
  # start a second server for the target.
  defp start_redirect_server(status, location) do
    {:ok, listen_socket} =
      :gen_tcp.listen(0, [:binary, packet: :raw, active: false, reuseaddr: true])

    {:ok, port} = :inet.port(listen_socket)
    caller = self()

    spawn(fn ->
      case :gen_tcp.accept(listen_socket, 5000) do
        {:ok, socket} ->
          {:ok, data} = :gen_tcp.recv(socket, 0, 5000)
          headers = parse_request_headers(data)
          send(caller, {:source_headers, headers})

          response =
            "HTTP/1.1 #{status} Found\r\n" <>
              "Location: #{location}\r\n" <>
              "Content-Length: 0\r\n" <>
              "Connection: close\r\n" <>
              "\r\n"

          :gen_tcp.send(socket, response)
          :gen_tcp.close(socket)

        _ ->
          :ok
      end

      :gen_tcp.close(listen_socket)
    end)

    Process.sleep(20)
    {"http://127.0.0.1:#{port}", port}
  end

  # 3.1: the static credential trio (Authorization / Cookie /
  # Proxy-Authorization) is ALWAYS stripped on a cross-origin hop,
  # regardless of whether the spec declared any apiKey-in-header schemes.
  # Non-sensitive headers must survive the hop.
  test "bhv redirects 012 3.1: strips Authorization / Cookie / Proxy-Authorization on cross-origin redirect" do
    # Target server records every header that arrived.
    {target_url, _} = start_header_capture_server()
    # Source server replies with a 302 pointing at the target (different port
    # — that counts as a different origin per scheme+host+port equality).
    {source_url, _} = start_redirect_server(302, "#{target_url}/echo")

    client = PetstoreClient.DefaultApiClient.new()

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      source_url,
      %{
        "Authorization" => "Bearer secret",
        "Cookie" => "session=abc",
        "Proxy-Authorization" => "Basic Zm9v",
        "X-Trace" => "keep"
      },
      nil
    )

    assert_receive {:source_headers, _src_headers}, 5000
    assert_receive {:captured_headers, target_headers}, 5000

    refute Map.has_key?(target_headers, "authorization"),
           "Authorization must be stripped on cross-origin redirect"

    refute Map.has_key?(target_headers, "cookie"),
           "Cookie must be stripped on cross-origin redirect"

    refute Map.has_key?(target_headers, "proxy-authorization"),
           "Proxy-Authorization must be stripped on cross-origin redirect"

    # Non-sensitive headers must survive the hop.
    assert target_headers["x-trace"] == "keep"
  end

  # Answers the first request with a 302 to a relative Location on the same
  # origin, then records the headers of the follow-up request it receives.
  defp start_same_origin_redirect_server do
    {:ok, listen_socket} =
      :gen_tcp.listen(0, [:binary, packet: :raw, active: false, reuseaddr: true])

    {:ok, port} = :inet.port(listen_socket)
    caller = self()

    spawn(fn ->
      {:ok, first} = :gen_tcp.accept(listen_socket, 5000)
      {:ok, _data} = :gen_tcp.recv(first, 0, 5000)

      :gen_tcp.send(
        first,
        "HTTP/1.1 302 Found\r\nLocation: /second\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
      )

      :gen_tcp.close(first)

      {:ok, second} = :gen_tcp.accept(listen_socket, 5000)
      {:ok, data} = :gen_tcp.recv(second, 0, 5000)
      send(caller, {:second_headers, parse_request_headers(data)})

      :gen_tcp.send(
        second,
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}"
      )

      :gen_tcp.close(second)
      :gen_tcp.close(listen_socket)
    end)

    Process.sleep(20)
    "http://127.0.0.1:#{port}"
  end

  test "bhv redirects 013 3.1: keeps Authorization and Cookie on a same-origin redirect" do
    base_url = start_same_origin_redirect_server()
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "#{base_url}/first",
        %{"Authorization" => "Bearer secret", "Cookie" => "session=abc", "X-Trace" => "keep"},
        nil
      )

    assert response.status_code == 200
    assert_receive {:second_headers, headers}, 5000
    assert headers["authorization"] == "Bearer secret"
    assert headers["cookie"] == "session=abc"
    assert headers["x-trace"] == "keep"
  end

  test "3.1: strips any header named like a credential on cross-origin redirect" do
    {target_url, _} = start_header_capture_server()
    {source_url, _} = start_redirect_server(302, "#{target_url}/echo")

    client = PetstoreClient.DefaultApiClient.new()

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      source_url,
      %{
        "X-Tenant-Token" => "t",
        "X-Custom-Auth-Key" => "k",
        "X-Gateway-Secret" => "s",
        "X-Trace" => "keep"
      },
      nil
    )

    assert_receive {:source_headers, _src_headers}, 5000
    assert_receive {:captured_headers, target_headers}, 5000
    refute Map.has_key?(target_headers, "x-tenant-token")
    refute Map.has_key?(target_headers, "x-custom-auth-key")
    refute Map.has_key?(target_headers, "x-gateway-secret")
    assert target_headers["x-trace"] == "keep"
  end

  # 3.1: spec-declared apiKey-in-header names are merged into the
  # cross-origin sensitive-header allowlist at codegen time, so an API key
  # sent in a custom header (e.g. X-API-Key) is stripped on the hop too.
  test "bhv redirects 011 3.1: strips spec-declared apiKey header on cross-origin redirect" do
    api_key_header_names = ["X-API-Key", "X-Internal-Key"]
    api_key_header = List.first(api_key_header_names)

    {target_url, _} = start_header_capture_server()
    {source_url, _} = start_redirect_server(302, "#{target_url}/echo")

    client = PetstoreClient.DefaultApiClient.new()

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      source_url,
      %{api_key_header => "secret-api-key-value"},
      nil
    )

    assert_receive {:source_headers, _src_headers}, 5000
    assert_receive {:captured_headers, target_headers}, 5000
    # The API-key header must be gone on the cross-origin follow-up.
    refute Map.has_key?(target_headers, String.downcase(api_key_header)),
           "API-key header #{api_key_header} leaked on cross-origin redirect"
  end

  test "bhv retries 017 3.3: downgrade? predicate flags HTTPS->HTTP only" do
    # Direct unit test against the predicate. TLS endpoints aren't practical
    # to stand up in a hermetic unit test, so we drive the predicate that
    # the redirect loop uses to make the abort decision.
    assert PetstoreClient.DefaultApiClient.downgrade?(
             "https://api.example.com/x",
             "http://attacker.example/y"
           )

    refute PetstoreClient.DefaultApiClient.downgrade?(
             "https://api.example.com/x",
             "https://api.example.com/y"
           )

    refute PetstoreClient.DefaultApiClient.downgrade?(
             "http://api.example.com/x",
             "http://api.example.com/y"
           )

    refute PetstoreClient.DefaultApiClient.downgrade?(
             "http://api.example.com/x",
             "https://api.example.com/y"
           )

    # Case-insensitive scheme comparison.
    assert PetstoreClient.DefaultApiClient.downgrade?(
             "HTTPS://api.example.com/x",
             "HTTP://api.example.com/y"
           )
  end

  test "3.3: body replay is refused only for a real body on a 307/308 downgrade" do
    https = "https://api.example.com/x"
    http = "http://api.example.com/y"

    assert PetstoreClient.DefaultApiClient.refuse_body_replay?(307, https, http, ~s({"a":1}))
    assert PetstoreClient.DefaultApiClient.refuse_body_replay?(308, https, http, ~s({"a":1}))

    # No body to leak: absent, or the empty string sent to force Content-Length: 0.
    refute PetstoreClient.DefaultApiClient.refuse_body_replay?(307, https, http, nil)
    refute PetstoreClient.DefaultApiClient.refuse_body_replay?(307, https, http, "")
    refute PetstoreClient.DefaultApiClient.refuse_body_replay?(308, https, http, "")

    # The body is dropped on these statuses, and https->https is no downgrade.
    refute PetstoreClient.DefaultApiClient.refuse_body_replay?(302, https, http, "x")
    refute PetstoreClient.DefaultApiClient.refuse_body_replay?(307, https, https, "x")
  end

  test "decode_text_body honours a little-endian BOM on a bare utf-16 label" do
    body = <<0xFF, 0xFE, 0x41, 0x00, 0x42, 0x00>>

    assert PetstoreClient.DefaultApiClient.decode_text_body(body, "text/plain; charset=utf-16") ==
             "AB"
  end

  test "bhv serializationwire 031 decode_text_body honours a big-endian BOM on a bare utf-16 label" do
    body = <<0xFE, 0xFF, 0x00, 0x41, 0x00, 0x42>>

    assert PetstoreClient.DefaultApiClient.decode_text_body(body, "text/plain; charset=utf-16") ==
             "AB"
  end

  test "bhv serializationwire 030 decode_text_body reads a bare utf-16 body without a BOM as big-endian" do
    body = <<0x00, 0x41, 0x00, 0x42>>

    assert PetstoreClient.DefaultApiClient.decode_text_body(body, "text/plain; charset=utf-16") ==
             "AB"
  end

  test "bhv serializationwire 033 decode_text_body decodes windows-1252 including the C1 range" do
    body = <<0x80, 0x20, 0x93, 0x68, 0x69, 0x94, 0x20, 0xE9>>

    for label <- ["windows-1252", "cp1252", "Windows-1252"] do
      assert PetstoreClient.DefaultApiClient.decode_text_body(
               body,
               "text/plain; charset=#{label}"
             ) ==
               "\u20AC \u201Chi\u201D \u00E9"
    end
  end

  test "bhv retries 016 3.3: HTTP->HTTP 307 replay is permitted (no downgrade)" do
    # Sanity check: the guard is strictly HTTPS->HTTP. Pure-HTTP 307 chains
    # must continue to round-trip the body untouched.
    {target_url, _} = start_header_capture_server()
    {source_url, _} = start_redirect_server(307, "#{target_url}/echo")

    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :post,
        source_url,
        %{"Content-Type" => "application/json"},
        ~s({"payload":"x"})
      )

    assert response.status_code == 200
  end

  # redirect-scheme-refusal-silent: a redirect whose Location points at a
  # non-http(s) scheme (file:, javascript:, data:) must raise a typed SDK
  # error rather than silently returning the 3xx response.
  test "bhv redirects 020 redirect to non-http(s) Location raises ApiError" do
    {source_url, _} = start_redirect_server(302, "file:///etc/passwd")

    client = PetstoreClient.DefaultApiClient.new()

    err =
      assert_raise PetstoreClient.Errors.ApiError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, source_url, %{}, nil)
      end

    # The redirect response arrived, so the error keeps its real status.
    assert err.status_code == 302
    assert err.message == "Refusing to follow redirect to non-HTTP(S) URL: file:///etc/passwd"
    refute PetstoreClient.Errors.NetworkError.network_error?(err)
  end

  test "bhv retries 020 the refused body replay across an HTTPS to HTTP redirect names both URLs without credentials" do
    assert PetstoreClient.DefaultApiClient.replay_refusal_message(
             "https://user:pw@api.example.com/a?token=SECRET",
             "http://api.example.com/b?token=SECRET#frag"
           ) ==
             "Refusing to replay request body across HTTPS->HTTP redirect: " <>
               "https://api.example.com/a -> http://api.example.com/b"
  end

  test "bhv redirects 010 a 3xx whose Location is absent or unparseable comes back as the response, never an exception" do
    client = PetstoreClient.DefaultApiClient.new()

    no_location = start_server(302, "text/plain", "")

    assert PetstoreClient.DefaultApiClient.send_request(client, :get, no_location, %{}, nil).status_code ==
             302

    for location <- ["http://[bad", "http:///nohost"] do
      {source_url, _} = start_redirect_server(302, location)

      assert PetstoreClient.DefaultApiClient.send_request(client, :get, source_url, %{}, nil).status_code ==
               302
    end
  end

  test "bhv redirects 019 redirect refusal messages never carry a query-parameter credential" do
    {source_url, _} = start_redirect_server(302, "file:///etc/passwd?api_key=SECRET")

    client = PetstoreClient.DefaultApiClient.new()

    err =
      assert_raise PetstoreClient.Errors.ApiError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, source_url, %{}, nil)
      end

    refute err.message =~ "SECRET"
  end

  test "redact_url drops the query, userinfo and fragment" do
    assert PetstoreClient.DefaultApiClient.redact_url(
             "https://u:p@h.example/pet?api_key=SECRET#frag"
           ) ==
             "https://h.example/pet"

    assert PetstoreClient.DefaultApiClient.redact_url("http://h.example:8080/a/b") ==
             "http://h.example:8080/a/b"
  end

  # A malformed or non-HTTP request URL is a caller mistake: ArgumentError,
  # never a NetworkError.
  test "bhv errors 040 malformed request URL raises ArgumentError" do
    client = PetstoreClient.DefaultApiClient.new()

    for url <- ["http://[::1", "file:///etc/passwd", "not a url"] do
      err =
        assert_raise ArgumentError, fn ->
          PetstoreClient.DefaultApiClient.send_request(client, :get, url, %{}, nil)
        end

      refute PetstoreClient.Errors.OpenAPIError.open_api_error?(err)
    end
  end

  # ca-cert-fail-fast: an explicitly configured CA certificate path that cannot
  # be read or parsed must fail fast at construction rather than silently
  # falling back to the system trust store (security theater). It is a
  # configuration mistake, so it raises ArgumentError, not ApiError.
  test "bhv errors 041 non-existent caCertPath raises ArgumentError at construction" do
    transport = PetstoreClient.TransportOptions.new(ca_cert_path: "/nonexistent/ca.pem")

    err =
      assert_raise ArgumentError, fn ->
        PetstoreClient.DefaultApiClient.new(transport)
      end

    refute PetstoreClient.Errors.OpenAPIError.open_api_error?(err)
  end

  test "bhv errors 039 a caCertPath that is not PEM raises ArgumentError at construction" do
    path = Path.join(System.tmp_dir!(), "garbage-ca-#{System.unique_integer([:positive])}.pem")
    File.write!(path, "this is not a certificate")
    on_exit(fn -> File.rm(path) end)

    transport = PetstoreClient.TransportOptions.new(ca_cert_path: path)

    err =
      assert_raise ArgumentError, fn ->
        PetstoreClient.DefaultApiClient.new(transport)
      end

    assert err.message ==
             "failed to parse CA certificate from #{inspect(path)}: no PEM blocks found or unparseable"

    refute PetstoreClient.Errors.OpenAPIError.open_api_error?(err)
  end

  # A server that answers with a complete header block promising 100 body bytes,
  # sends 5 of them, then either closes the connection or stalls.
  defp start_partial_body_server(:close) do
    start_partial_body_server(fn socket -> :gen_tcp.close(socket) end)
  end

  defp start_partial_body_server(:stall) do
    start_partial_body_server(fn socket ->
      Process.sleep(3_000)
      :gen_tcp.close(socket)
    end)
  end

  defp start_partial_body_server(after_partial) when is_function(after_partial, 1) do
    {:ok, listen_socket} =
      :gen_tcp.listen(0, [:binary, packet: :raw, active: false, reuseaddr: true])

    {:ok, port} = :inet.port(listen_socket)

    spawn(fn ->
      case :gen_tcp.accept(listen_socket, 5000) do
        {:ok, socket} ->
          :gen_tcp.recv(socket, 0, 2000)

          :gen_tcp.send(
            socket,
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\nhello"
          )

          after_partial.(socket)

        _ ->
          :ok
      end

      :gen_tcp.close(listen_socket)
    end)

    Process.sleep(20)
    "http://127.0.0.1:#{port}"
  end

  # A connection that dies after the headers arrived is a NetworkError with no
  # status: the response never completed.
  test "bhv errors 038 a body cut off after the headers raises NetworkError with status 0" do
    base_url = start_partial_body_server(:close)
    client = PetstoreClient.DefaultApiClient.new()

    err =
      assert_raise PetstoreClient.Errors.NetworkError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/cut", %{}, nil)
      end

    assert err.status_code == 0
    assert err.cause != nil
  end

  test "bhv transport 029 a body that stalls after the headers raises NetworkTimeoutError with status 0" do
    base_url = start_partial_body_server(:stall)
    transport = PetstoreClient.TransportOptions.new(timeout: 300)
    client = PetstoreClient.DefaultApiClient.new(transport)

    err =
      assert_raise PetstoreClient.Errors.NetworkTimeoutError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/stall", %{}, nil)
      end

    assert err.status_code == 0
  end

  # network-error: a request that gets no HTTP response (connection refused)
  # raises NetworkError with status 0, keeping the Req exception as `cause`.
  # Req's default transient retry is off, so it fails once and fast.
  test "bhv transport 032 connection refused raises NetworkError" do
    {:ok, socket} = :gen_tcp.listen(0, [:binary, active: false])
    {:ok, port} = :inet.port(socket)
    :ok = :gen_tcp.close(socket)

    client = PetstoreClient.DefaultApiClient.new()
    started = System.monotonic_time(:millisecond)

    err =
      assert_raise PetstoreClient.Errors.NetworkError, fn ->
        PetstoreClient.DefaultApiClient.send_request(
          client,
          :get,
          "http://127.0.0.1:#{port}/refused",
          %{},
          nil
        )
      end

    assert err.status_code == 0
    assert %Req.TransportError{} = err.cause
    assert PetstoreClient.Errors.OpenAPIError.open_api_error?(err)
    assert PetstoreClient.Errors.ApiError.api_error?(err)

    assert System.monotonic_time(:millisecond) - started < 2_000,
           "the request must not be retried"
  end

  # network-timeout-error: a request that exceeds the configured timeout
  # raises NetworkTimeoutError with status 0.
  test "bhv transport 058 timeout raises NetworkTimeoutError" do
    {:ok, socket} = :gen_tcp.listen(0, [:binary, active: false, reuseaddr: true])
    {:ok, port} = :inet.port(socket)

    # Accept and hold the connection without ever answering.
    spawn(fn ->
      {:ok, conn} = :gen_tcp.accept(socket)
      Process.sleep(5_000)
      :gen_tcp.close(conn)
    end)

    transport = PetstoreClient.TransportOptions.new(timeout: 200)
    client = PetstoreClient.DefaultApiClient.new(transport)

    err =
      assert_raise PetstoreClient.Errors.NetworkTimeoutError, fn ->
        PetstoreClient.DefaultApiClient.send_request(
          client,
          :get,
          "http://127.0.0.1:#{port}/slow",
          %{},
          nil
        )
      end

    assert err.status_code == 0
    assert PetstoreClient.Errors.OpenAPIError.open_api_error?(err)
    # A NetworkTimeoutError is a NetworkError, which is an ApiError.
    assert PetstoreClient.Errors.NetworkError.network_error?(err)
    assert PetstoreClient.Errors.ApiError.api_error?(err)
  end

  # Cancellation is the process exit that ends the call: a caller shut down
  # while its request is in flight ends with no reply, and the SDK never
  # surfaces an error of its own for it.
  test "cancelling an in-flight call ends the caller without an SDK error" do
    {:ok, socket} = :gen_tcp.listen(0, [:binary, active: false, reuseaddr: true])
    {:ok, port} = :inet.port(socket)
    test_pid = self()

    # Accept and hold the connection without ever answering.
    spawn(fn ->
      {:ok, conn} = :gen_tcp.accept(socket)
      send(test_pid, :request_accepted)
      Process.sleep(5_000)
      :gen_tcp.close(conn)
    end)

    client =
      PetstoreClient.DefaultApiClient.new(PetstoreClient.TransportOptions.new(timeout: 30_000))

    task =
      Task.async(fn ->
        PetstoreClient.DefaultApiClient.send_request(
          client,
          :get,
          "http://127.0.0.1:#{port}/slow",
          %{},
          nil
        )
      end)

    assert_receive :request_accepted, 5_000
    ref = task.ref
    assert Task.shutdown(task, :brutal_kill) == nil
    refute_receive {^ref, _reply}, 100
    :gen_tcp.close(socket)
  end

  # The one `:timeout` option arms four separate deadlines, and each of them
  # must classify as NetworkTimeoutError. Three are Mint/Req timeouts that
  # arrive as `%Req.TransportError{reason: :timeout}`; the pool checkout is a
  # `%Finch.Error{reason: :pool_timeout}`, which used to miss the transport
  # clause entirely and surface the same expired deadline as a NetworkError.
  test "every configured deadline classifies as NetworkTimeoutError" do
    for cause <- [
          %Req.TransportError{reason: :timeout},
          %Finch.Error{reason: :pool_timeout}
        ] do
      err = PetstoreClient.DefaultApiClient.classify_transport_error(cause)

      assert %PetstoreClient.Errors.NetworkTimeoutError{} = err
      assert err.status_code == 0
      assert err.cause == cause
      assert PetstoreClient.Errors.NetworkError.network_error?(err)
    end
  end

  # Every deadline this client is allowed to set is armed from the single
  # `:timeout` option: the idle timer and the connect handshake. Leaving one of
  # them at the library's own default is what makes the same expired budget
  # surface under two different types.
  test "the timeout option arms every deadline the stack has" do
    transport = PetstoreClient.TransportOptions.new(timeout: 3_000)
    opts = PetstoreClient.DefaultApiClient.request_options(transport)

    assert Keyword.get(opts, :receive_timeout) == 3_000
    assert Keyword.get(Keyword.get(opts, :connect_options, []), :timeout) == 3_000
    # The pool checkout cannot be armed from here: Req keeps `:pool_timeout`
    # under `:finch` and refuses `:finch` alongside `:connect_options`. It is
    # covered by classification instead, above.
    refute Keyword.has_key?(opts, :finch)
  end

  test "bhv redirects 015 3.2: send_request/6 with no_redirect: true returns the raw 302 response" do
    {target_url, _} = start_header_capture_server()
    {source_url, _} = start_redirect_server(302, "#{target_url}/echo")

    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        source_url,
        %{},
        nil,
        no_redirect: true
      )

    assert response.status_code == 302
    # And nothing should have hit the target server.
    refute_receive {:captured_headers, _}, 200
  end

  test "bhv redirects 022 3.2: send_request/6 with no_redirect: true returns the raw 308 response" do
    {target_url, _} = start_header_capture_server()
    {source_url, _} = start_redirect_server(308, "#{target_url}/echo")

    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :post,
        source_url,
        %{},
        "grant_type=client_credentials",
        no_redirect: true
      )

    assert response.status_code == 308
    refute_receive {:captured_headers, _}, 200
  end

  test "bhv redirects 014 3.2: no_redirect: false follows the redirect to completion" do
    {target_url, _} = start_header_capture_server()
    {source_url, _} = start_redirect_server(307, "#{target_url}/echo")

    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        source_url,
        %{},
        nil,
        no_redirect: false
      )

    assert response.status_code == 200
    assert_receive {:captured_headers, _}, 5000
  end

  # ── Redirect exhaustion raises loudly ──────────────────────────────────────

  # Always answers with a 302 pointing back at itself so the redirect budget is
  # exhausted while the response is still a 3xx. Handles up to `max_conns`
  # connections then stops.
  defp start_infinite_redirect_server(max_conns) do
    {:ok, listen_socket} =
      :gen_tcp.listen(0, [:binary, packet: :raw, active: false, reuseaddr: true])

    {:ok, port} = :inet.port(listen_socket)
    loc = "http://127.0.0.1:#{port}/loop"

    spawn(fn ->
      # `_ =` because the fold's result is not used: the accept loop runs for
      # its side effects and Credo rejects a discarded `Enum` return value.
      _ =
        Enum.reduce_while(1..max_conns, :ok, fn _, _ ->
          case :gen_tcp.accept(listen_socket, 5000) do
            {:ok, socket} ->
              :gen_tcp.recv(socket, 0, 2000)

              response =
                "HTTP/1.1 302 Found\r\n" <>
                  "Location: #{loc}\r\n" <>
                  "Content-Length: 0\r\n" <>
                  "Connection: close\r\n" <>
                  "\r\n"

              :gen_tcp.send(socket, response)
              :gen_tcp.close(socket)
              {:cont, :ok}

            _ ->
              {:halt, :ok}
          end
        end)

      :gen_tcp.close(listen_socket)
    end)

    Process.sleep(20)
    "http://127.0.0.1:#{port}"
  end

  test "bhv redirects 018 raises ApiError when the redirect budget is exhausted" do
    base_url = start_infinite_redirect_server(10)

    transport =
      PetstoreClient.TransportOptions.new(follow_redirects: true, max_redirects: 2)

    client = PetstoreClient.DefaultApiClient.new(transport)

    err =
      assert_raise PetstoreClient.Errors.ApiError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/loop", %{}, nil)
      end

    assert err.status_code == 302
  end

  test "an identity-encoded body keeps its Content-Encoding and Content-Length headers" do
    base_url = start_server(200, "text/plain", "plain", [{"Content-Encoding", "identity"}])
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/plain", %{}, nil)

    assert response.body == "plain"
    assert response.headers["content-encoding"] == "identity"
    assert response.headers["content-length"] == "5"
  end

  # ── Gap AL — Content-Encoding lie (gzip header, non-gzip body) ─────────────
  #
  # Canonical cross-SDK scenario: a server advertises `Content-Encoding: gzip`
  # but sends a plain (non-gzip) body. The client MUST surface the SDK's typed
  # ApiError — no crash (dart) and no silent corrupt passthrough
  # (csharp/kotlin/node/swift/elixir). Buggy before the fix in those six;
  # java/python/ruby/go/php already wrap the decompression failure. Canonical =
  # wrap as ApiError. RED before the elixir fix (the non-magic-bytes gunzip
  # clause returned the corrupt bytes verbatim), GREEN after.
  test "bhv transport 030 Gap AL: surfaces ApiError when gzip header lies about a plain body" do
    base_url =
      start_server(
        200,
        "application/json",
        "this is not gzip-compressed data",
        [{"Content-Encoding", "gzip"}]
      )

    client = PetstoreClient.DefaultApiClient.new()

    err =
      assert_raise PetstoreClient.Errors.ApiError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/gz", %{}, nil)
      end

    # The response arrived, so the error carries its real status code and is
    # never a NetworkError.
    assert err.status_code == 200
    # A response arrived, so the headers are kept: nil would mean that none did.
    assert err.response_headers["content-encoding"] == "gzip"
    refute PetstoreClient.Errors.NetworkError.network_error?(err)
  end

  # An unrecognised Content-Encoding the client cannot decode hits the
  # decompressor's default clause and must surface a typed ApiError carrying the
  # response status, never a NetworkError and never the body still encoded.
  test "bhv errors 042 unsupported Content-Encoding surfaces ApiError with the real status" do
    base_url =
      start_server(
        200,
        "application/json",
        "payload",
        [{"Content-Encoding", "made-up-codec"}]
      )

    client = PetstoreClient.DefaultApiClient.new()

    err =
      assert_raise PetstoreClient.Errors.ApiError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/enc", %{}, nil)
      end

    assert err.status_code == 200
    refute PetstoreClient.Errors.NetworkError.network_error?(err)
  end

  # Gap AL — empty body declared gzip passes through as empty.
  # A zero-byte body that still advertises `Content-Encoding: gzip` has nothing
  # to inflate. The client must return the empty body unchanged, never raise on
  # the absent gzip stream.
  test "bhv transport 037 empty body declared gzip passes through as empty" do
    base_url =
      start_server(
        200,
        "application/json",
        <<>>,
        [{"Content-Encoding", "gzip"}]
      )

    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/gz", %{}, nil)

    assert response.status_code == 200
    assert response.body == ""
  end

  # A body that carries the gzip magic bytes but is truncated is also a
  # response that arrived but cannot be used.
  test "corrupt gzip body with the magic bytes raises ApiError with the real status" do
    base_url =
      start_server(
        503,
        "application/json",
        <<0x1F, 0x8B, 0x08, 0x00>> <> "truncated",
        [{"Content-Encoding", "gzip"}]
      )

    client = PetstoreClient.DefaultApiClient.new()

    err =
      assert_raise PetstoreClient.Errors.ApiError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/gz", %{}, nil)
      end

    assert err.status_code == 503
  end

  # HTTP "deflate" is the zlib-wrapped format (RFC 1950).
  test "bhv transport 035 decompresses a zlib-wrapped deflate response" do
    base_url =
      start_server(
        200,
        "text/plain",
        :zlib.compress("deflated body"),
        [{"Content-Encoding", "deflate"}]
      )

    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/df", %{}, nil)

    assert response.status_code == 200
    assert response.body == "deflated body"
  end

  test "bhv transport 049 a raw headerless deflate body raises ApiError with the real status" do
    base_url =
      start_server(
        200,
        "text/plain",
        :zlib.zip("deflated body"),
        [{"Content-Encoding", "deflate"}]
      )

    client = PetstoreClient.DefaultApiClient.new()

    err =
      assert_raise PetstoreClient.Errors.ApiError, fn ->
        PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/df", %{}, nil)
      end

    assert err.status_code == 200
    assert err.message =~ "Failed to decompress deflate response body"
    refute PetstoreClient.Errors.NetworkError.network_error?(err)
  end

  # Without a caller-supplied Accept-Encoding the client advertises exactly the
  # codecs it can decode: gzip and deflate always, br only with the optional
  # :brotli NIF loaded, zstd only on an OTP that ships :zstd.
  test "bhv serializationwire 029 advertises the codecs it can decode in Accept-Encoding by default" do
    {base_url, _port} = start_header_capture_server()
    client = PetstoreClient.DefaultApiClient.new()
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000

    advertised = String.split(headers["accept-encoding"], ", ")

    assert Enum.take(advertised, 2) == ["gzip", "deflate"]
    assert "br" in advertised == Code.ensure_loaded?(:brotli)
    assert "zstd" in advertised == Code.ensure_loaded?(:zstd)
    assert length(advertised) == 2 + Enum.count(["br", "zstd"], &(&1 in advertised))
  end

  test "bhv transport 045 a caller-supplied Accept-Encoding is sent as given" do
    {base_url, _port} = start_header_capture_server()
    client = PetstoreClient.DefaultApiClient.new()

    PetstoreClient.DefaultApiClient.send_request(
      client,
      :get,
      "#{base_url}/test",
      %{"Accept-Encoding" => "identity"},
      nil
    )

    assert_receive {:captured_headers, headers}, 5000
    assert headers["accept-encoding"] == "identity"
  end

  # A redirect's origin is scheme + host + port, and an implicit default port is
  # the same origin as the explicit one: credentials must not be stripped on
  # https://h -> https://h:443.
  test "same_origin? treats an implicit and an explicit default port as one origin" do
    assert PetstoreClient.DefaultApiClient.same_origin?(
             "https://h.example/a",
             "https://h.example:443/b"
           )

    assert PetstoreClient.DefaultApiClient.same_origin?(
             "https://h.example:443/a",
             "https://h.example/b"
           )

    assert PetstoreClient.DefaultApiClient.same_origin?(
             "http://h.example/a",
             "http://h.example:80/b"
           )

    assert PetstoreClient.DefaultApiClient.same_origin?(
             "https://H.Example/a",
             "https://h.example/b"
           )
  end

  test "same_origin? separates a different scheme, host or port" do
    refute PetstoreClient.DefaultApiClient.same_origin?(
             "https://h.example/a",
             "http://h.example/b"
           )

    refute PetstoreClient.DefaultApiClient.same_origin?(
             "https://h.example/a",
             "https://other.example/b"
           )

    refute PetstoreClient.DefaultApiClient.same_origin?(
             "https://h.example/a",
             "https://h.example:8443/b"
           )

    refute PetstoreClient.DefaultApiClient.same_origin?(
             "https://h.example:443/a",
             "http://h.example:80/b"
           )

    refute PetstoreClient.DefaultApiClient.same_origin?("/relative", "https://h.example/b")
  end

  # ── Multipart filename directive (Gap BI / Gap F) ──────────────────────────

  test "bhv serializationwire 044 build_content_disposition emits RFC 5987 filename* for non-ASCII names" do
    cd = PetstoreClient.DefaultApiClient.build_content_disposition("file", "日本.pdf")
    assert String.contains?(cd, "filename*=UTF-8''")
    assert String.contains?(cd, "%E6%97%A5%E6%9C%AC")
    assert String.contains?(cd, "filename=\"")
  end

  test "bhv serializationwire 028 build_content_disposition falls back to one underscore per code point" do
    assert PetstoreClient.DefaultApiClient.build_content_disposition("file", "résumé*(1).pdf") ==
             ~s|Content-Disposition: form-data; name="file"; filename="r_sum_*(1).pdf"; | <>
               "filename*=UTF-8''r%C3%A9sum%C3%A9%2A%281%29.pdf"

    assert PetstoreClient.DefaultApiClient.build_content_disposition("file", "😀.png") ==
             ~s|Content-Disposition: form-data; name="file"; filename="_.png"; | <>
               "filename*=UTF-8''%F0%9F%98%80.png"
  end

  test "bhv serializationwire 041 build_content_disposition omits filename* for ASCII-only names" do
    cd = PetstoreClient.DefaultApiClient.build_content_disposition("file", "file.png")
    assert String.contains?(cd, "filename=\"file.png\"")
    refute String.contains?(cd, "filename*=")
  end

  test "bhv serializationwire 042 build_content_disposition backslash-escapes quotes in the filename" do
    cd = PetstoreClient.DefaultApiClient.build_content_disposition("file", "a\"b.txt")
    assert String.contains?(cd, "a\\\"b.txt")
  end

  test "bhv serializationwire 043 build_content_disposition rejects filenames containing CRLF" do
    for bad <- ["a\rb.pdf", "a\nb.pdf", "a\r\nb.pdf"] do
      assert_raise ArgumentError, fn ->
        PetstoreClient.DefaultApiClient.build_content_disposition("file", bad)
      end
    end

    # ASCII filename does not raise.
    PetstoreClient.DefaultApiClient.build_content_disposition("file", "file.png")
  end

  test "bhv serializationwire 050 build_content_disposition rejects filenames containing NUL" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.DefaultApiClient.build_content_disposition("file", "a" <> <<0>> <> "b.pdf")
    end
  end

  # ── 4.8: format:time / format:duration round-trip ──────────────────────────

  describe "4.8 format: time" do
    test "stringify/1 emits ISO-8601 for Time.t()" do
      t = ~T[12:30:45]
      assert PetstoreClient.ObjectSerializer.stringify(t) == "12:30:45"
    end

    test "convert_to_type parses ISO-8601 to Time.t() under typespec key" do
      result = PetstoreClient.ObjectSerializer.convert_to_type("12:30:45", "Time.t()")
      assert %Time{} = result
      assert result.hour == 12 and result.minute == 30 and result.second == 45
    end

    test "convert_to_type parses ISO-8601 to Time.t() under bare key" do
      result = PetstoreClient.ObjectSerializer.convert_to_type("12:30:45", "Time")
      assert %Time{} = result
    end

    test "round-trip via sanitize_for_serialization" do
      t = ~T[09:00:00]
      sanitized = PetstoreClient.ObjectSerializer.sanitize_for_serialization(t)
      assert is_binary(sanitized)
      assert PetstoreClient.ObjectSerializer.convert_to_type(sanitized, "Time.t()") == t
    end
  end

  describe "4.8 format: duration" do
    test "stringify/1 emits protobuf duration for Duration.t()" do
      d = Duration.new!(hour: 1, minute: 30)
      pb = PetstoreClient.ObjectSerializer.stringify(d)
      assert is_binary(pb)
      assert pb == "5400s"
    end

    test "convert_to_type parses protobuf duration to Duration.t() under typespec key" do
      result = PetstoreClient.ObjectSerializer.convert_to_type("5400s", "Duration.t()")
      assert %Duration{second: 5400} = result
    end

    test "round-trip via sanitize_for_serialization" do
      original = Duration.new!(hour: 2)
      sanitized = PetstoreClient.ObjectSerializer.sanitize_for_serialization(original)
      assert is_binary(sanitized)
      parsed = PetstoreClient.ObjectSerializer.convert_to_type(sanitized, "Duration.t()")
      assert %Duration{} = parsed
    end

    # A NEGATIVE duration must serialize with a leading minus and round-trip
    # back to the same signed total — the protobuf-JSON grammar allows `-?`.
    test "negative duration round-trips with a leading minus sign" do
      d = Duration.new!(second: -90)
      pb = PetstoreClient.ObjectSerializer.stringify(d)
      assert pb == "-90s"
      parsed = PetstoreClient.ObjectSerializer.convert_to_type(pb, "Duration.t()")
      assert %Duration{} = parsed
      assert parsed.second == -90
    end

    # A sub-0.0001s magnitude must emit fixed-point fractional digits, never
    # scientific notation (e.g. "0.00005s", not "5.0e-5s"). 50 microseconds is
    # below 1e-4 seconds and exercises the fractional padding path.
    test "sub-0.0001s magnitude emits fixed-point digits, no scientific notation" do
      d = Duration.new!(microsecond: {50, 6})
      pb = PetstoreClient.ObjectSerializer.stringify(d)
      assert pb == "0.000050s"
      refute pb =~ ~r/[eE]/, "duration must not use scientific notation: #{pb}"
      parsed = PetstoreClient.ObjectSerializer.convert_to_type(pb, "Duration.t()")
      assert {micros, _precision} = parsed.microsecond
      assert micros == 50
    end
  end

  describe "multipart parts" do
    test "bhv serializationwire 048 primitives are plain text parts and only a map is an application/json part" do
      body =
        PetstoreClient.DefaultApiClient.build_multipart_body(
          [
            {"flag", true},
            {"count", 3},
            {"at", ~U[2024-01-15 10:30:00Z]},
            {"day", ~D[2024-01-15]},
            {"kind", :sample},
            {"item", %{"k" => "v"}}
          ],
          "b"
        )

      assert body ==
               ~s(--b\r\nContent-Disposition: form-data; name="flag"\r\n\r\ntrue\r\n) <>
                 ~s(--b\r\nContent-Disposition: form-data; name="count"\r\n\r\n3\r\n) <>
                 ~s(--b\r\nContent-Disposition: form-data; name="at"\r\n\r\n2024-01-15T10:30:00.000+00:00\r\n) <>
                 ~s(--b\r\nContent-Disposition: form-data; name="day"\r\n\r\n2024-01-15\r\n) <>
                 ~s(--b\r\nContent-Disposition: form-data; name="kind"\r\n\r\nsample\r\n) <>
                 ~s(--b\r\nContent-Disposition: form-data; name="item"\r\n) <>
                 ~s(Content-Type: application/json\r\n\r\n{"k":"v"}\r\n) <>
                 "--b--\r\n"
    end
  end

  describe "redirect_method/2 (RFC 9110 section 15.4)" do
    alias PetstoreClient.DefaultApiClient

    test "301 and 302 turn only POST into GET" do
      for status <- [301, 302] do
        assert DefaultApiClient.redirect_method(status, :post) == :get
        assert DefaultApiClient.redirect_method(status, :put) == :put
        assert DefaultApiClient.redirect_method(status, :patch) == :patch
        assert DefaultApiClient.redirect_method(status, :delete) == :delete
        assert DefaultApiClient.redirect_method(status, :head) == :head
        assert DefaultApiClient.redirect_method(status, :get) == :get
      end
    end

    test "303 turns every method except HEAD into GET" do
      for method <- [:post, :put, :patch, :delete, :get] do
        assert DefaultApiClient.redirect_method(303, method) == :get
      end

      assert DefaultApiClient.redirect_method(303, :head) == :head
    end

    test "307 and 308 never change the method" do
      for status <- [307, 308], method <- [:post, :put, :patch, :delete, :get, :head] do
        assert DefaultApiClient.redirect_method(status, method) == method
      end
    end
  end

  describe "the ApiClient behaviour" do
    test "close/1 is optional, so a minimal custom transport need not implement it" do
      optional = PetstoreClient.ApiClient.behaviour_info(:optional_callbacks)

      assert {:close, 1} in optional
      assert {:close, 1} in PetstoreClient.ApiClient.behaviour_info(:callbacks)
    end

    test "the default transport implements close/1" do
      assert Code.ensure_loaded?(PetstoreClient.DefaultApiClient)
      assert function_exported?(PetstoreClient.DefaultApiClient, :close, 1)
    end
  end
end
