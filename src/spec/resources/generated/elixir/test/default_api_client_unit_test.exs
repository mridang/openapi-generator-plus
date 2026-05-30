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

          status_text =
            if status == 200 do
              "OK"
            else
              to_string(status)
            end

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

  test "sends GET request and returns response" do
    base_url = start_server(200, "application/json", ~s({"method":"GET"}))
    client = PetstoreClient.DefaultApiClient.new()
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/echo", %{}, nil)
    assert response.status_code == 200
    assert String.contains?(response.body, "GET")
  end

  test "sends POST with JSON body" do
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

  test "returns response headers" do
    base_url = start_server(200, "application/json", "ok", [{"X-Test-Header", "test-value"}])
    client = PetstoreClient.DefaultApiClient.new()
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/echo", %{}, nil)
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

  test "returns non-2xx status code" do
    base_url = start_server(404, "text/plain", "not found")
    client = PetstoreClient.DefaultApiClient.new()
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/not-found", %{}, nil)
    assert response.status_code == 404
    assert response.body == "not found"
  end

  test "sends PUT request" do
    base_url = start_server(200, "application/json", ~s({"method":"PUT"}))
    client = PetstoreClient.DefaultApiClient.new()
    response = PetstoreClient.DefaultApiClient.send_request(client, :put, "#{base_url}/echo", %{}, "update")
    assert response.status_code == 200
    assert String.contains?(response.body, "PUT")
  end

  test "sends DELETE request" do
    base_url = start_server(200, "application/json", ~s({"method":"DELETE"}))
    client = PetstoreClient.DefaultApiClient.new()
    response = PetstoreClient.DefaultApiClient.send_request(client, :delete, "#{base_url}/echo", %{}, nil)
    assert response.status_code == 200
    assert String.contains?(response.body, "DELETE")
  end

  test "returns JSON body for vendor JSON content type" do
    base_url = start_server(200, "application/vnd.api+json", ~s({"format":"vendor"}))
    client = PetstoreClient.DefaultApiClient.new()
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/vendor-json", %{}, nil)
    assert response.status_code == 200
    assert String.contains?(response.body, "vendor")
  end

  test "joins multi-value response headers" do
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

  test "injects custom User-Agent header" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(user_agent: "MyApp/1.0")
    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    assert headers["user-agent"] == "MyApp/1.0"
  end

  test "injects default User-Agent when not explicitly set" do
    {base_url, _port} = start_header_capture_server()
    client = PetstoreClient.DefaultApiClient.new()
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    assert headers["user-agent"] != nil
    assert String.length(headers["user-agent"]) > 0
  end

  test "injects X-Request-ID when enabled" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(inject_request_id: true)
    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    assert headers["x-request-id"] != nil
    assert String.length(headers["x-request-id"]) > 0
  end

  test "does not inject X-Request-ID when disabled" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(inject_request_id: false)
    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    refute Map.has_key?(headers, "x-request-id")
  end

  test "does not override caller-provided X-Request-ID" do
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

  test "generates unique X-Request-ID per request" do
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

  test "includes transport-level default headers" do
    {base_url, _port} = start_header_capture_server()
    transport = PetstoreClient.TransportOptions.new(default_headers: %{"X-Custom" => "custom-value"})
    client = PetstoreClient.DefaultApiClient.new(transport)
    PetstoreClient.DefaultApiClient.send_request(client, :get, "#{base_url}/test", %{}, nil)
    assert_receive {:captured_headers, headers}, 5000
    assert headers["x-custom"] == "custom-value"
  end

  test "caller headers override transport default headers" do
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
end
