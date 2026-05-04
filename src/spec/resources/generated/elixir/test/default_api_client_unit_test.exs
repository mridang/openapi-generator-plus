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
    transport = PetstoreClient.TransportOptions.new(
      user_agent: "TestAgent/1.0",
      inject_request_id: true
    )

    client = PetstoreClient.DefaultApiClient.new(transport)
    assert client.transport_options.user_agent == "TestAgent/1.0"
    assert client.transport_options.inject_request_id == true
  end
end
