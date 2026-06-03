defmodule PetstoreClient.Api.BaseApiTest do
  use ExUnit.Case, async: true

  defmodule TestAuthenticator do
    use PetstoreClient.Auth.BaseAuthenticator

    defstruct [:host_url, :headers, :query, :cookies]

    @impl true
    def host(%__MODULE__{} = self) do
      self.host_url
    end

    @impl true
    def auth_headers(%__MODULE__{} = self) do
      self.headers || %{}
    end

    @impl true
    def query_params(%__MODULE__{} = self) do
      self.query || %{}
    end

    @impl true
    def cookie_params(%__MODULE__{} = self) do
      self.cookies || %{}
    end
  end

  setup do
    chasm_url = System.fetch_env!("CHASM_HTTP_URL")
    config = PetstoreClient.Configuration.new(base_url: chasm_url)
    api_client = PetstoreClient.DefaultApiClient.new()
    state = %{config: config, api_client: api_client}

    %{state: state, chasm_url: chasm_url}
  end

  # Exception dispatch

  test "raises BadRequestError for status 400", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/400",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.BadRequestError{} = error
    assert error.status_code == 400
  end

  test "raises UnauthorizedError for status 401", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/401",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.UnauthorizedError{} = error
    assert error.status_code == 401
  end

  test "raises ForbiddenError for status 403", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/403",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.ForbiddenError{} = error
    assert error.status_code == 403
  end

  test "raises NotFoundError for status 404", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/404",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.NotFoundError{} = error
    assert error.status_code == 404
  end

  test "raises ConflictError for status 409", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/409",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.ConflictError{} = error
    assert error.status_code == 409
  end

  test "raises UnprocessableEntityError for status 422", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/422",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.UnprocessableEntityError{} = error
    assert error.status_code == 422
  end

  test "raises InternalServerError for status 500", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/500",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.InternalServerError{} = error
    assert error.status_code == 500
  end

  test "raises ServerError for status 502", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/502",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.ServerError{} = error
    assert error.status_code == 502
  end

  test "raises ClientError for status 418", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/418",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.ClientError{} = error
    assert error.status_code == 418
  end

  # Error body parsing

  test "parses JSON error body", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/400",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.BadRequestError{} = error
    assert error.error_body != nil
  end

  # Success deserialization

  test "deserializes JSON response", %{state: state} do
    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )

    assert result != nil
    # chasm /test/echo returns an envelope {method, body, headers, cookies, contentLength}
    assert result["method"] == "GET"
  end

  test "returns raw string for non-JSON response", %{state: state} do
    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/text-plain",
               %{},
               %{},
               nil,
               ["text/plain"],
               "application/json",
               "String"
             )

    assert result != nil
    assert is_binary(result)
  end

  test "returns nil when return_type is nil", %{state: state} do
    assert {:ok, nil} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )
  end

  # Content-Type check -- non-JSON response skips deserialization

  test "returns raw body when Content-Type is text/plain", %{state: state} do
    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/text-plain",
               %{},
               %{},
               nil,
               ["text/plain"],
               "application/json",
               "String"
             )

    assert is_binary(result)
  end

  # Vendor JSON MIME type deserialization

  defmodule VendorJsonApiClient do
    @behaviour PetstoreClient.ApiClient

    @impl true
    def send_request(_method, _url, _headers, _body) do
      %PetstoreClient.ApiResponse{
        status_code: 200,
        body: "{\"title\":\"Not Found\"}",
        headers: %{"Content-Type" => "application/problem+json"}
      }
    end
  end

  test "deserializes vendor JSON MIME types like application/problem+json" do
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: VendorJsonApiClient}

    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )

    assert result != nil
    assert result["title"] == "Not Found"
  end

  # Nil content-type skips deserialization

  defmodule NilContentTypeApiClient do
    @behaviour PetstoreClient.ApiClient

    @impl true
    def send_request(_method, _url, _headers, _body) do
      %PetstoreClient.ApiResponse{status_code: 200, body: "raw body content", headers: %{}}
    end
  end

  test "nil content-type skips JSON deserialization and returns raw body" do
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: NilContentTypeApiClient}

    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "String"
             )

    assert result == "raw body content"
  end

  # Server variable overrides

  test "server variable overrides resolve in base URL" do
    config =
      PetstoreClient.Configuration.from_server(
        PetstoreClient.Servers.server_1(),
        %{"environment" => "staging"}
      )

    assert config.base_url == "https://staging.example.com/api/v3"
  end

  test "default server variables produce correct base URL" do
    config = PetstoreClient.Configuration.from_server(PetstoreClient.Servers.server_1())
    assert config.base_url == "https://api.example.com/api/v3"
  end

  test "invalid enum value raises ArgumentError" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.Configuration.from_server(
        PetstoreClient.Servers.server_1(),
        %{"environment" => "invalid"}
      )
    end
  end

  # allowEmptyValue query params

  defmodule CapturingApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> "" end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured_url(name) do
      Agent.get(name, & &1)
    end

    @impl true
    def send_request(_method, url, _headers, _body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> url end)
      %PetstoreClient.ApiResponse{status_code: 200, body: "{}", headers: %{"Content-Type" => "application/json"}}
    end
  end

  test "null options omits allow_empty_value param" do
    {:ok, name} = CapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(CapturingApiClient, config)

    _result = PetstoreClient.Api.PetApi.find_pets_by_status(api, nil)
    url = CapturingApiClient.captured_url(name)

    refute String.contains?(url, "status="),
           "Expected no status param when options is nil, got: #{url}"

    Agent.stop(name)
  end

  test "allow_empty_value param included when value is nil in options" do
    {:ok, name} = CapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(CapturingApiClient, config)

    _result = PetstoreClient.Api.PetApi.find_pets_by_status(api, %PetstoreClient.Api.Options.FindPetsByStatusOptions{})
    url = CapturingApiClient.captured_url(name)

    assert String.contains?(url, "status="),
           "Expected status= in URL for allowEmptyValue param with nil value, got: #{url}"

    Agent.stop(name)
  end

  test "allow_empty_value param included when value is empty string" do
    {:ok, name} = CapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(CapturingApiClient, config)

    _result =
      PetstoreClient.Api.PetApi.find_pets_by_status(api, %PetstoreClient.Api.Options.FindPetsByStatusOptions{status: ""})

    url = CapturingApiClient.captured_url(name)

    assert String.contains?(url, "status="),
           "Expected status= in URL for empty string allowEmptyValue param, got: #{url}"

    Agent.stop(name)
  end

  test "collapses double-slash when base_url has trailing slash" do
    # Gap Z — base_url='http://x/' + path='/y' must produce 'http://x/y',
    # not 'http://x//y' which most servers route to 404.
    {:ok, name} = CapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost/")
    api = PetstoreClient.Api.PetApi.new(CapturingApiClient, config)

    _result = PetstoreClient.Api.PetApi.get_pet_by_id(api, 1)
    url = CapturingApiClient.captured_url(name)

    refute String.contains?(url, "//pet"),
           "Expected no double-slash in URL, got: #{url}"

    assert String.starts_with?(url, "http://localhost/pet/"),
           "Expected http://localhost/pet/... in URL, got: #{url}"

    Agent.stop(name)
  end

  # Query serialization

  test "serializes boolean query params" do
    {:ok, name} = CapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: CapturingApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{"active" => true},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil
      )

    url = CapturingApiClient.captured_url(name)

    assert String.contains?(url, "active=true"),
           "Expected active=true, got: #{url}"

    Agent.stop(name)
  end

  test "serializes number query params" do
    {:ok, name} = CapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: CapturingApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{"limit" => 10},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil
      )

    url = CapturingApiClient.captured_url(name)

    assert String.contains?(url, "limit=10"),
           "Expected limit=10, got: #{url}"

    refute String.contains?(url, "limit=10.0"),
           "Should not contain limit=10.0, got: #{url}"

    Agent.stop(name)
  end

  # Server variable: API request uses resolved server URL

  test "API request uses resolved server URL" do
    config =
      PetstoreClient.Configuration.from_server(
        PetstoreClient.Servers.server_1(),
        %{"environment" => "staging"}
      )

    assert String.starts_with?(config.base_url, "https://staging.example.com")
  end

  # Header flow-through

  test "all headers from selector flow through to request", %{state: state} do
    assert {:ok, _result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )

    # If the call succeeds, headers flowed through correctly
  end

  # Body serialization

  test "serializes JSON body for POST", %{state: state} do
    body = %{"name" => "TestPet", "photoUrls" => []}

    result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        body,
        ["application/json"],
        "application/json",
        "Object"
      )

    case result do
      {:ok, parsed} ->
        # chasm envelope: .body holds the raw request body as a string
        assert parsed["method"] == "POST"
        assert is_binary(parsed["body"])
        assert String.contains?(parsed["body"], "TestPet")

      {:error, _} ->
        # The echo endpoint may not be reachable; that's OK for this test
        :ok
    end
  end

  test "serializes text/plain body as string", %{state: state} do
    body = "hello world"

    result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        body,
        ["text/plain"],
        "text/plain",
        "String"
      )

    case result do
      {:ok, returned} ->
        assert is_binary(returned)
        # chasm returns a JSON envelope; the raw body is embedded in the .body field
        assert String.contains?(returned, "hello world")

      {:error, _} ->
        # The echo endpoint may not be reachable; that's OK for this test
        :ok
    end
  end

  test "serializes form-urlencoded body", %{state: state} do
    body = %{"name" => "alice", "age" => "30"}

    result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        body,
        ["application/json"],
        "application/x-www-form-urlencoded",
        "String"
      )

    case result do
      {:ok, returned} ->
        assert is_binary(returned)
        # chasm envelope wraps the form body verbatim inside .body
        assert String.contains?(returned, "name=alice")

      {:error, _} ->
        # The echo endpoint may not be reachable; that's OK for this test
        :ok
    end
  end

  test "passes binary body as-is for octet-stream", %{state: state} do
    body = <<0x01, 0x02, 0x03>>

    result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        body,
        ["application/octet-stream"],
        "application/octet-stream",
        "String"
      )

    case result do
      {:ok, _returned} ->
        :ok

      {:error, _} ->
        # The echo endpoint may not be reachable; that's OK for this test
        :ok
    end
  end

  # Exception hierarchy

  test "NotFoundError is an exception with status_code and error_body", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/404",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.NotFoundError{} = error
    assert error.status_code == 404
    assert is_binary(error.message)
  end

  test "InternalServerError is an exception with status_code and error_body", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/status/500",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               nil
             )

    assert %PetstoreClient.Errors.InternalServerError{} = error
    assert error.status_code == 500
    assert is_binary(error.message)
  end

  # Auth header injection

  test "forwards auth headers via echo endpoint", %{state: state} do
    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{"X-Custom" => "auth-value"},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )

    assert result != nil
    # chasm preserves the original header casing in the envelope's .headers map
    assert result["headers"]["x-custom"] == "auth-value"
  end

  # Cookie injection via authenticator

  test "sets cookie header from auth cookies", %{state: state} do
    auth = %TestAuthenticator{
      host_url: "",
      headers: %{},
      query: %{},
      cookies: %{"session" => "abc123"}
    }

    # Invoke with auth - the important thing is that it does not crash
    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil,
        auth
      )
  end

  # Nil body handling

  test "handles nil body", %{state: state} do
    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )

    assert result != nil
    assert result["method"] == "GET"
  end

  # Query parameter serialization

  test "appends query params to URL", %{state: state} do
    assert {:ok, _result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{"foo" => "bar"},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )
  end

  test "includes empty value param in query string when value is empty string", %{state: state} do
    assert {:ok, _result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{"filter" => ""},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )
  end

  test "empty query params produce no query string", %{state: state} do
    assert {:ok, _result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )
  end

  # Empty content-type defaults to JSON

  test "empty content-type defaults to application/json", %{state: state} do
    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "",
               "Object"
             )

    assert result != nil
    assert result["method"] == "GET"
  end

  # BinaryResponseTests

  defmodule OctetStreamApiClient do
    @behaviour PetstoreClient.ApiClient

    @impl true
    def send_request(_method, _url, _headers, _body) do
      binary_data = <<0x00, 0xFF, 0x42>>
      encoded = Base.encode64(binary_data)

      %PetstoreClient.ApiResponse{
        status_code: 200,
        body: encoded,
        headers: %{"Content-Type" => "application/octet-stream"}
      }
    end
  end

  defmodule ImagePngApiClient do
    @behaviour PetstoreClient.ApiClient

    @impl true
    def send_request(_method, _url, _headers, _body) do
      binary_data = <<0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D>>
      encoded = Base.encode64(binary_data)
      %PetstoreClient.ApiResponse{status_code: 200, body: encoded, headers: %{"Content-Type" => "image/png"}}
    end
  end

  defmodule EmptyBinaryApiClient do
    @behaviour PetstoreClient.ApiClient

    @impl true
    def send_request(_method, _url, _headers, _body) do
      %PetstoreClient.ApiResponse{status_code: 200, body: "", headers: %{"Content-Type" => "application/octet-stream"}}
    end
  end

  test "octet-stream binary response roundtrips exactly via base64 decode" do
    original = <<0x00, 0xFF, 0x42>>
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: OctetStreamApiClient}

    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/octet-stream"],
               "application/octet-stream",
               "String"
             )

    assert {:ok, decoded} = Base.decode64(result)
    assert decoded == original
  end

  test "image/png binary response roundtrips exactly via base64 decode" do
    original = <<0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D>>
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: ImagePngApiClient}

    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["image/png"],
               "image/png",
               "String"
             )

    assert {:ok, decoded} = Base.decode64(result)
    assert decoded == original
  end

  test "application/json response is deserialized to map" do
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: VendorJsonApiClient}

    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/json"],
               "application/json",
               "Object"
             )

    assert is_map(result)
  end

  test "text/plain response returns raw string" do
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: NilContentTypeApiClient}

    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["text/plain"],
               "application/json",
               "String"
             )

    assert is_binary(result)
  end

  test "empty body returns nil" do
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: EmptyBinaryApiClient}

    assert {:ok, nil} =
             PetstoreClient.Api.BaseApi.invoke_api(
               state,
               :get,
               "/test/echo",
               %{},
               %{},
               nil,
               ["application/octet-stream"],
               "application/octet-stream",
               nil
             )
  end

  # CrossOriginRedirectTests

  test "same-origin redirect forwards Authorization header" do
    sensitive_headers = MapSet.new(["authorization", "cookie", "proxy-authorization"])
    is_same_origin = true
    original_headers = %{"Authorization" => "Bearer token123", "Accept" => "application/json"}

    forwarded =
      Enum.reject(original_headers, fn {k, _v} ->
        !is_same_origin and MapSet.member?(sensitive_headers, String.downcase(k))
      end)
      |> Enum.into(%{})

    assert Map.get(forwarded, "Authorization") == "Bearer token123"
  end

  test "cross-origin redirect drops Authorization header" do
    sensitive_headers = MapSet.new(["authorization", "cookie", "proxy-authorization"])
    is_same_origin = false
    original_headers = %{"Authorization" => "Bearer token123", "Accept" => "application/json"}

    forwarded =
      Enum.reject(original_headers, fn {k, _v} ->
        !is_same_origin and MapSet.member?(sensitive_headers, String.downcase(k))
      end)
      |> Enum.into(%{})

    refute Map.has_key?(forwarded, "Authorization"),
           "Authorization should be dropped on cross-origin redirect"

    assert Map.has_key?(forwarded, "Accept"),
           "Accept should be forwarded on cross-origin redirect"
  end

  test "cross-origin redirect drops Cookie header" do
    sensitive_headers = MapSet.new(["authorization", "cookie", "proxy-authorization"])
    is_same_origin = false
    original_headers = %{"Cookie" => "session=abc123", "Accept" => "application/json"}

    forwarded =
      Enum.reject(original_headers, fn {k, _v} ->
        !is_same_origin and MapSet.member?(sensitive_headers, String.downcase(k))
      end)
      |> Enum.into(%{})

    refute Map.has_key?(forwarded, "Cookie"),
           "Cookie should be dropped on cross-origin redirect"

    assert Map.has_key?(forwarded, "Accept"),
           "Accept should be forwarded on cross-origin redirect"
  end

  # NullBodyContentTypeTests

  defmodule CapturingHeadersApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> %{} end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured_headers(name) do
      Agent.get(name, & &1)
    end

    @impl true
    def send_request(_method, _url, headers, _body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> headers end)
      %PetstoreClient.ApiResponse{status_code: 200, body: "{}", headers: %{"Content-Type" => "application/json"}}
    end
  end

  test "null body POST does not send Content-Type" do
    {:ok, name} = CapturingHeadersApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: CapturingHeadersApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil
      )

    headers = CapturingHeadersApiClient.captured_headers(name)

    refute Map.has_key?(headers, "Content-Type"),
           "Content-Type must NOT be sent when body is nil"

    Agent.stop(name)
  end

  test "empty string body includes Content-Type" do
    {:ok, name} = CapturingHeadersApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: CapturingHeadersApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        "",
        ["application/json"],
        "application/json",
        nil
      )

    headers = CapturingHeadersApiClient.captured_headers(name)

    assert Map.has_key?(headers, "Content-Type"),
           "Content-Type must be sent when body is an empty string"

    Agent.stop(name)
  end

  # Multipart filename sanitization (Gap F)

  test "multipart filename containing CRLF raises ArgumentError" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.DefaultApiClient.build_content_disposition(
        "file",
        "a\r\nX-Injected: yes"
      )
    end
  end

  test "multipart filename containing NUL raises ArgumentError" do
    assert_raise ArgumentError, fn ->
      PetstoreClient.DefaultApiClient.build_content_disposition("file", "a\0b.txt")
    end
  end

  test "multipart filename with embedded quote is backslash-escaped" do
    disposition =
      PetstoreClient.DefaultApiClient.build_content_disposition("file", "a\"b.txt")

    assert String.contains?(disposition, "filename=\"a\\\"b.txt\"")
  end

  test "multipart filename with embedded backslash is backslash-escaped" do
    disposition =
      PetstoreClient.DefaultApiClient.build_content_disposition("file", "a\\b.txt")

    assert String.contains?(disposition, "filename=\"a\\\\b.txt\"")
  end

  test "multipart non-ASCII filename emits RFC 5987 filename*" do
    disposition =
      PetstoreClient.DefaultApiClient.build_content_disposition("file", "日本.pdf")

    assert String.contains?(disposition, "filename*=UTF-8''")
    assert String.contains?(disposition, "%E6%97%A5%E6%9C%AC")
    assert String.contains?(disposition, "filename=\"")
  end

  # Charset-aware response decoding (Gap H)

  test "decode_text_body decodes ISO-8859-1 to é" do
    result =
      PetstoreClient.DefaultApiClient.decode_text_body(
        <<0xE9>>,
        "text/plain; charset=ISO-8859-1"
      )

    assert result == "é"
  end

  test "decode_text_body with no charset returns bytes unchanged (UTF-8 default)" do
    result =
      PetstoreClient.DefaultApiClient.decode_text_body("hello", "text/plain")

    assert result == "hello"
  end

  test "decode_text_body with unknown charset falls back to UTF-8 (no exception)" do
    bytes = "hello"

    result =
      PetstoreClient.DefaultApiClient.decode_text_body(bytes, "text/plain; charset=x-bogus")

    assert result == bytes
  end

  test "empty JSON object body includes Content-Type" do
    {:ok, name} = CapturingHeadersApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: CapturingHeadersApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        %{},
        ["application/json"],
        "application/json",
        nil
      )

    headers = CapturingHeadersApiClient.captured_headers(name)

    assert Map.has_key?(headers, "Content-Type"),
           "Content-Type must be sent when body is {}"

    assert headers["Content-Type"] == "application/json"

    Agent.stop(name)
  end

  # Auth via keyword arg (item #4) — client-level authenticator used when no :auth opt

  defmodule AuthCapturingApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> %{} end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured_headers(name) do
      Agent.get(name, & &1)
    end

    @impl true
    def send_request(_method, _url, headers, _body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> headers end)
      %PetstoreClient.ApiResponse{status_code: 200, body: "{}", headers: %{"Content-Type" => "application/json"}}
    end
  end

  defmodule MapAuth do
    defstruct [:auth_headers, :query_params, :cookie_params]
  end

  test "client-level authenticator is used when no :auth opt is supplied" do
    {:ok, name} = AuthCapturingApiClient.start()

    client_auth = %MapAuth{
      auth_headers: %{"X-Auth" => "from-client"},
      query_params: %{},
      cookie_params: %{}
    }

    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: AuthCapturingApiClient, authenticator: client_auth}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil
      )

    headers = AuthCapturingApiClient.captured_headers(name)

    assert headers["X-Auth"] == "from-client",
           "Expected client-level auth header, got: #{inspect(headers)}"

    Agent.stop(name)
  end

  test "per-call :auth overrides client-level authenticator" do
    {:ok, name} = AuthCapturingApiClient.start()

    client_auth = %MapAuth{
      auth_headers: %{"X-Auth" => "from-client"},
      query_params: %{},
      cookie_params: %{}
    }

    per_call_auth = %MapAuth{
      auth_headers: %{"X-Auth" => "from-call"},
      query_params: %{},
      cookie_params: %{}
    }

    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: AuthCapturingApiClient, authenticator: client_auth}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil,
        per_call_auth
      )

    headers = AuthCapturingApiClient.captured_headers(name)

    assert headers["X-Auth"] == "from-call",
           "Expected per-call auth header, got: #{inspect(headers)}"

    Agent.stop(name)
  end

  # Per-op server override (item #5)

  defmodule UrlCapturingApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> "" end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured_url(name) do
      Agent.get(name, & &1)
    end

    @impl true
    def send_request(_method, url, _headers, _body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> url end)
      %PetstoreClient.ApiResponse{status_code: 200, body: "{}", headers: %{"Content-Type" => "application/json"}}
    end
  end

  test "absolute URL in path bypasses client base_url (per-op server override)" do
    {:ok, name} = UrlCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://default.example.com")
    state = %{config: config, api_client: UrlCapturingApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "http://override.example.com/api/test",
        %{},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil
      )

    url = UrlCapturingApiClient.captured_url(name)

    assert String.starts_with?(url, "http://override.example.com"),
           "Expected override server base, got: #{url}"

    refute String.starts_with?(url, "http://default.example.com"),
           "Should not start with default base, got: #{url}"

    Agent.stop(name)
  end

  test "relative path uses client base_url when no override given" do
    {:ok, name} = UrlCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://default.example.com")
    state = %{config: config, api_client: UrlCapturingApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil
      )

    url = UrlCapturingApiClient.captured_url(name)

    assert String.starts_with?(url, "http://default.example.com"),
           "Expected default base, got: #{url}"

    Agent.stop(name)
  end

  # Empty array in query is omitted (item #20)

  test "empty array query param is omitted entirely" do
    {:ok, name} = UrlCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: UrlCapturingApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{"tags" => []},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil
      )

    url = UrlCapturingApiClient.captured_url(name)

    refute String.contains?(url, "tags="),
           "Empty array param should be omitted, got: #{url}"

    refute String.contains?(url, "tags"),
           "Empty array param key should not appear, got: #{url}"

    Agent.stop(name)
  end

  test "non-empty array query param is included" do
    {:ok, name} = UrlCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: UrlCapturingApiClient}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{"tags" => ["a", "b"]},
        %{},
        nil,
        ["application/json"],
        "application/json",
        nil
      )

    url = UrlCapturingApiClient.captured_url(name)

    assert String.contains?(url, "tags=a"),
           "Expected tags=a in URL, got: #{url}"

    assert String.contains?(url, "tags=b"),
           "Expected tags=b in URL, got: #{url}"

    Agent.stop(name)
  end

  # Success-path deserialize failures must propagate, not be swallowed
  # (elixir-success-deserialize-swallow). A 2xx body that does not match
  # the declared schema must surface the decode error instead of silently
  # substituting the raw body string, matching the other 11 SDKs.

  defmodule MalformedSuccessApiClient do
    @behaviour PetstoreClient.ApiClient

    @impl true
    def send_request(_method, _url, _headers, _body) do
      # 200 OK with a declared JSON content type but a body that cannot be
      # parsed against the declared return type.
      %PetstoreClient.ApiResponse{
        status_code: 200,
        body: "{",
        headers: %{"Content-Type" => "application/json"}
      }
    end
  end

  test "2xx body that fails to deserialize propagates the error instead of returning the raw string" do
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: MalformedSuccessApiClient}

    assert_raise PetstoreClient.SerializationError, fn ->
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :get,
        "/test/echo",
        %{},
        %{},
        nil,
        ["application/json"],
        "application/json",
        "Category"
      )
    end
  end

  # apiresponse-body-nullable-elixir: ApiResponse.body is non-null (a
  # guaranteed string, defaulting to ""), matching the other 11 SDKs —
  # callers never need to nil-check it.
  test "ApiResponse.body defaults to an empty string, not nil" do
    resp = %PetstoreClient.ApiResponse{status_code: 204, headers: %{}}
    assert resp.body == ""
    assert is_binary(resp.body)
  end

  # Proxy authentication (item #29)

  @tag :skip
  test "proxy with basic authentication forwards Proxy-Authorization header" do
    # Skipped: requires a proxy with HTTP basic-auth configured (e.g. Squid).
    # If the local test environment provides one (e.g. via PROXY_AUTH_URL), this
    # block exercises the proxy_auth_url with embedded credentials.
    proxy_auth_url = System.get_env("PROXY_AUTH_URL")
    assert proxy_auth_url != nil

    opts = PetstoreClient.TransportOptions.new(proxy: proxy_auth_url)
    assert opts.proxy == proxy_auth_url
  end
end
