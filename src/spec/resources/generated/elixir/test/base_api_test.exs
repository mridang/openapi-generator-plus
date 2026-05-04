defmodule PetstoreClient.Api.BaseApiTest do
  use ExUnit.Case, async: false

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
    wiremock_url = System.fetch_env!("WIREMOCK_HTTP_URL")
    config = PetstoreClient.Configuration.new(base_url: wiremock_url)
    api_client = PetstoreClient.DefaultApiClient.new()
    state = %{config: config, api_client: api_client}

    %{state: state, wiremock_url: wiremock_url}
  end

  # Exception dispatch

  test "raises BadRequestError for status 400", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/400", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.BadRequestError{} = error
    assert error.status_code == 400
  end

  test "raises UnauthorizedError for status 401", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/401", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.UnauthorizedError{} = error
    assert error.status_code == 401
  end

  test "raises ForbiddenError for status 403", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/403", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.ForbiddenError{} = error
    assert error.status_code == 403
  end

  test "raises NotFoundError for status 404", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/404", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.NotFoundError{} = error
    assert error.status_code == 404
  end

  test "raises ConflictError for status 409", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/409", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.ConflictError{} = error
    assert error.status_code == 409
  end

  test "raises UnprocessableEntityError for status 422", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/422", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.UnprocessableEntityError{} = error
    assert error.status_code == 422
  end

  test "raises InternalServerError for status 500", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/500", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.InternalServerError{} = error
    assert error.status_code == 500
  end

  test "raises ServerError for status 502", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/502", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.ServerError{} = error
    assert error.status_code == 502
  end

  test "raises ClientError for status 418", %{state: state} do
    assert {:error, error} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/error/418", %{}, %{}, nil, ["application/json"], "application/json", nil)

    assert %PetstoreClient.Errors.ClientError{} = error
    assert error.status_code == 418
  end

  # Success deserialization

  test "deserializes JSON response", %{state: state} do
    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/test", %{}, %{}, nil, ["application/json"], "application/json", "Object")

    assert result != nil
    assert result["message"] == "success"
  end

  test "returns raw string for non-JSON response", %{state: state} do
    assert {:ok, result} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/text", %{}, %{}, nil, ["text/plain"], "application/json", "String")

    assert result != nil
    assert String.contains?(result, "hello plain text")
  end

  test "returns nil when return_type is nil", %{state: state} do
    assert {:ok, nil} =
             PetstoreClient.Api.BaseApi.invoke_api(state, :get, "/api/test", %{}, %{}, nil, ["application/json"], "application/json", nil)
  end

  # Server variable overrides

  test "server variable overrides resolve in base URL" do
    config = PetstoreClient.Configuration.from_server(
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
end
