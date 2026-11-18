defmodule PetstoreClient.ClientTest do
  use ExUnit.Case, async: true

  test "bhv lifecycle 012 construct with authenticator only" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")

    client = PetstoreClient.Client.new(authenticator)

    assert %PetstoreClient.Client{} = client
  end

  test "bhv lifecycle 010 construct with authenticator and nil transport options" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")

    client = PetstoreClient.Client.new(authenticator, nil)

    assert %PetstoreClient.Client{} = client
  end

  test "bhv lifecycle 011 construct with authenticator and transport options" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")
    transport = PetstoreClient.TransportOptions.new()

    client = PetstoreClient.Client.new(authenticator, transport)

    assert %PetstoreClient.Client{} = client
  end

  test "construct with a custom Req adapter in the transport options" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")
    adapter = fn request -> {request, Req.Response.new(status: 200)} end
    transport = PetstoreClient.TransportOptions.new(req_options: [adapter: adapter])

    client = PetstoreClient.Client.new(authenticator, transport)

    assert %PetstoreClient.Client{} = client
  end

  test "close releases the transport and the authenticator's token manager" do
    authenticator =
      PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
        "/api/v3",
        "id",
        "secret",
        "https://auth.example.com/token",
        []
      )

    client = PetstoreClient.Client.new(authenticator)
    manager = authenticator.token_manager
    assert Process.alive?(manager)

    assert PetstoreClient.Client.close(client) == :ok
    refute Process.alive?(manager)
    assert PetstoreClient.Client.close(client) == :ok

    err =
      assert_raise RuntimeError, fn ->
        PetstoreClient.DefaultApiClient.send_request(
          client.api_client,
          :get,
          "http://127.0.0.1:1/never",
          %{},
          nil
        )
      end

    assert err.message == "ApiClient has been closed and can no longer send requests"
  end

  test "close works for an authenticator that starts nothing" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")
    client = PetstoreClient.Client.new(authenticator)

    assert PetstoreClient.Client.close(client) == :ok
  end

  # An authenticator that tells the test process each time the client releases it.
  defmodule ClosingAuthenticator do
    use PetstoreClient.Auth.BaseAuthenticator

    defstruct [:notify, :label]

    @impl true
    def host(%__MODULE__{}), do: "http://closing.invalid"

    @impl true
    def auth_headers(%__MODULE__{}), do: %{}

    def close(%__MODULE__{notify: notify, label: label}), do: send(notify, {:released, label})
  end

  # Answers every request with an empty 200 once the test sends it `:proceed`,
  # and tells the test process when a request is waiting.
  defp blocked_transport do
    test_pid = self()

    adapter = fn request ->
      send(test_pid, {:waiting, self()})

      receive do
        :proceed -> :ok
      end

      {request, Req.Response.new(status: 200, body: "")}
    end

    {adapter,
     PetstoreClient.TransportOptions.new(req_options: [adapter: adapter], max_retries: 0)}
  end

  defp call_through(client) do
    state = %{
      api_client: client.api_client,
      authenticator: client.authenticator,
      scheme_authenticators: client.schemes,
      config: PetstoreClient.Configuration.new(base_url: "http://closing.invalid")
    }

    fn ->
      PetstoreClient.Api.BaseApi.invoke_api_for_result(
        state,
        :get,
        "/slow",
        %{},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil,
        PetstoreClient.Api.BaseApi.no_auth()
      )
    end
  end

  test "bhv lifecycle 021 closing the client lets a running request finish and releases the authenticator after it" do
    {_adapter, transport} = blocked_transport()
    authenticator = %ClosingAuthenticator{notify: self()}
    client = PetstoreClient.Client.new(authenticator, transport)
    call = call_through(client)

    task = Task.async(call)
    assert_receive {:waiting, in_flight}, 5_000

    assert PetstoreClient.Client.close(client) == :ok
    refute_received {:released, _label}

    send(in_flight, :proceed)
    assert {:ok, %{status_code: 200}} = Task.await(task)
    assert_received {:released, nil}
  end

  test "bhv lifecycle 008 a request that starts after close fails with the one canonical client closed error" do
    authenticator = %ClosingAuthenticator{notify: self()}
    {_adapter, transport} = blocked_transport()
    client = PetstoreClient.Client.new(authenticator, transport)
    call = call_through(client)

    assert PetstoreClient.Client.close(client) == :ok

    error = assert_raise RuntimeError, call
    assert error.message == "ApiClient has been closed and can no longer send requests"
    refute_received {:waiting, _pid}
  end

  test "a request that starts while another is in flight after close still fails" do
    {_adapter, transport} = blocked_transport()
    client = PetstoreClient.Client.new(%ClosingAuthenticator{notify: self()}, transport)
    call = call_through(client)

    task = Task.async(call)
    assert_receive {:waiting, in_flight}, 5_000
    assert PetstoreClient.Client.close(client) == :ok

    error = assert_raise RuntimeError, call
    assert error.message == "ApiClient has been closed and can no longer send requests"

    send(in_flight, :proceed)
    assert {:ok, _result} = Task.await(task)
  end

  test "close twice releases the authenticator once" do
    client = PetstoreClient.Client.new(%ClosingAuthenticator{notify: self()})

    assert PetstoreClient.Client.close(client) == :ok
    assert_received {:released, nil}
    assert PetstoreClient.Client.close(client) == :ok
    refute_received {:released, _label}
  end

  test "closing after the last in-flight request does not release the authenticator a second time" do
    {_adapter, transport} = blocked_transport()
    client = PetstoreClient.Client.new(%ClosingAuthenticator{notify: self()}, transport)
    call = call_through(client)

    task = Task.async(call)
    assert_receive {:waiting, in_flight}, 5_000
    assert PetstoreClient.Client.close(client) == :ok
    send(in_flight, :proceed)
    assert {:ok, _result} = Task.await(task)
    assert_received {:released, nil}

    assert PetstoreClient.Client.close(client) == :ok
    refute_received {:released, _label}
  end

  test "bhv lifecycle 009 close never touches a transport the caller supplied" do
    {adapter, transport} = blocked_transport()
    first = PetstoreClient.Client.new(%ClosingAuthenticator{notify: self()}, transport)
    second = PetstoreClient.Client.new(%ClosingAuthenticator{notify: self()}, transport)

    assert PetstoreClient.Client.close(first) == :ok
    refute_received {:waiting, _pid}
    assert first.api_client.transport_options.req_options[:adapter] == adapter

    task = Task.async(call_through(second))
    assert_receive {:waiting, in_flight}, 5_000
    send(in_flight, :proceed)
    assert {:ok, %{status_code: 200}} = Task.await(task)
  end

  test "bhv lifecycle 015 construct with one credential per security scheme and no authenticator" do
    credential = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "scheme-token")

    client = PetstoreClient.Client.new(nil, nil, pet_store_basic: credential)

    assert client.authenticator == nil
    assert client.schemes == %{"petStoreBasic" => credential}
    assert client.pet.scheme_authenticators == client.schemes
    assert client.pet.authenticator == nil
  end

  test "the base URL comes from the authenticator first, then the first scheme credential" do
    default = PetstoreClient.Auth.BearerAuthenticator.new("https://default.example.com", "t")
    credential = PetstoreClient.Auth.BearerAuthenticator.new("https://scheme.example.com", "t")

    with_default = PetstoreClient.Client.new(default, nil, pet_store_basic: credential)
    assert with_default.pet.config.base_url == "https://default.example.com"

    schemes_only = PetstoreClient.Client.new(nil, nil, pet_store_basic: credential)
    assert schemes_only.pet.config.base_url == "https://scheme.example.com"
  end

  test "a key that names no security scheme of the API raises ArgumentError" do
    credential = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "scheme-token")

    assert_raise ArgumentError, ~r/unknown keys \[:no_such_scheme\]/, fn ->
      PetstoreClient.Client.new(credential, nil, no_such_scheme: credential)
    end
  end

  test "a client without an authenticator and without any scheme credential raises ArgumentError" do
    assert_raise ArgumentError, ~r/At least one credential is required/, fn ->
      PetstoreClient.Client.new(nil)
    end

    assert_raise ArgumentError, ~r/At least one credential is required/, fn ->
      PetstoreClient.Client.new(nil, nil, pet_store_basic: nil)
    end
  end

  test "close releases the authenticator and every scheme credential once" do
    default = %ClosingAuthenticator{notify: self(), label: :default}
    credential = %ClosingAuthenticator{notify: self(), label: :scheme}
    client = PetstoreClient.Client.new(default, nil, pet_store_basic: credential)

    assert PetstoreClient.Client.close(client) == :ok
    assert PetstoreClient.Client.close(client) == :ok

    assert_received {:released, :default}
    assert_received {:released, :scheme}
    refute_received {:released, _label}
  end

  test "bhv lifecycle 002 api key header rejects CR, LF and non-ASCII keys at construction" do
    for key <- ["abc\r", "abc\n", "kéy"] do
      assert_raise ArgumentError, fn ->
        PetstoreClient.Auth.ApiKeyAuthenticator.new("/api/v3", "X-Api-Key", key, :header)
      end
    end
  end

  test "bhv lifecycle 003 api key query accepts non-ASCII keys and values" do
    authenticator =
      PetstoreClient.Auth.ApiKeyAuthenticator.new("/api/v3", "api_key", "kéy ñ", :query)

    assert PetstoreClient.Auth.ApiKeyAuthenticator.query_params(authenticator) == %{
             "api_key" => "kéy ñ"
           }
  end

  test "bhv lifecycle 007 closing a client leaves its supplied transport usable for another client" do
    adapter = fn request -> {request, Req.Response.new(status: 200, body: "")} end

    transport =
      PetstoreClient.TransportOptions.new(req_options: [adapter: adapter], max_retries: 0)

    first = PetstoreClient.Client.new(%ClosingAuthenticator{notify: self()}, transport)
    second = PetstoreClient.Client.new(%ClosingAuthenticator{notify: self()}, transport)

    assert PetstoreClient.Client.close(first) == :ok

    assert {:ok, %{status_code: 200}} = call_through(second).()
  end

  test "bhv lifecycle 017 takes a token provider instead of a token and calls it for every request" do
    {:ok, counter} = Agent.start_link(fn -> 0 end)

    provider = fn ->
      "t" <> Integer.to_string(Agent.get_and_update(counter, fn n -> {n + 1, n + 1} end))
    end

    client =
      PetstoreClient.Client.new(PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", provider))

    assert PetstoreClient.Auth.BearerAuthenticator.auth_headers(client.authenticator)[
             "Authorization"
           ] == "Bearer t1"

    assert PetstoreClient.Auth.BearerAuthenticator.auth_headers(client.authenticator)[
             "Authorization"
           ] == "Bearer t2"
  end

  test "bhv lifecycle 006 BearerAuthenticator rejects CR/LF and non-ASCII (RFC 7230 §3.2.6)" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "tok\r\nInjected: yes")
    end

    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "ñoño")
    end
  end

  test "bhv lifecycle 005 BearerAuthenticator rejects an empty or whitespace token" do
    # bearer-no-empty-token-guard: an empty/whitespace token would emit a
    # bare "Authorization: Bearer " header, sending the request
    # unauthenticated, so new/2 must reject it.
    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "")
    end

    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "   ")
    end
  end

  test "bhv lifecycle 004 ApiKeyAuthenticator :header rejects CR/LF and non-ASCII (RFC 7230 §3.2.6)" do
    # HEADER location must reject anything outside printable ASCII + TAB
    # to prevent header injection (\r\n) and silent UTF-8 mangling that
    # varies per HTTP lib.
    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.ApiKeyAuthenticator.new(
        "/api/v3",
        "X-Api-Key",
        "abc\r\nInjected: yes",
        :header
      )
    end

    assert_raise ArgumentError, fn ->
      PetstoreClient.Auth.ApiKeyAuthenticator.new("/api/v3", "X-Api-Key", "kéy", :header)
    end

    # Non-header locations accept arbitrary chars.
    query_auth = PetstoreClient.Auth.ApiKeyAuthenticator.new("/api/v3", "api_key", "kéy", :query)

    assert PetstoreClient.Auth.ApiKeyAuthenticator.query_params(query_auth) == %{
             "api_key" => "kéy"
           }
  end

  test "bhv lifecycle 001 API groups are accessible" do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("/api/v3", "test-token")

    client = PetstoreClient.Client.new(authenticator)

    assert client.pet != nil
    assert client.problems != nil
    assert client.store != nil
  end

  test "bhv mocking 001 every group has a behaviour the module implements" do
    pet_behaviours =
      PetstoreClient.Api.PetApi.module_info(:attributes)
      |> Keyword.get_values(:behaviour)
      |> List.flatten()

    assert PetstoreClient.Api.PetApiBehaviour in pet_behaviours

    pet_listed =
      PetstoreClient.Api.PetApiBehaviour.behaviour_info(:callbacks) |> Enum.map(&elem(&1, 0))

    assert :add_pet in pet_listed
    assert :add_pet! in pet_listed
    assert :add_pet_with_http_info in pet_listed
    assert :add_pet_photos in pet_listed
    assert :add_pet_photos! in pet_listed
    assert :add_pet_photos_with_http_info in pet_listed
    assert :add_pet_treatment in pet_listed
    assert :add_pet_treatment! in pet_listed
    assert :add_pet_treatment_with_http_info in pet_listed
    assert :delete_pet in pet_listed
    assert :delete_pet! in pet_listed
    assert :delete_pet_with_http_info in pet_listed
    assert :download_pet_document in pet_listed
    assert :download_pet_document! in pet_listed
    assert :download_pet_document_with_http_info in pet_listed
    assert :find_pets_born_on in pet_listed
    assert :find_pets_born_on! in pet_listed
    assert :find_pets_born_on_with_http_info in pet_listed
    assert :find_pets_by_status in pet_listed
    assert :find_pets_by_status! in pet_listed
    assert :find_pets_by_status_with_http_info in pet_listed
    assert :get_external_pet_info in pet_listed
    assert :get_external_pet_info! in pet_listed
    assert :get_external_pet_info_with_http_info in pet_listed
    assert :get_multi_server_pet_info in pet_listed
    assert :get_multi_server_pet_info! in pet_listed
    assert :get_multi_server_pet_info_with_http_info in pet_listed
    assert :get_pet_avatar in pet_listed
    assert :get_pet_avatar! in pet_listed
    assert :get_pet_avatar_with_http_info in pet_listed
    assert :get_pet_avatar_thumbnail in pet_listed
    assert :get_pet_avatar_thumbnail! in pet_listed
    assert :get_pet_avatar_thumbnail_with_http_info in pet_listed
    assert :get_pet_by_id in pet_listed
    assert :get_pet_by_id! in pet_listed
    assert :get_pet_by_id_with_http_info in pet_listed
    assert :get_pet_by_name in pet_listed
    assert :get_pet_by_name! in pet_listed
    assert :get_pet_by_name_with_http_info in pet_listed
    assert :get_pet_passport in pet_listed
    assert :get_pet_passport! in pet_listed
    assert :get_pet_passport_with_http_info in pet_listed
    assert :get_pet_photo in pet_listed
    assert :get_pet_photo! in pet_listed
    assert :get_pet_photo_with_http_info in pet_listed
    assert :get_pet_summary in pet_listed
    assert :get_pet_summary! in pet_listed
    assert :get_pet_summary_with_http_info in pet_listed
    assert :get_pet_tag in pet_listed
    assert :get_pet_tag! in pet_listed
    assert :get_pet_tag_with_http_info in pet_listed
    assert :get_staging_pet_info in pet_listed
    assert :get_staging_pet_info! in pet_listed
    assert :get_staging_pet_info_with_http_info in pet_listed
    assert :set_pet_avatar in pet_listed
    assert :set_pet_avatar! in pet_listed
    assert :set_pet_avatar_with_http_info in pet_listed
    assert :set_pet_avatar_thumbnail in pet_listed
    assert :set_pet_avatar_thumbnail! in pet_listed
    assert :set_pet_avatar_thumbnail_with_http_info in pet_listed
    assert :set_pet_preferences in pet_listed
    assert :set_pet_preferences! in pet_listed
    assert :set_pet_preferences_with_http_info in pet_listed
    assert :update_pet in pet_listed
    assert :update_pet! in pet_listed
    assert :update_pet_with_http_info in pet_listed
    assert :upload_pet_certificate in pet_listed
    assert :upload_pet_certificate! in pet_listed
    assert :upload_pet_certificate_with_http_info in pet_listed
    assert :upload_pet_document in pet_listed
    assert :upload_pet_document! in pet_listed
    assert :upload_pet_document_with_http_info in pet_listed

    problems_behaviours =
      PetstoreClient.Api.ProblemsApi.module_info(:attributes)
      |> Keyword.get_values(:behaviour)
      |> List.flatten()

    assert PetstoreClient.Api.ProblemsApiBehaviour in problems_behaviours

    problems_listed =
      PetstoreClient.Api.ProblemsApiBehaviour.behaviour_info(:callbacks) |> Enum.map(&elem(&1, 0))

    assert :get_fallback in problems_listed
    assert :get_fallback! in problems_listed
    assert :get_fallback_with_http_info in problems_listed
    assert :get_member in problems_listed
    assert :get_member! in problems_listed
    assert :get_member_with_http_info in problems_listed
    assert :get_project in problems_listed
    assert :get_project! in problems_listed
    assert :get_project_with_http_info in problems_listed
    assert :get_range in problems_listed
    assert :get_range! in problems_listed
    assert :get_range_with_http_info in problems_listed
    assert :get_upstream in problems_listed
    assert :get_upstream! in problems_listed
    assert :get_upstream_with_http_info in problems_listed
    assert :take_lock in problems_listed
    assert :take_lock! in problems_listed
    assert :take_lock_with_http_info in problems_listed

    store_behaviours =
      PetstoreClient.Api.StoreApi.module_info(:attributes)
      |> Keyword.get_values(:behaviour)
      |> List.flatten()

    assert PetstoreClient.Api.StoreApiBehaviour in store_behaviours

    store_listed =
      PetstoreClient.Api.StoreApiBehaviour.behaviour_info(:callbacks) |> Enum.map(&elem(&1, 0))

    assert :create_account in store_listed
    assert :create_account! in store_listed
    assert :create_account_with_http_info in store_listed
    assert :delete_order in store_listed
    assert :delete_order! in store_listed
    assert :delete_order_with_http_info in store_listed
    assert :get_by_swatch in store_listed
    assert :get_by_swatch! in store_listed
    assert :get_by_swatch_with_http_info in store_listed
    assert :get_defaults in store_listed
    assert :get_defaults! in store_listed
    assert :get_defaults_with_http_info in store_listed
    assert :get_department in store_listed
    assert :get_department! in store_listed
    assert :get_department_with_http_info in store_listed
    assert :get_grouped_categories in store_listed
    assert :get_grouped_categories! in store_listed
    assert :get_grouped_categories_with_http_info in store_listed
    assert :get_inventory in store_listed
    assert :get_inventory! in store_listed
    assert :get_inventory_with_http_info in store_listed
    assert :get_matrix in store_listed
    assert :get_matrix! in store_listed
    assert :get_matrix_with_http_info in store_listed
    assert :get_order_by_id in store_listed
    assert :get_order_by_id! in store_listed
    assert :get_order_by_id_with_http_info in store_listed
    assert :get_order_by_tracking_id in store_listed
    assert :get_order_by_tracking_id! in store_listed
    assert :get_order_by_tracking_id_with_http_info in store_listed
    assert :get_stock_item in store_listed
    assert :get_stock_item! in store_listed
    assert :get_stock_item_with_http_info in store_listed
    assert :get_swatch in store_listed
    assert :get_swatch! in store_listed
    assert :get_swatch_with_http_info in store_listed
    assert :get_swatch_groups in store_listed
    assert :get_swatch_groups! in store_listed
    assert :get_swatch_groups_with_http_info in store_listed
    assert :get_timestamp_groups in store_listed
    assert :get_timestamp_groups! in store_listed
    assert :get_timestamp_groups_with_http_info in store_listed
    assert :get_tree in store_listed
    assert :get_tree! in store_listed
    assert :get_tree_with_http_info in store_listed
    assert :place_order in store_listed
    assert :place_order! in store_listed
    assert :place_order_with_http_info in store_listed
    assert :reserve_stock in store_listed
    assert :reserve_stock! in store_listed
    assert :reserve_stock_with_http_info in store_listed
    assert :search_store in store_listed
    assert :search_store! in store_listed
    assert :search_store_with_http_info in store_listed
  end

  test "bhv mocking 002 the group behaviour lists exactly the public operations of the module" do
    listed =
      PetstoreClient.Api.PetApiBehaviour.behaviour_info(:callbacks)
      |> Enum.map(&elem(&1, 0))
      |> Enum.uniq()
      |> Enum.sort()

    declared =
      PetstoreClient.Api.PetApi.__info__(:functions)
      |> Enum.map(&elem(&1, 0))
      |> Enum.reject(&(&1 == :new or String.starts_with?(Atom.to_string(&1), "__")))
      |> Enum.uniq()
      |> Enum.sort()

    assert listed == declared

    listed =
      PetstoreClient.Api.ProblemsApiBehaviour.behaviour_info(:callbacks)
      |> Enum.map(&elem(&1, 0))
      |> Enum.uniq()
      |> Enum.sort()

    declared =
      PetstoreClient.Api.ProblemsApi.__info__(:functions)
      |> Enum.map(&elem(&1, 0))
      |> Enum.reject(&(&1 == :new or String.starts_with?(Atom.to_string(&1), "__")))
      |> Enum.uniq()
      |> Enum.sort()

    assert listed == declared

    listed =
      PetstoreClient.Api.StoreApiBehaviour.behaviour_info(:callbacks)
      |> Enum.map(&elem(&1, 0))
      |> Enum.uniq()
      |> Enum.sort()

    declared =
      PetstoreClient.Api.StoreApi.__info__(:functions)
      |> Enum.map(&elem(&1, 0))
      |> Enum.reject(&(&1 == :new or String.starts_with?(Atom.to_string(&1), "__")))
      |> Enum.uniq()
      |> Enum.sort()

    assert listed == declared
  end
end
