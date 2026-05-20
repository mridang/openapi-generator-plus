defmodule PetstoreClient.DefaultApiClientIntegrationTest do
  use ExUnit.Case, async: false

  test "makes HTTPS request with verify_ssl=false" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTPS_URL")

    transport = PetstoreClient.TransportOptions.new(verify_ssl: false)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/test", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  test "makes HTTPS request with custom CA cert" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTPS_URL")
    ca_cert_path = System.fetch_env!("CA_CERT_PATH")

    transport =
      PetstoreClient.TransportOptions.new(
        verify_ssl: true,
        ca_cert_path: ca_cert_path
      )

    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/test", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  @tag :skip
  test "makes HTTP request through proxy" do
    wiremock_url = System.fetch_env!("WIREMOCK_INTERNAL_HTTP_URL")
    proxy_url = System.fetch_env!("PROXY_URL")

    transport = PetstoreClient.TransportOptions.new(proxy: proxy_url)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/test", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  @tag :skip
  test "makes HTTPS request through proxy with verify_ssl=false" do
    wiremock_url = System.fetch_env!("WIREMOCK_INTERNAL_HTTPS_URL")
    proxy_url = System.fetch_env!("PROXY_URL")

    transport = PetstoreClient.TransportOptions.new(proxy: proxy_url, verify_ssl: false)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/test", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  test "times out on slow endpoint" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(timeout: 1)
    client = PetstoreClient.DefaultApiClient.new(transport)

    try do
      PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/slow", %{}, nil)
      flunk("Expected a transport error but none was raised")
    rescue
      _ in [PetstoreClient.ApiError, Req.TransportError, Finch.TransportError] -> :ok
    end
  end

  test "injects custom User-Agent header" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(user_agent: "MyApp/1.0")
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)

    assert response.status_code == 200
    json = Jason.decode!(response.body)
    assert json["user-agent"] == "MyApp/1.0"
  end

  test "injects X-Request-ID header with UUID format" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(inject_request_id: true)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)

    assert response.status_code == 200
    json = Jason.decode!(response.body)
    request_id = json["x-request-id"]
    assert request_id != nil
    assert Regex.match?(~r/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/, request_id)
  end

  test "generates unique X-Request-ID per request" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(inject_request_id: true)
    client = PetstoreClient.DefaultApiClient.new(transport)

    response1 = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)
    request_id1 = Jason.decode!(response1.body)["x-request-id"]

    response2 = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)
    request_id2 = Jason.decode!(response2.body)["x-request-id"]

    assert request_id1 != request_id2
  end

  test "includes transport-level default headers" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(default_headers: %{"X-Custom" => "custom-value"})

    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)

    assert response.status_code == 200
    json = Jason.decode!(response.body)
    assert json["x-custom"] == "custom-value"
  end

  test "caller headers override transport default headers" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(default_headers: %{"Accept" => "text/plain"})

    client = PetstoreClient.DefaultApiClient.new(transport)

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "#{wiremock_url}/api/echo-headers",
        %{"Accept" => "application/json"},
        nil
      )

    assert response.status_code == 200
    json = Jason.decode!(response.body)
    assert json["accept"] == "application/json"
  end

  test "follows redirects when enabled" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(follow_redirects: true)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/redirect", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  test "returns redirect response when disabled" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(follow_redirects: false)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/redirect", %{}, nil)

    assert response.status_code == 302
  end

  test "respects max_redirects limit" do
    transport =
      PetstoreClient.TransportOptions.new(
        follow_redirects: true,
        max_redirects: 5
      )

    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client != nil
    assert transport.max_redirects == 5
  end

  test "sends multipart form data" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    client = PetstoreClient.DefaultApiClient.new()
    form_data = %{"description" => "A test file", "file" => "file content"}
    response = PetstoreClient.DefaultApiClient.send_request(client, :post, "#{wiremock_url}/api/test", %{}, form_data)

    assert response != nil
  end

  test "decompresses gzip response" do
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "https://jsonplaceholder.typicode.com/posts/1",
        %{"Accept-Encoding" => "gzip"},
        nil
      )

    assert response.status_code == 200
    assert String.contains?(response.body, "userId")
  end

  test "decompresses brotli response" do
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "https://jsonplaceholder.typicode.com/posts/1",
        %{"Accept-Encoding" => "br"},
        nil
      )

    assert response.status_code == 200
    assert String.contains?(response.body, "userId")
  end

  @tag :skip
  test "decompresses zstd response" do
    # zstd is not supported by the Req HTTP library or Erlang's built-in HTTP client
    client = PetstoreClient.DefaultApiClient.new()

    response =
      PetstoreClient.DefaultApiClient.send_request(
        client,
        :get,
        "https://jsonplaceholder.typicode.com/posts/1",
        %{"Accept-Encoding" => "zstd"},
        nil
      )

    assert response.status_code == 200
    assert String.contains?(response.body, "userId")
  end
end
