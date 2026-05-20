defmodule PetstoreClient.Api.StoreApiTest do
  use ExUnit.Case, async: false

  setup do
    base_url = System.get_env("API_BASE_URL", "http://localhost:4010")

    config =
      PetstoreClient.Configuration.new(
        base_url: base_url,
        default_headers: %{"Authorization" => "Bearer test-token"}
      )

    api = PetstoreClient.Api.StoreApi.new(nil, config)

    %{api: api}
  end

  test "get_inventory returns inventory", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.StoreApi.get_inventory(api)
    assert is_map(result)
  end

  test "place_order places an order", %{api: api} do
    order = %PetstoreClient.Models.Order{
      id: 1,
      pet_id: 12_345,
      quantity: 1,
      ship_date: DateTime.utc_now() |> DateTime.to_iso8601(),
      status: "placed",
      complete: false
    }

    assert {:ok, result} = PetstoreClient.Api.StoreApi.place_order(api, order)
    assert result != nil
    assert result.id != nil
  end

  test "get_order_by_id returns an order by id", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.StoreApi.get_order_by_id(api, 1)
    assert result != nil
    assert result.id != nil
  end

  test "delete_order deletes an order", %{api: api} do
    assert {:ok, _result} = PetstoreClient.Api.StoreApi.delete_order(api, 1)
  end

  defp new_store_api_for_mock(status, content_type, body) do
    {:ok, socket} = :gen_tcp.listen(0, [:binary, active: false, reuseaddr: true])
    {:ok, port} = :inet.port(socket)

    spawn(fn ->
      {:ok, client} = :gen_tcp.accept(socket)
      {:ok, _data} = :gen_tcp.recv(client, 0)

      response =
        "HTTP/1.1 #{status} OK\r\nContent-Type: #{content_type}\r\nContent-Length: #{byte_size(body)}\r\n\r\n#{body}"

      :gen_tcp.send(client, response)
      :gen_tcp.close(client)
      :gen_tcp.close(socket)
    end)

    config =
      PetstoreClient.Configuration.new(
        base_url: "http://127.0.0.1:#{port}",
        default_headers: %{}
      )

    PetstoreClient.Api.StoreApi.new(nil, config)
  end

  test "get_order_by_id 404 returns error" do
    api = new_store_api_for_mock(404, "application/json", ~s({"message":"Order not found"}))
    assert {:error, _reason} = PetstoreClient.Api.StoreApi.get_order_by_id(api, 99_999)
  end

  test "place_order 500 returns error" do
    api = new_store_api_for_mock(500, "application/json", ~s({"message":"Internal server error"}))

    order = %PetstoreClient.Models.Order{
      id: 1,
      pet_id: 12_345,
      quantity: 1,
      status: "placed",
      complete: false
    }

    assert {:error, _reason} = PetstoreClient.Api.StoreApi.place_order(api, order)
  end

  test "delete_order 404 returns error" do
    api = new_store_api_for_mock(404, "application/json", ~s({"message":"Order not found"}))
    assert {:error, _reason} = PetstoreClient.Api.StoreApi.delete_order(api, 99_999)
  end
end
