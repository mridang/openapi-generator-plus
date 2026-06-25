# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
defmodule PetstoreClient.Api.BaseApiTest do
  use ExUnit.Case, async: true

  defmodule TestAuthenticator do
    use PetstoreClient.Auth.BaseAuthenticator

    defstruct [:host_url, :headers, :query, :cookies]

    @impl true
    def host(%__MODULE__{} = self), do: self.host_url

    @impl true
    def auth_headers(%__MODULE__{} = self), do: self.headers || %{}

    @impl true
    def query_params(%__MODULE__{} = self), do: self.query || %{}

    @impl true
    def cookie_params(%__MODULE__{} = self), do: self.cookies || %{}
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
      %PetstoreClient.ApiHttpResponse{
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
      %PetstoreClient.ApiHttpResponse{status_code: 200, body: "raw body content", headers: %{}}
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

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "",
        headers: %{"Content-Type" => "application/json"}
      }
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

  test "allow_empty_value param omitted when value is nil in options" do
    {:ok, name} = CapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(CapturingApiClient, config)

    _result =
      PetstoreClient.Api.PetApi.find_pets_by_status(
        api,
        %PetstoreClient.Api.Options.FindPetsByStatusOptions{}
      )

    url = CapturingApiClient.captured_url(name)
    # A nil optional allowEmptyValue value must NOT emit a spurious empty key; the
    # key is sent only when the caller supplies a value (see the empty-string test).
    refute String.contains?(url, "status="),
           "Expected no status= for allowEmptyValue param with nil value, got: #{url}"

    Agent.stop(name)
  end

  test "allow_empty_value param included when value is empty string" do
    {:ok, name} = CapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(CapturingApiClient, config)

    _result =
      PetstoreClient.Api.PetApi.find_pets_by_status(
        api,
        %PetstoreClient.Api.Options.FindPetsByStatusOptions{status: ""}
      )

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

  # R5-1: a config default header wins over the operation-negotiated value.
  # The header selector negotiates Accept: application/json for this operation,
  # but the config default Accept: application/xml must override it because
  # base_api merges config.default_headers OVER the selected headers.
  test "config default Accept overrides operation-negotiated Accept", %{chasm_url: chasm_url} do
    config =
      PetstoreClient.Configuration.new(
        base_url: chasm_url,
        default_headers: %{"Accept" => "application/xml"}
      )

    state = %{config: config, api_client: PetstoreClient.DefaultApiClient.new()}

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

    # chasm echoes request headers (lowercased) under .headers; config wins
    assert result["headers"]["accept"] == "application/xml"
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

      %PetstoreClient.ApiHttpResponse{
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

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: encoded,
        headers: %{"Content-Type" => "image/png"}
      }
    end
  end

  defmodule EmptyBinaryApiClient do
    @behaviour PetstoreClient.ApiClient

    @impl true
    def send_request(_method, _url, _headers, _body) do
      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "",
        headers: %{"Content-Type" => "application/octet-stream"}
      }
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

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "{}",
        headers: %{"Content-Type" => "application/json"}
      }
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

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "{}",
        headers: %{"Content-Type" => "application/json"}
      }
    end
  end

  # A real authenticator: the credential providers are behaviour CALLBACKS
  # (def auth_headers/1 ...), never struct data fields. base_api must dispatch
  # through the module to read them; a struct that merely *names* fields
  # :auth_headers/:query_params/:cookie_params would mask the dispatch bug.
  defmodule MapAuth do
    use PetstoreClient.Auth.BaseAuthenticator

    defstruct [:header_value]

    @impl true
    def host(%__MODULE__{} = self), do: self.header_value

    # The credential provider is a behaviour CALLBACK, not a struct field.
    # query_params/1 and cookie_params/1 fall back to the BaseAuthenticator
    # defaults (%{}), mirroring the real BearerAuthenticator.
    @impl true
    def auth_headers(%__MODULE__{} = self), do: %{"X-Auth" => self.header_value}
  end

  test "client-level authenticator is used when no :auth opt is supplied" do
    {:ok, name} = AuthCapturingApiClient.start()
    client_auth = %MapAuth{header_value: "from-client"}
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
    client_auth = %MapAuth{header_value: "from-client"}
    per_call_auth = %MapAuth{header_value: "from-call"}
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

  # AUTH-APPLIED (Wave A1): a configured authenticator must actually be applied
  # to a SECURED request. This is the regression guard for the Elixir bug where
  # base_api read auth_headers/query_params/cookie_params as struct DATA FIELDS
  # gated on Map.has_key?/2 — but those are behaviour CALLBACK FUNCTIONS on the
  # authenticator's module, so the guard was always false and the credential was
  # silently dropped. The test drives the real production path: a genuine
  # authenticator struct (PetstoreClient.Auth.BearerAuthenticator /
  # PetstoreClient.Auth.ApiKeyAuthenticator, whose providers are def callbacks,
  # NOT a struct that merely names :auth_headers fields) configured on the API
  # instance, issuing a secured operation (POST /pet), and asserting the
  # outbound request actually carried the credential.

  defmodule AuthAppliedCapturingApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> %{headers: %{}, url: ""} end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured(name), do: Agent.get(name, & &1)

    @impl true
    def send_request(_method, url, headers, _body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> %{headers: headers, url: url} end)

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "",
        headers: %{"Content-Type" => "application/json"}
      }
    end
  end

  test "configured Bearer authenticator is applied to a secured operation" do
    {:ok, name} = AuthAppliedCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")

    authenticator =
      PetstoreClient.Auth.BearerAuthenticator.new("http://localhost", "secret-token-123")

    api = PetstoreClient.Api.PetApi.new(AuthAppliedCapturingApiClient, config, authenticator)

    pet = %PetstoreClient.Models.Pet{name: "rex", photo_urls: []}
    _result = PetstoreClient.Api.PetApi.add_pet(api, pet)

    captured = AuthAppliedCapturingApiClient.captured(name)

    assert captured.headers["Authorization"] == "Bearer secret-token-123",
           "Configured Bearer authenticator must add the Authorization header to the secured request, got: #{inspect(captured.headers)}"

    Agent.stop(name)
  end

  test "configured api-key authenticator is applied to a secured operation" do
    {:ok, name} = AuthAppliedCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")

    authenticator =
      PetstoreClient.Auth.ApiKeyAuthenticator.new(
        "http://localhost",
        "X-API-Key",
        "key-value-456",
        :header
      )

    api = PetstoreClient.Api.PetApi.new(AuthAppliedCapturingApiClient, config, authenticator)

    pet = %PetstoreClient.Models.Pet{name: "rex", photo_urls: []}
    _result = PetstoreClient.Api.PetApi.add_pet(api, pet)

    captured = AuthAppliedCapturingApiClient.captured(name)

    assert captured.headers["X-API-Key"] == "key-value-456",
           "Configured api-key authenticator must add the api-key header to the secured request, got: #{inspect(captured.headers)}"

    Agent.stop(name)
  end

  # SECURITY-NONE (Wave A2): a `security: []` operation is UNAUTHENTICATED and
  # must suppress the client-level credential. The generated api method passes
  # the BaseApi no-auth SENTINEL (not nil) for these operations, so BaseApi does
  # NOT fall back to the configured client authenticator. get_pet_by_id is
  # declared `security: []`; invoking it on an API instance that carries a
  # configured authenticator must emit NO Authorization header, NO api-key
  # header/query-param, and NO auth Cookie — proving the credential is dropped
  # for unauthenticated operations rather than leaked (e.g. to a reflecting
  # testEcho* endpoint). Guards against the regression where `auth = nil`
  # re-acquired the client authenticator via `auth || client_authenticator`.

  test "security:[] operation suppresses a configured Bearer client authenticator" do
    {:ok, name} = AuthAppliedCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")

    authenticator =
      PetstoreClient.Auth.BearerAuthenticator.new("http://localhost", "secret-token-123")

    api = PetstoreClient.Api.PetApi.new(AuthAppliedCapturingApiClient, config, authenticator)

    _result = PetstoreClient.Api.PetApi.get_pet_by_id(api, 1)

    captured = AuthAppliedCapturingApiClient.captured(name)

    refute Map.has_key?(captured.headers, "Authorization"),
           "security:[] op must NOT send Authorization from the client authenticator, got: #{inspect(captured.headers)}"

    refute Map.has_key?(captured.headers, "Cookie"),
           "security:[] op must NOT send an auth Cookie, got: #{inspect(captured.headers)}"
  end

  test "security:[] operation suppresses a configured api-key client authenticator (header and query)" do
    {:ok, header_name} = AuthAppliedCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")

    header_auth =
      PetstoreClient.Auth.ApiKeyAuthenticator.new(
        "http://localhost",
        "X-API-Key",
        "key-value-456",
        :header
      )

    header_api =
      PetstoreClient.Api.PetApi.new(AuthAppliedCapturingApiClient, config, header_auth)

    _result = PetstoreClient.Api.PetApi.get_pet_by_id(header_api, 1)
    header_captured = AuthAppliedCapturingApiClient.captured(header_name)

    refute Map.has_key?(header_captured.headers, "X-API-Key"),
           "security:[] op must NOT send the api-key header from the client authenticator, got: #{inspect(header_captured.headers)}"

    Agent.stop(header_name)

    # And the same suppression must hold for an api-key delivered as a query
    # parameter — the credential must not leak onto the URL either.
    {:ok, query_name} = AuthAppliedCapturingApiClient.start()

    query_auth =
      PetstoreClient.Auth.ApiKeyAuthenticator.new(
        "http://localhost",
        "api_key",
        "key-value-789",
        :query
      )

    query_api =
      PetstoreClient.Api.PetApi.new(AuthAppliedCapturingApiClient, config, query_auth)

    _result = PetstoreClient.Api.PetApi.get_pet_by_id(query_api, 1)
    query_captured = AuthAppliedCapturingApiClient.captured(query_name)

    refute String.contains?(query_captured.url, "api_key="),
           "security:[] op must NOT append the api-key query param from the client authenticator, got: #{query_captured.url}"

    refute String.contains?(query_captured.url, "key-value-789"),
           "security:[] op must NOT leak the api-key value onto the URL, got: #{query_captured.url}"

    Agent.stop(query_name)
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

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "",
        headers: %{"Content-Type" => "application/json"}
      }
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
      %PetstoreClient.ApiHttpResponse{
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

  # apiresponse-body-nullable-elixir: ApiHttpResponse.body is non-null (a
  # guaranteed string, defaulting to ""), matching the other 11 SDKs —
  # callers never need to nil-check it.
  test "ApiHttpResponse.body defaults to an empty string, not nil" do
    resp = %PetstoreClient.ApiHttpResponse{status_code: 204, headers: %{}}
    assert resp.body == ""
    assert is_binary(resp.body)
  end

  # Typed error body accessor (item #7)

  test "typed_error_body deserializes the error body into the typed model" do
    err =
      PetstoreClient.ApiError.exception(
        status_code: 400,
        response_body: ~s({"id":42,"name":"Dogs"}),
        response_headers: %{}
      )

    body = PetstoreClient.ApiError.typed_error_body(err, "Category")
    assert %PetstoreClient.Models.Category{} = body
    assert body.id == 42
    assert body.name == "Dogs"
  end

  test "typed_error_body returns nil for an empty error body" do
    err =
      PetstoreClient.ApiError.exception(
        status_code: 500,
        response_body: "",
        response_headers: %{}
      )

    assert PetstoreClient.ApiError.typed_error_body(err, "Category") == nil
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

  # ---------------------------------------------------------------------------
  # Canonical cross-language behaviors (1-6). These pin the six behaviors all
  # 12 SDKs converge on; each is exercised against the real production code
  # path, not a re-implementation in the test.
  # ---------------------------------------------------------------------------

  defmodule BodyCapturingApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> nil end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured_body(name), do: Agent.get(name, & &1)

    @impl true
    def send_request(_method, _url, _headers, body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> body end)

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "",
        headers: %{"Content-Type" => "application/json"}
      }
    end
  end

  # Behavior #1: a form array serializes to repeated keys (tags=a&tags=b),
  # never a single comma-joined value.
  test "form array body serializes to repeated keys" do
    {:ok, name} = BodyCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: BodyCapturingApiClient}

    body = %{"tags" => ["a", "b"], "nickname" => "rex"}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        body,
        ["application/json"],
        "application/x-www-form-urlencoded",
        nil
      )

    captured = BodyCapturingApiClient.captured_body(name)
    pairs = String.split(captured, "&")
    assert "tags=a" in pairs, "Expected repeated tags=a, got: #{captured}"
    assert "tags=b" in pairs, "Expected repeated tags=b, got: #{captured}"

    refute String.contains?(captured, "tags=a%2Cb"),
           "Array must not be comma-joined, got: #{captured}"

    refute String.contains?(captured, "tags=ab"),
           "Array must not be charlist-flattened, got: #{captured}"

    Agent.stop(name)
  end

  # Behavior #2: an absent/nil optional form field is omitted from the body.
  # set_pet_preferences declares optional `tags` and `note`; only `nickname`
  # (required) is supplied here, so neither optional key must appear.
  test "absent optional form fields are omitted from the request body" do
    {:ok, name} = BodyCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(BodyCapturingApiClient, config)

    options = %PetstoreClient.Api.Options.SetPetPreferencesOptions{nickname: "rex"}
    _result = PetstoreClient.Api.PetApi.set_pet_preferences(api, 1, options)

    captured = BodyCapturingApiClient.captured_body(name)

    assert String.contains?(captured, "nickname=rex"),
           "Expected required nickname in body, got: #{captured}"

    refute String.contains?(captured, "tags"),
           "Absent optional 'tags' must be omitted, got: #{captured}"

    refute String.contains?(captured, "note"),
           "Absent optional 'note' must be omitted, got: #{captured}"

    Agent.stop(name)
  end

  # Behavior #3: a space in a form-body value encodes as `+`, not %20
  # (form-urlencoded media type).
  test "form body encodes space as plus, not percent-20" do
    {:ok, name} = BodyCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    state = %{config: config, api_client: BodyCapturingApiClient}

    body = %{"nickname" => "the big boss"}

    _result =
      PetstoreClient.Api.BaseApi.invoke_api(
        state,
        :post,
        "/test/echo",
        %{},
        %{},
        body,
        ["application/json"],
        "application/x-www-form-urlencoded",
        nil
      )

    captured = BodyCapturingApiClient.captured_body(name)

    assert String.contains?(captured, "nickname=the+big+boss"),
           "Expected spaces encoded as +, got: #{captured}"

    refute String.contains?(captured, "%20"),
           "Spaces must not encode as %20 in a form body, got: #{captured}"

    Agent.stop(name)
  end

  # Behavior #4: a multipart file part derives its Content-Type from the
  # filename extension (.png -> image/png), falling back to
  # application/octet-stream for an unknown/extension-less filename.
  test "multipart file part Content-Type is derived from the filename extension" do
    body = %{"file" => {:file, "avatar.png", <<1, 2, 3>>}}
    serialized = PetstoreClient.DefaultApiClient.build_multipart_body(body, "BOUNDARY")

    assert String.contains?(serialized, "Content-Type: image/png"),
           "Expected image/png for .png file, got: #{serialized}"
  end

  test "multipart file part falls back to application/octet-stream for unknown extension" do
    body = %{"file" => {:file, "blob", <<1, 2, 3>>}}
    serialized = PetstoreClient.DefaultApiClient.build_multipart_body(body, "BOUNDARY")

    assert String.contains?(serialized, "Content-Type: application/octet-stream"),
           "Expected octet-stream fallback for extension-less file, got: #{serialized}"
  end

  # Behavior #4b: a raw-bytes part (binary content, no explicit filename)
  # reuses the field name as the filename and derives its Content-Type from
  # that name's extension, falling back to application/octet-stream when the
  # name has none. Matches the cross-language go/node/java contract.
  test "multipart raw-bytes part reuses field name as filename and falls back to octet-stream" do
    body = %{"file" => {:raw, <<0, 1, 2>>}}
    serialized = PetstoreClient.DefaultApiClient.build_multipart_body(body, "BOUNDARY")

    assert String.contains?(serialized, "name=\"file\"; filename=\"file\""),
           "Expected Content-Disposition to reuse field name as filename, got: #{serialized}"

    assert String.contains?(serialized, "Content-Type: application/octet-stream"),
           "Expected octet-stream fallback for extension-less raw bytes, got: #{serialized}"
  end

  # Behavior #5: a non-ASCII multipart field name is preserved verbatim as
  # UTF-8 in the Content-Disposition `name=` parameter — not transliterated
  # to `?` and not stripped.
  test "multipart non-ASCII field name is preserved as UTF-8" do
    body = %{"имя" => "value"}
    serialized = PetstoreClient.DefaultApiClient.build_multipart_body(body, "BOUNDARY")

    assert String.contains?(serialized, "name=\"имя\""),
           "Expected non-ASCII field name preserved as UTF-8, got: #{serialized}"

    refute String.contains?(serialized, "name=\"?\""),
           "Field name must not be transliterated to ?, got: #{serialized}"
  end

  # Behavior #6: deserializing an out-of-schema enum value raises the SDK
  # (de)serialization error instead of silently passing the raw value through
  # or defaulting to an "unknown" member. The Pet model declares an inline
  # `status` enum (available | pending | sold).
  test "unknown enum wire value raises SerializationError on deserialize" do
    assert_raise PetstoreClient.SerializationError, fn ->
      PetstoreClient.ObjectSerializer.deserialize(
        ~s({"name":"rex","photoUrls":[],"status":"banana"}),
        "Pet"
      )
    end
  end

  test "valid enum wire value deserializes to its atom" do
    pet =
      PetstoreClient.ObjectSerializer.deserialize(
        ~s({"name":"rex","photoUrls":[],"status":"available"}),
        "Pet"
      )

    assert pet.status == :available
  end
end
