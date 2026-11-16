defmodule PetstoreClient.Api.StoreApiTest do
  use ExUnit.Case, async: true

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
      id: :rand.uniform(1_000_000_000),
      pet_id: :rand.uniform(1_000_000_000),
      quantity: 1,
      ship_date: DateTime.utc_now() |> DateTime.to_iso8601(),
      status: :placed,
      complete: false
    }

    assert {:ok, result} = PetstoreClient.Api.StoreApi.place_order(api, order)
    assert result != nil
    assert result.id != nil
  end

  test "get_inventory_with_http_info returns status and data", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.StoreApi.get_inventory_with_http_info(api)
    assert result.status_code >= 200 and result.status_code < 300
    assert result.data != nil
  end

  test "place_order_with_http_info returns status and data", %{api: api} do
    order = %PetstoreClient.Models.Order{
      id: :rand.uniform(1_000_000_000),
      pet_id: :rand.uniform(1_000_000_000),
      quantity: 1,
      ship_date: DateTime.utc_now() |> DateTime.to_iso8601(),
      status: :placed,
      complete: false
    }

    assert {:ok, result} = PetstoreClient.Api.StoreApi.place_order_with_http_info(api, order)
    assert result.status_code >= 200 and result.status_code < 300
    assert result.data != nil
  end

  test "get_order_by_id returns an order by id", %{api: api} do
    assert {:ok, result} =
             PetstoreClient.Api.StoreApi.get_order_by_id(api, :rand.uniform(1_000_000_000))

    assert result != nil
    assert result.id != nil
  end

  test "get_order_by_id_with_http_info returns status and data", %{api: api} do
    assert {:ok, result} =
             PetstoreClient.Api.StoreApi.get_order_by_id_with_http_info(
               api,
               :rand.uniform(1_000_000_000)
             )

    assert result.status_code >= 200 and result.status_code < 300
    assert result.data != nil
  end

  test "delete_order deletes an order", %{api: api} do
    assert {:ok, _result} =
             PetstoreClient.Api.StoreApi.delete_order(api, :rand.uniform(1_000_000_000))
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

    assert {:error, %PetstoreClient.Errors.NotFoundError{status_code: 404}} =
             PetstoreClient.Api.StoreApi.get_order_by_id(api, 99_999)
  end

  test "place_order 500 returns error" do
    api = new_store_api_for_mock(500, "application/json", ~s({"message":"Internal server error"}))

    order = %PetstoreClient.Models.Order{
      id: :rand.uniform(1_000_000_000),
      pet_id: :rand.uniform(1_000_000_000),
      quantity: 1,
      status: :placed,
      complete: false
    }

    assert {:error, %PetstoreClient.Errors.InternalServerError{status_code: 500}} =
             PetstoreClient.Api.StoreApi.place_order(api, order)
  end

  test "delete_order 404 returns error" do
    api = new_store_api_for_mock(404, "application/json", ~s({"message":"Order not found"}))

    assert {:error, %PetstoreClient.Errors.NotFoundError{status_code: 404}} =
             PetstoreClient.Api.StoreApi.delete_order(api, 99_999)
  end

  # Nested container deserialization: getGroupedCategories returns
  # Array<Record<string, Category>>. Each leaf of the array-of-maps must be
  # decoded into a typed Category struct, not left as a raw map.
  test "get_grouped_categories deserializes nested map leaves into typed Category structs" do
    body = ~s([{"a":{"id":1,"name":"Dogs"}},{"b":{"id":2,"name":"Cats"}}])
    api = new_store_api_for_mock(200, "application/json", body)

    assert {:ok, result} = PetstoreClient.Api.StoreApi.get_grouped_categories(api)
    assert %PetstoreClient.Models.Category{} = Enum.at(result, 0)["a"]
    assert Enum.at(result, 0)["a"].id == 1
    assert Enum.at(result, 0)["a"].name == "Dogs"
  end

  # A module-style api_client that records the request it was handed and
  # answers with a canned JSON body.
  defmodule StubApiClient do
    use Agent

    def start(body) do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> %{request: nil, body: body} end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured(name), do: Agent.get(name, & &1.request)

    def send_request(method, url, headers, body) do
      name = Process.get(__MODULE__)

      Agent.get_and_update(name, fn state ->
        response = %PetstoreClient.ApiHttpResponse{
          status_code: 200,
          body: state.body,
          headers: %{"Content-Type" => "application/json"}
        }

        {response, %{state | request: %{method: method, url: url, headers: headers, body: body}}}
      end)
    end
  end

  # A named-enum ($ref) parameter goes on the wire as its declared value in a
  # path, a query string and a header, never as the atom's debug form.
  test "get_by_swatch sends the named enum's wire value in path, query and header" do
    {:ok, name} = StubApiClient.start(~s({"id":1,"name":"sample"}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    options = %PetstoreClient.Api.Options.GetBySwatchOptions{
      query_swatch: PetstoreClient.Models.Swatch.green(),
      preferred_swatch: PetstoreClient.Models.Swatch.blue()
    }

    assert {:ok, %PetstoreClient.Models.Category{id: 1}} =
             PetstoreClient.Api.StoreApi.get_by_swatch(
               api,
               PetstoreClient.Models.Swatch.red(),
               options
             )

    captured = StubApiClient.captured(name)
    assert captured.url == "http://localhost/store/by-swatch/red?querySwatch=green"
    assert captured.headers["Preferred-Swatch"] == "blue"

    Agent.stop(name)
  end

  test "get_by_swatch without options sends only the path value" do
    {:ok, name} = StubApiClient.start(~s({"id":1,"name":"sample"}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, _category} =
             PetstoreClient.Api.StoreApi.get_by_swatch(api, PetstoreClient.Models.Swatch.blue())

    captured = StubApiClient.captured(name)
    assert captured.url == "http://localhost/store/by-swatch/blue"
    refute Map.has_key?(captured.headers, "Preferred-Swatch")

    Agent.stop(name)
  end

  # A parameter declared with `content: application/json` goes through the
  # SDK's own object serializer, so a model is encoded with its wire names and
  # canonical date-time and a map keeps its keys.
  test "search_store sends content-encoded query and header parameters through the object serializer" do
    {:ok, name} = StubApiClient.start("")
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    photo = %PetstoreClient.Models.PhotoMetadata{
      caption: "a<b",
      taken_at: ~U[2024-01-15 10:30:00Z]
    }

    options = %PetstoreClient.Api.Options.SearchStoreOptions{
      q: photo,
      x_filter: %{"k" => "v"}
    }

    _result = PetstoreClient.Api.StoreApi.search_store(api, options)

    captured = StubApiClient.captured(name)
    query = captured.url |> String.split("?") |> List.last() |> URI.decode_query()
    assert query["q"] == PetstoreClient.ObjectSerializer.serialize(photo)
    assert query["q"] =~ ~s("takenAt":"2024-01-15T10:30:00.000+00:00")
    assert query["q"] =~ ~s("caption":"a<b")
    assert captured.headers["X-Filter"] == ~s({"k":"v"})

    Agent.stop(name)
  end

  test "search_store keeps the fractional seconds of a time parameter when non-zero" do
    {:ok, name} = StubApiClient.start("")
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    options = %PetstoreClient.Api.Options.SearchStoreOptions{at: ~T[10:30:00.123456]}
    _result = PetstoreClient.Api.StoreApi.search_store(api, options)

    assert StubApiClient.captured(name).url =~ "at=10%3A30%3A00.123"

    Agent.stop(name)
  end

  # The response-shape operations: each return type is decoded by the
  # generated function itself, so a wrong return type or a decoder that cannot
  # handle a bare enum, a map-of-array or a recursive model fails here.
  test "get_swatch decodes a bare enum response to its atom" do
    {:ok, name} = StubApiClient.start(~s("blue"))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, :blue} = PetstoreClient.Api.StoreApi.get_swatch(api)
    assert StubApiClient.captured(name).url == "http://localhost/store/swatch"

    Agent.stop(name)
  end

  test "get_swatch_groups decodes an array of maps of enums" do
    {:ok, name} = StubApiClient.start(~s([{"a":"red"},{"b":"blue"}]))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, [%{"a" => :red}, %{"b" => :blue}]} =
             PetstoreClient.Api.StoreApi.get_swatch_groups(api)

    Agent.stop(name)
  end

  test "get_timestamp_groups decodes an array of maps of date-times" do
    {:ok, name} = StubApiClient.start(~s([{"a":"2024-01-15T10:30:00Z"}]))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, [%{"a" => %DateTime{} = at}]} =
             PetstoreClient.Api.StoreApi.get_timestamp_groups(api)

    assert DateTime.compare(at, ~U[2024-01-15 10:30:00Z]) == :eq

    Agent.stop(name)
  end

  test "get_department decodes the mutually recursive Department and Employee" do
    body = ~s({"name":"eng","lead":{"name":"ann","department":{"name":"eng"}}})
    {:ok, name} = StubApiClient.start(body)
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, %PetstoreClient.Models.Department{name: "eng", lead: lead}} =
             PetstoreClient.Api.StoreApi.get_department(api)

    assert %PetstoreClient.Models.Employee{name: "ann", department: department} = lead
    assert %PetstoreClient.Models.Department{name: "eng"} = department

    Agent.stop(name)
  end

  test "get_tree decodes the self-referential TreeNode" do
    {:ok, name} = StubApiClient.start(~s({"value":"root","child":{"value":"leaf"}}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, %PetstoreClient.Models.TreeNode{value: "root", child: child}} =
             PetstoreClient.Api.StoreApi.get_tree(api)

    assert %PetstoreClient.Models.TreeNode{value: "leaf"} = child

    Agent.stop(name)
  end

  test "get_defaults decodes an empty object with the schema defaults applied" do
    {:ok, name} = StubApiClient.start(~s({}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, %PetstoreClient.Models.Defaults{retries: 3, mode: :medium}} =
             PetstoreClient.Api.StoreApi.get_defaults(api)

    Agent.stop(name)
  end

  test "get_stock_item sends asOf as a date-time and decodes the referenced enums" do
    {:ok, name} = StubApiClient.start(~s({"priority":1,"availability":"Sold"}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    options = %PetstoreClient.Api.Options.GetStockItemOptions{as_of: ~U[2024-01-15 10:30:00Z]}

    assert {:ok, %PetstoreClient.Models.StockItem{priority: :number_1, availability: :sold}} =
             PetstoreClient.Api.StoreApi.get_stock_item(api, options)

    assert StubApiClient.captured(name).url =~ "asOf=2024-01-15T10%3A30%3A00"

    Agent.stop(name)
  end

  test "get_order_by_tracking_id puts the canonical lowercase uuid in the path" do
    {:ok, name} = StubApiClient.start(~s({"id":1}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, %PetstoreClient.Models.Order{id: 1}} =
             PetstoreClient.Api.StoreApi.get_order_by_tracking_id(
               api,
               "E621E1F8-C36C-495A-93FC-0C247A3E6E5F"
             )

    assert StubApiClient.captured(name).url ==
             "http://localhost/store/order/by-tracking/e621e1f8-c36c-495a-93fc-0c247a3e6e5f"

    Agent.stop(name)
  end

  test "get_order_by_tracking_id rejects a malformed uuid before sending" do
    {:ok, name} = StubApiClient.start(~s({"id":1}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert_raise PetstoreClient.Errors.SerializationError, fn ->
      PetstoreClient.Api.StoreApi.get_order_by_tracking_id(api, "not-a-uuid")
    end

    assert StubApiClient.captured(name) == nil

    Agent.stop(name)
  end

  # readOnly and writeOnly have no wire effect: the writeOnly password is sent
  # and the readOnly id of the response is decoded.
  test "create_account sends the writeOnly property and decodes the readOnly one" do
    {:ok, name} = StubApiClient.start(~s({"id":7,"username":"u","password":"p"}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, %PetstoreClient.Models.Account{id: 7, username: "u", password: "p"}} =
             PetstoreClient.Api.StoreApi.create_account(api, %PetstoreClient.Models.Account{
               username: "u",
               password: "p"
             })

    assert Jason.decode!(StubApiClient.captured(name).body)["password"] == "p"

    Agent.stop(name)
  end
end
