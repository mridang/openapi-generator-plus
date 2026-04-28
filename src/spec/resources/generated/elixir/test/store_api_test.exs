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
end
