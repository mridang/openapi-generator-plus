# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
defmodule PetstoreClient.ApiErrorTest do
  use ExUnit.Case, async: true

  test "exposes status_code, response_body, response_headers and error_body" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        status_code: 404,
        message: "not found",
        response_headers: %{"content-type" => "application/json"},
        response_body: ~s({"id":7,"name":"missing"}),
        error_body: nil
      )

    assert err.status_code == 404
    assert err.response_body == ~s({"id":7,"name":"missing"})
    assert err.response_headers["content-type"] == "application/json"
    assert err.error_body == nil
  end

  test "nil headers and body mark transport-no-response" do
    # apierror-responsebody-headers-nullable-split: nil is distinct from an
    # empty header map / empty body so a pre-response transport failure can
    # be encoded.
    err =
      PetstoreClient.Errors.ApiError.exception(
        status_code: 0,
        message: "connection reset",
        response_headers: nil,
        response_body: nil
      )

    assert err.response_headers == nil
    assert err.response_body == nil
  end

  test "is an exception with a message" do
    err = PetstoreClient.Errors.ApiError.exception(status_code: 500, message: "boom")

    assert %PetstoreClient.Errors.ApiError{} = err
    assert PetstoreClient.Errors.ApiError.message(err) != ""
  end

  test "typed_error_body deserializes the body into the typed model" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        status_code: 400,
        response_body: ~s({"id":42,"name":"Dogs"}),
        response_headers: %{}
      )

    body = PetstoreClient.Errors.ApiError.typed_error_body(err, "Category")
    assert %PetstoreClient.Models.Category{} = body
    assert body.id == 42
    assert body.name == "Dogs"
  end

  test "typed_error_body returns nil when there is no response body" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        status_code: 500,
        response_body: "",
        response_headers: %{}
      )

    assert PetstoreClient.Errors.ApiError.typed_error_body(err, "Category") == nil
  end

  test "typed_error_body ignores extraneous fields not on the model" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        status_code: 422,
        response_body: ~s({"id":1,"name":"Cat","extra":"drop-me"}),
        response_headers: %{}
      )

    body = PetstoreClient.Errors.ApiError.typed_error_body(err, "Category")
    assert %PetstoreClient.Models.Category{} = body
    assert body.id == 1
    assert body.name == "Cat"
  end

  describe "SDK error grouping" do
    test "ApiError, a typed error and the Serialization error are all recognised" do
      api_error = PetstoreClient.Errors.ApiError.exception(status_code: 500, message: "boom")
      typed_error = PetstoreClient.Errors.BadRequestError.exception(%{message: "nope"})
      serialization_error = %PetstoreClient.Errors.SerializationError{message: "bad json"}

      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(api_error)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(typed_error)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(serialization_error)
    end

    test "every SDK exception module is listed in exceptions/0" do
      modules = PetstoreClient.Errors.OpenAPIError.exceptions()

      assert PetstoreClient.Errors.ApiError in modules
      assert PetstoreClient.Errors.BadRequestError in modules
      assert PetstoreClient.Errors.SerializationError in modules
    end

    test "a rescued SDK error is recognised via exceptions/0" do
      # Elixir's `rescue e in [...]` clause needs a compile-time literal list
      # of exception modules, so a runtime list cannot be spliced in. The
      # idiomatic equivalent is to rescue the error and assert membership in
      # the canonical `exceptions/0` list, which is the single source of truth.
      caught =
        try do
          raise PetstoreClient.Errors.BadRequestError, %{message: "nope"}
        rescue
          e -> e
        end

      assert caught.__struct__ in PetstoreClient.Errors.OpenAPIError.exceptions()
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(caught)
    end

    test "network and OAuth errors are recognised" do
      for err <- [
            PetstoreClient.Errors.NetworkError.exception(message: "refused"),
            PetstoreClient.Errors.NetworkTimeoutError.exception(message: "timed out"),
            %PetstoreClient.Errors.OAuth2TokenError{message: "no access_token"},
            %PetstoreClient.Errors.OAuth2ServerError{status_code: 400, message: "invalid_grant"}
          ] do
        assert PetstoreClient.Errors.OpenAPIError.open_api_error?(err)
      end
    end

    # Elixir exceptions have no inheritance, so the family predicates express
    # the hierarchy: a 404 is a NotFoundError, a ClientError and an ApiError; a
    # 500 is an InternalServerError, a ServerError and an ApiError; a
    # NetworkTimeoutError is a NetworkError and an ApiError.
    test "family predicates express the error hierarchy" do
      not_found =
        PetstoreClient.Errors.NotFoundError.exception(%{message: "missing", status_code: 404})

      assert PetstoreClient.Errors.ClientError.client_error?(not_found)
      refute PetstoreClient.Errors.ServerError.server_error?(not_found)
      refute PetstoreClient.Errors.NetworkError.network_error?(not_found)
      assert PetstoreClient.Errors.ApiError.api_error?(not_found)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(not_found)

      internal =
        PetstoreClient.Errors.InternalServerError.exception(%{message: "boom", status_code: 500})

      assert PetstoreClient.Errors.ServerError.server_error?(internal)
      refute PetstoreClient.Errors.ClientError.client_error?(internal)
      assert PetstoreClient.Errors.ApiError.api_error?(internal)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(internal)

      timeout = PetstoreClient.Errors.NetworkTimeoutError.exception(message: "timed out")
      assert PetstoreClient.Errors.NetworkError.network_error?(timeout)
      assert PetstoreClient.Errors.ApiError.api_error?(timeout)

      refute PetstoreClient.Errors.NetworkError.network_error?(
               PetstoreClient.Errors.ApiError.exception(message: "x")
             )

      serialization = %PetstoreClient.Errors.SerializationError{message: "bad"}
      refute PetstoreClient.Errors.ApiError.api_error?(serialization)
      refute PetstoreClient.Errors.ApiError.api_error?(%ArgumentError{message: "unrelated"})
    end

    # The public status factory every generated operation and the OpenID
    # Connect discovery request use: the most specific error for each status,
    # with the status, headers and body kept and a JSON body parsed.
    test "from_response maps every status to its error" do
      headers = %{"x-request-id" => "abc"}

      for {status, module} <- [
            {400, PetstoreClient.Errors.BadRequestError},
            {401, PetstoreClient.Errors.UnauthorizedError},
            {403, PetstoreClient.Errors.ForbiddenError},
            {404, PetstoreClient.Errors.NotFoundError},
            {409, PetstoreClient.Errors.ConflictError},
            {422, PetstoreClient.Errors.UnprocessableEntityError},
            {418, PetstoreClient.Errors.ClientError},
            {500, PetstoreClient.Errors.InternalServerError},
            {503, PetstoreClient.Errors.ServerError},
            {302, PetstoreClient.Errors.ApiError},
            # A status outside 400-599 is neither a ClientError nor a
            # ServerError: the 5xx arm stops at 599, so 600 is an ApiError.
            {600, PetstoreClient.Errors.ApiError}
          ] do
        err = PetstoreClient.Errors.ApiError.from_response(status, headers, ~s({"k":"v"}))

        assert err.__struct__ == module
        assert err.status_code == status
        assert err.response_headers == headers
        assert err.response_body == ~s({"k":"v"})
        assert err.error_body == %{"k" => "v"}
        assert PetstoreClient.Errors.ApiError.api_error?(err)
        assert PetstoreClient.Errors.ClientError.client_error?(err) == status in 400..499
        assert PetstoreClient.Errors.ServerError.server_error?(err) == status in 500..599
      end
    end

    test "network errors carry status 0" do
      assert PetstoreClient.Errors.NetworkError.exception(message: "refused").status_code == 0

      assert PetstoreClient.Errors.NetworkTimeoutError.exception(message: "timed out").status_code ==
               0
    end

    test "non-SDK exceptions are not recognised" do
      refute PetstoreClient.Errors.OpenAPIError.open_api_error?(%RuntimeError{
               message: "unrelated"
             })

      refute PetstoreClient.Errors.OpenAPIError.open_api_error?(%ArgumentError{
               message: "unrelated"
             })

      refute PetstoreClient.Errors.OpenAPIError.open_api_error?(:not_an_exception)
      refute PetstoreClient.Errors.OpenAPIError.open_api_error?(%{message: "plain map"})
    end
  end
end
