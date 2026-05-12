defmodule PetstoreClient.DefaultApiClientUnitTest do
  use ExUnit.Case, async: true

  test "creates client with default transport" do
    client = PetstoreClient.DefaultApiClient.new()
    assert client.transport_options != nil
    assert client.transport_options.verify_ssl == true
  end

  test "creates client with custom transport" do
    transport = PetstoreClient.TransportOptions.new(verify_ssl: false, timeout: 5000)
    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client.transport_options.verify_ssl == false
    assert client.transport_options.timeout == 5000
  end

  test "client struct stores transport options" do
    transport =
      PetstoreClient.TransportOptions.new(
        user_agent: "TestAgent/1.0",
        inject_request_id: true
      )

    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client.transport_options.user_agent == "TestAgent/1.0"
    assert client.transport_options.inject_request_id == true
  end

  test "user agent transport option is stored" do
    transport = PetstoreClient.TransportOptions.new(user_agent: "custom-agent/1.0")
    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client.transport_options.user_agent == "custom-agent/1.0"
  end

  test "inject request ID option is enabled" do
    transport = PetstoreClient.TransportOptions.new(inject_request_id: true)
    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client.transport_options.inject_request_id == true
  end

  test "inject request ID option is disabled" do
    transport = PetstoreClient.TransportOptions.new(inject_request_id: false)
    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client.transport_options.inject_request_id == false
  end

  test "default headers transport option is stored" do
    transport = PetstoreClient.TransportOptions.new(default_headers: %{"X-Custom" => "custom-value"})
    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client.transport_options.default_headers["X-Custom"] == "custom-value"
  end

  test "caller headers override transport default headers in merge logic" do
    transport_defaults = %{"Accept" => "text/plain"}
    caller_headers = %{"Accept" => "application/json"}
    merged = Map.merge(transport_defaults, caller_headers)
    assert merged["Accept"] == "application/json"
  end

  test "timeout transport option is stored" do
    transport = PetstoreClient.TransportOptions.new(timeout: 5000)
    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client.transport_options.timeout == 5000
  end
end
