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

  test "bhv operations 102 get_inventory", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.StoreApi.get_inventory(api)
    assert is_map(result)
  end

  test "bhv operations 117 place_order", %{api: api} do
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

  test "bhv operations 103 get_inventory_with_http_info", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.StoreApi.get_inventory_with_http_info(api)
    assert result.status_code >= 200 and result.status_code < 300
    assert result.data != nil
  end

  test "bhv operations 119 place_order_with_http_info", %{api: api} do
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

  test "bhv operations 104 get_order_by_id", %{api: api} do
    assert {:ok, result} =
             PetstoreClient.Api.StoreApi.get_order_by_id(api, :rand.uniform(1_000_000_000))

    assert result != nil
    assert result.id != nil
  end

  test "bhv operations 105 get_order_by_id_with_http_info", %{api: api} do
    assert {:ok, result} =
             PetstoreClient.Api.StoreApi.get_order_by_id_with_http_info(
               api,
               :rand.uniform(1_000_000_000)
             )

    assert result.status_code >= 200 and result.status_code < 300
    assert result.data != nil
  end

  test "bhv operations 099 delete_order", %{api: api} do
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

    # A mock answers one connection, so a retry would find nobody listening.
    client =
      PetstoreClient.DefaultApiClient.new(PetstoreClient.TransportOptions.new(max_retries: 0))

    PetstoreClient.Api.StoreApi.new(client, config)
  end

  test "bhv operations 106 get_order_not_found" do
    api = new_store_api_for_mock(404, "application/json", ~s({"message":"Order not found"}))

    assert {:error, %PetstoreClient.Errors.NotFoundError{status_code: 404}} =
             PetstoreClient.Api.StoreApi.get_order_by_id(api, 99_999)
  end

  test "bhv operations 118 place_order_server_error" do
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

  test "bhv operations 120 reserve_stock decodes the 200 body and the 201 body as their own schemas" do
    existing =
      new_store_api_for_mock(200, "application/json", ~s({"reservationId":7,"quantity":2}))

    assert {:ok, %PetstoreClient.Models.Reservation{reservation_id: 7, quantity: 2}} =
             PetstoreClient.Api.StoreApi.reserve_stock(existing)

    created =
      new_store_api_for_mock(
        201,
        "application/json",
        ~s({"receiptCode":"r-1","expiresAt":"2030-01-01T00:00:00.000+00:00"})
      )

    assert {:ok, %PetstoreClient.Models.ReservationReceipt{receipt_code: "r-1"}} =
             PetstoreClient.Api.StoreApi.reserve_stock(created)
  end

  test "bhv misc 079 a 201 reply from reserve_stock decodes as the receipt declared for 201" do
    created =
      new_store_api_for_mock(
        201,
        "application/json",
        ~s({"receiptCode":"r-2","expiresAt":"2030-01-01T00:00:00.000+00:00"})
      )

    assert {:ok, %PetstoreClient.Models.ReservationReceipt{receipt_code: "r-2"}} =
             PetstoreClient.Api.StoreApi.reserve_stock(created)
  end

  test "bhv misc 080 a body that fits another status's schema is refused with an error" do
    existing =
      new_store_api_for_mock(
        200,
        "application/json",
        ~s({"receiptCode":"r-1","expiresAt":"2030-01-01T00:00:00.000+00:00"})
      )

    assert {:error, _} = PetstoreClient.Api.StoreApi.reserve_stock(existing)
  end

  test "bhv operations 129 a 200 response from reserve_stock decodes as the existing reservation" do
    existing =
      new_store_api_for_mock(200, "application/json", ~s({"reservationId":9,"quantity":3}))

    assert {:ok, %PetstoreClient.Models.Reservation{reservation_id: 9, quantity: 3}} =
             PetstoreClient.Api.StoreApi.reserve_stock(existing)
  end

  test "bhv operations 154 a body is checked only against the schema of its own status" do
    created =
      new_store_api_for_mock(201, "application/json", ~s({"reservationId":1,"quantity":2}))

    assert_raise PetstoreClient.Errors.SerializationError,
                 ~r/Missing required field 'receiptCode'/,
                 fn ->
                   PetstoreClient.Api.StoreApi.reserve_stock(created)
                 end

    existing =
      new_store_api_for_mock(
        200,
        "application/json",
        ~s({"receiptCode":"r-1","expiresAt":"2030-01-01T00:00:00.000+00:00"})
      )

    assert_raise PetstoreClient.Errors.SerializationError,
                 ~r/Missing required field 'reservationId'/,
                 fn ->
                   PetstoreClient.Api.StoreApi.reserve_stock(existing)
                 end
  end

  test "bhv operations 130 a malformed 2xx JSON body propagates the deserialization error" do
    api = new_store_api_for_mock(200, "application/json", ~s({"id":))

    assert_raise PetstoreClient.Errors.SerializationError, ~r/Failed to deserialize JSON/, fn ->
      PetstoreClient.Api.StoreApi.get_order_by_id(api, 1)
    end
  end

  test "bhv operations 136 a bare JSON null body from a model operation throws an API error naming the operation" do
    api = new_store_api_for_mock(200, "application/json", "null")

    assert {:error, %PetstoreClient.Errors.ApiError{message: message}} =
             PetstoreClient.Api.StoreApi.get_order_by_id(api, 1)

    assert message =~ "get_order_by_id"
  end

  test "bhv langelixir 089 an error response names the request" do
    api = new_store_api_for_mock(404, "application/json", ~s({"message":"Order not found"}))

    assert {:error,
            %PetstoreClient.Errors.NotFoundError{
              request_method: "GET",
              request_path: "/store/order/99999"
            }} =
             PetstoreClient.Api.StoreApi.get_order_by_id(api, 99_999)
  end

  # F1161: the typed status routes of the mock answer getOrderById with each
  # status, and the matching status-specific error struct is chosen. The route
  # /test/status/<code>/store/order/<id> is reached by putting the status in
  # the base URL.
  test "bhv operations 110 get_order_by_id picks the status-specific error struct for each typed route" do
    chasm_url = System.fetch_env!("CHASM_HTTP_URL")

    client =
      PetstoreClient.DefaultApiClient.new(PetstoreClient.TransportOptions.new(max_retries: 0))

    cases = [
      {400, PetstoreClient.Errors.BadRequestError},
      {401, PetstoreClient.Errors.UnauthorizedError},
      {403, PetstoreClient.Errors.ForbiddenError},
      {409, PetstoreClient.Errors.ConflictError},
      {418, PetstoreClient.Errors.ClientError},
      {422, PetstoreClient.Errors.UnprocessableEntityError},
      {502, PetstoreClient.Errors.BadGatewayError}
    ]

    for {status, module} <- cases do
      config =
        PetstoreClient.Configuration.new(
          base_url: "#{chasm_url}/test/status/#{status}",
          default_headers: %{}
        )

      api = PetstoreClient.Api.StoreApi.new(client, config)

      assert {:error, error} = PetstoreClient.Api.StoreApi.get_order_by_id(api, 1)
      assert error.__struct__ == module
      assert error.status_code == status
    end
  end

  test "bhv operations 138 an error response is thrown as the exception class for its status through the typed routes" do
    chasm_url = System.fetch_env!("CHASM_HTTP_URL")

    client =
      PetstoreClient.DefaultApiClient.new(PetstoreClient.TransportOptions.new(max_retries: 0))

    cases = [
      {404, PetstoreClient.Errors.NotFoundError},
      {500, PetstoreClient.Errors.InternalServerError}
    ]

    for {status, module} <- cases do
      config =
        PetstoreClient.Configuration.new(
          base_url: "#{chasm_url}/test/status/#{status}",
          default_headers: %{}
        )

      api = PetstoreClient.Api.StoreApi.new(client, config)

      assert {:error, error} = PetstoreClient.Api.StoreApi.get_order_by_id(api, 1)
      assert error.__struct__ == module
      assert error.status_code == status
    end
  end

  test "bhv operations 100 delete_order_not_found" do
    api = new_store_api_for_mock(404, "application/json", ~s({"message":"Order not found"}))

    assert {:error, %PetstoreClient.Errors.NotFoundError{status_code: 404}} =
             PetstoreClient.Api.StoreApi.delete_order(api, 99_999)
  end

  # Nested container deserialization: getGroupedCategories returns
  # Array<Record<string, Category>>. Each leaf of the array-of-maps must be
  # decoded into a typed Category struct, not left as a raw map.
  test "bhv operations 101 get_grouped_categories deserializes nested map leaves into typed Category structs" do
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

  test "bhv operations 132 a missing required path argument names the first required parameter" do
    {:ok, name} = StubApiClient.start("{}")
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert_raise ArgumentError,
                 "Missing the required parameter 'order_id' when calling StoreApi.get_order_by_id",
                 fn -> PetstoreClient.Api.StoreApi.get_order_by_id(api, nil) end

    Agent.stop(name)
  end

  test "bhv operations 126 place_order without an order sends no body and no content type" do
    {:ok, name} = StubApiClient.start(~s({"id":1}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, result} = PetstoreClient.Api.StoreApi.place_order(api)
    assert result.id == 1

    captured = StubApiClient.captured(name)
    assert captured.method == :post
    assert captured.body == nil
    refute Map.has_key?(captured.headers, "Content-Type")

    Agent.stop(name)
  end

  test "bhv operations 018 with_http_info variant returns ApiResult with status, data, headers, and raw_body",
       %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.StoreApi.get_inventory_with_http_info(api)
    assert result.status_code >= 200 and result.status_code < 300
    assert result.data != nil
    assert is_map(result.headers)
    assert is_binary(result.raw_body)
  end

  test "bhv operations 097 content json query param is the object serializer output" do
    {:ok, name} = StubApiClient.start("")
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    photo = %PetstoreClient.Models.PhotoMetadata{
      caption: "a<b",
      taken_at: ~U[2024-01-15 10:30:00Z]
    }

    options = %PetstoreClient.Api.Options.SearchStoreOptions{q: photo}

    _result = PetstoreClient.Api.StoreApi.search_store(api, options)

    captured = StubApiClient.captured(name)
    query = captured.url |> String.split("?") |> List.last() |> URI.decode_query()
    assert query["q"] == PetstoreClient.ObjectSerializer.serialize(photo)

    Agent.stop(name)
  end

  test "bhv operations 109 get_matrix decodes an array of integer arrays" do
    {:ok, name} = StubApiClient.start("[[1,2],[3]]")
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, [[1, 2], [3]]} = PetstoreClient.Api.StoreApi.get_matrix(api)

    Agent.stop(name)
  end

  # A named-enum ($ref) parameter goes on the wire as its declared value in a
  # path, a query string and a header, never as the atom's debug form.
  test "bhv operations 122 get_by_swatch sends the named enum's wire value in path, query and header" do
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

  # An operation with no query, header, form or cookie parameter and no security
  # still takes an options struct, which carries only the per-call settings.
  test "bhv authintegration 011 get_inventory takes an options struct that carries the per-call settings and no auth" do
    {:ok, name} = StubApiClient.start(~s({"available":1}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    options = %PetstoreClient.Api.Options.GetInventoryOptions{
      timeout: 30_000,
      headers: %{"X-Trace" => "1"},
      max_retries: 0
    }

    refute Map.has_key?(options, :auth)
    assert {:ok, %{"available" => 1}} = PetstoreClient.Api.StoreApi.get_inventory(api, options)
    assert StubApiClient.captured(name).headers["X-Trace"] == "1"

    Agent.stop(name)
  end

  test "bhv operations 096 get_inventory takes the per-call settings in opts after a nil options struct" do
    {:ok, name} = StubApiClient.start(~s({"available":1}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, _inventory} =
             PetstoreClient.Api.StoreApi.get_inventory(api, nil, headers: %{"X-Trace" => "2"})

    assert StubApiClient.captured(name).headers["X-Trace"] == "2"

    assert_raise ArgumentError,
                 "Invalid max_retries: must be a non-negative integer (got -1)",
                 fn ->
                   PetstoreClient.Api.StoreApi.get_inventory(
                     api,
                     %PetstoreClient.Api.Options.GetInventoryOptions{max_retries: -1}
                   )
                 end

    Agent.stop(name)
  end

  # F857: an operation declared `security: []` sends no credential, however many
  # a client holds; an operation that inherits the root requirement (petStoreBasic
  # or petStoreBearer) uses the first of those two the client holds.
  test "bhv authintegration 016 get_inventory (security: []) sends no credential even when the client holds several" do
    {:ok, name} = StubApiClient.start(~s({"available":1}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    default = %{auth_headers: %{"Authorization" => "default"}}
    schemes = %{"petStoreBasic" => %{auth_headers: %{"Authorization" => "basic"}}}
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config, default, schemes)

    assert {:ok, _inventory} = PetstoreClient.Api.StoreApi.get_inventory(api)

    refute Map.has_key?(StubApiClient.captured(name).headers, "Authorization")

    Agent.stop(name)
  end

  test "bhv authintegration 007 each operation sends the credential of the scheme it names, not another's" do
    {:ok, name} = StubApiClient.start(~s({"id":1}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    bearer = %{auth_headers: %{"Authorization" => "bearer"}}

    api =
      PetstoreClient.Api.StoreApi.new(StubApiClient, config, nil, %{"petStoreBearer" => bearer})

    assert {:ok, _order} =
             PetstoreClient.Api.StoreApi.place_order(api, %PetstoreClient.Models.Order{id: 1})

    assert StubApiClient.captured(name).headers["Authorization"] == "bearer"

    assert {:ok, _inventory} = PetstoreClient.Api.StoreApi.get_inventory(api)
    refute Map.has_key?(StubApiClient.captured(name).headers, "Authorization")

    Agent.stop(name)
  end

  test "bhv operations 148 an operation that inherits the root security uses the first scheme of that requirement the client holds" do
    {:ok, name} = StubApiClient.start(~s({"id":1}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    bearer = %{auth_headers: %{"Authorization" => "bearer"}}
    basic = %{auth_headers: %{"Authorization" => "basic"}}
    order = %PetstoreClient.Models.Order{id: 1}

    api =
      PetstoreClient.Api.StoreApi.new(StubApiClient, config, nil, %{"petStoreBearer" => bearer})

    assert {:ok, _order} = PetstoreClient.Api.StoreApi.place_order(api, order)
    assert StubApiClient.captured(name).headers["Authorization"] == "bearer"

    both =
      PetstoreClient.Api.StoreApi.new(StubApiClient, config, nil, %{
        "petStoreBearer" => bearer,
        "petStoreBasic" => basic
      })

    assert {:ok, _order} = PetstoreClient.Api.StoreApi.place_order(both, order)
    assert StubApiClient.captured(name).headers["Authorization"] == "basic"

    Agent.stop(name)
  end

  test "bhv operations 107 get_by_swatch omits the optional query parameter and header when they are not set" do
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
  test "bhv operations 121 search_store sends content-encoded query and header parameters through the object serializer" do
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

  test "bhv operations 139 search_store keeps the fractional seconds of a time parameter when non-zero" do
    {:ok, name} = StubApiClient.start("")
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    options = %PetstoreClient.Api.Options.SearchStoreOptions{at: ~T[10:30:00.123456]}
    _result = PetstoreClient.Api.StoreApi.search_store(api, options)

    assert StubApiClient.captured(name).url =~ "at=10%3A30%3A00.123"

    Agent.stop(name)
  end

  test "bhv serialization 008 a content-typed query parameter is JSON-encoded exactly once" do
    {:ok, name} = StubApiClient.start("")
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    photo = %PetstoreClient.Models.PhotoMetadata{
      caption: "a<b",
      taken_at: ~U[2024-01-15 10:30:00Z]
    }

    options = %PetstoreClient.Api.Options.SearchStoreOptions{q: photo}

    _result = PetstoreClient.Api.StoreApi.search_store(api, options)

    captured = StubApiClient.captured(name)
    query = captured.url |> String.split("?") |> List.last() |> URI.decode_query()
    assert query["q"] == PetstoreClient.ObjectSerializer.serialize(photo)

    Agent.stop(name)
  end

  test "bhv operations 128 a content query parameter that cannot be serialized raises a serialization error and sends no request" do
    {:ok, name} = StubApiClient.start("")
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    options = %PetstoreClient.Api.Options.SearchStoreOptions{q: {:no, :json_form}}

    assert_raise PetstoreClient.Errors.SerializationError, fn ->
      PetstoreClient.Api.StoreApi.search_store(api, options)
    end

    assert StubApiClient.captured(name) == nil

    Agent.stop(name)
  end

  # The response-shape operations: each return type is decoded by the
  # generated function itself, so a wrong return type or a decoder that cannot
  # handle a bare enum, a map-of-array or a recursive model fails here.
  test "bhv operations 113 get_swatch decodes a bare enum response to its atom" do
    {:ok, name} = StubApiClient.start(~s("blue"))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, :blue} = PetstoreClient.Api.StoreApi.get_swatch(api)
    assert StubApiClient.captured(name).url == "http://localhost/store/swatch"

    Agent.stop(name)
  end

  test "bhv operations 114 get_swatch_groups decodes an array of maps of enums" do
    {:ok, name} = StubApiClient.start(~s([{"a":"red"},{"b":"blue"}]))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, [%{"a" => :red}, %{"b" => :blue}]} =
             PetstoreClient.Api.StoreApi.get_swatch_groups(api)

    Agent.stop(name)
  end

  test "bhv operations 115 get_timestamp_groups decodes an array of maps of date-times" do
    {:ok, name} = StubApiClient.start(~s([{"a":"2024-01-15T10:30:00Z"}]))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, [%{"a" => %DateTime{} = at}]} =
             PetstoreClient.Api.StoreApi.get_timestamp_groups(api)

    assert DateTime.compare(at, ~U[2024-01-15 10:30:00Z]) == :eq

    Agent.stop(name)
  end

  test "bhv operations 108 get_department decodes the mutually recursive Department and Employee" do
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

  test "bhv operations 116 get_tree decodes the self-referential TreeNode" do
    {:ok, name} = StubApiClient.start(~s({"value":"root","child":{"value":"leaf"}}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, %PetstoreClient.Models.TreeNode{value: "root", child: child}} =
             PetstoreClient.Api.StoreApi.get_tree(api)

    assert %PetstoreClient.Models.TreeNode{value: "leaf"} = child

    Agent.stop(name)
  end

  test "bhv operations 123 get_defaults decodes an empty object with the schema defaults applied" do
    {:ok, name} = StubApiClient.start(~s({}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    assert {:ok, %PetstoreClient.Models.Defaults{retries: 3, mode: :medium}} =
             PetstoreClient.Api.StoreApi.get_defaults(api)

    Agent.stop(name)
  end

  test "bhv operations 112 get_stock_item sends asOf as a date-time and decodes the referenced enums" do
    {:ok, name} = StubApiClient.start(~s({"priority":1,"availability":"Sold"}))
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.StoreApi.new(StubApiClient, config)

    options = %PetstoreClient.Api.Options.GetStockItemOptions{as_of: ~U[2024-01-15 10:30:00Z]}

    assert {:ok, %PetstoreClient.Models.StockItem{priority: :number_1, availability: :sold}} =
             PetstoreClient.Api.StoreApi.get_stock_item(api, options)

    assert StubApiClient.captured(name).url =~ "asOf=2024-01-15T10%3A30%3A00"

    Agent.stop(name)
  end

  test "bhv operations 124 get_order_by_tracking_id puts the canonical lowercase uuid in the path" do
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

  test "bhv operations 111 get_order_by_tracking_id rejects a malformed uuid before sending" do
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
  test "bhv operations 098 create_account sends the writeOnly property and decodes the readOnly one" do
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

  test "bhv misc 011 an options object for an operation with no parameters defaults the timeout and headers when omitted",
       %{api: api} do
    options = %PetstoreClient.Api.Options.GetInventoryOptions{}

    assert options.timeout == nil
    assert options.headers == nil
    assert {:ok, _} = PetstoreClient.Api.StoreApi.get_inventory(api, options)
  end

  test "bhv misc 013 an operation with no parameters returns its result when called with no argument",
       %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.StoreApi.get_inventory(api)
    assert is_map(result)
  end

  test "bhv misc 114 a success response is decoded by the schema for the status actually received" do
    created =
      new_store_api_for_mock(
        201,
        "application/json",
        ~s({"receiptCode":"r-3","expiresAt":"2030-01-01T00:00:00.000+00:00"})
      )

    assert {:ok, %PetstoreClient.Models.ReservationReceipt{receipt_code: "r-3"}} =
             PetstoreClient.Api.StoreApi.reserve_stock(created)
  end
end
