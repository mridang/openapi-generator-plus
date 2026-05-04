defmodule PetstoreClient.DefaultApiClientIntegrationTest do
  use ExUnit.Case, async: false

  test "TLS verification disabled makes HTTPS request with verify_ssl=false" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTPS_URL")

    transport = PetstoreClient.TransportOptions.new(verify_ssl: false)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/test", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  test "custom CA bundle makes HTTPS request with custom CA cert" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTPS_URL")
    ca_cert_path = System.fetch_env!("CA_CERT_PATH")

    transport = PetstoreClient.TransportOptions.new(
      verify_ssl: true,
      ca_cert_path: ca_cert_path
    )

    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/test", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  test "HTTP proxy makes HTTP request through proxy" do
    wiremock_url = System.get_env("WIREMOCK_INTERNAL_HTTP_URL", System.fetch_env!("WIREMOCK_HTTP_URL"))
    proxy_url = System.fetch_env!("PROXY_URL")

    transport = PetstoreClient.TransportOptions.new(proxy: proxy_url)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/test", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  test "request timeout times out on slow endpoint" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(timeout: 1)
    client = PetstoreClient.DefaultApiClient.new(transport)

    assert_raise RuntimeError, fn ->
      PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/slow", %{}, nil)
    end
  end

  test "User-Agent header injection" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(user_agent: "MyApp/1.0")
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)

    assert response.status_code == 200
    json = Jason.decode!(response.body)
    assert json["user-agent"] == "MyApp/1.0"
  end

  test "X-Request-ID injection with UUID format" do
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

  test "X-Request-ID generates unique values per request" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(inject_request_id: true)
    client = PetstoreClient.DefaultApiClient.new(transport)

    response1 = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)
    request_id1 = Jason.decode!(response1.body)["x-request-id"]

    response2 = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)
    request_id2 = Jason.decode!(response2.body)["x-request-id"]

    assert request_id1 != request_id2
  end

  test "default headers includes transport-level default headers" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(
      default_headers: %{"X-Custom" => "custom-value"}
    )

    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/echo-headers", %{}, nil)

    assert response.status_code == 200
    json = Jason.decode!(response.body)
    assert json["x-custom"] == "custom-value"
  end

  test "caller headers override transport default headers" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(
      default_headers: %{"Accept" => "text/plain"}
    )

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

  test "redirect handling follows redirects when enabled" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(follow_redirects: true)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/redirect", %{}, nil)

    assert response.status_code == 200
    assert String.contains?(response.body, "success")
  end

  test "redirect handling returns redirect response when disabled" do
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")

    transport = PetstoreClient.TransportOptions.new(follow_redirects: false)
    client = PetstoreClient.DefaultApiClient.new(transport)
    response = PetstoreClient.DefaultApiClient.send_request(client, :get, "#{wiremock_url}/api/redirect", %{}, nil)

    assert response.status_code == 302
  end
end
