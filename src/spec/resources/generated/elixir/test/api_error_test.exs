defmodule PetstoreClient.ApiErrorTest do
  use ExUnit.Case, async: true

  test "bhv errors 004 exposes status_code, response_body, response_headers and error_body" do
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

  test "bhv errors 021 nil headers and body mark transport-no-response" do
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

  test "bhv errors 019 is an exception whose string form names the status" do
    err = PetstoreClient.Errors.ApiError.exception(status_code: 500, message: "boom")

    assert %PetstoreClient.Errors.ApiError{} = err
    assert PetstoreClient.Errors.ApiError.message(err) != ""
    assert Exception.message(err) =~ "500"
  end

  test "bhv errors 051 an empty error message falls back to the generic error line" do
    default = "Error message: the server returns an error"

    assert PetstoreClient.Errors.ApiError.message(
             PetstoreClient.Errors.ApiError.exception(message: "")
           ) == default

    assert PetstoreClient.Errors.ApiError.message(
             PetstoreClient.Errors.ApiError.exception(status_code: 500)
           ) == default <> "\nHTTP status code: 500"
  end

  test "bhv errors 024 the message renders the message, status, headers and body" do
    full =
      PetstoreClient.Errors.ApiError.exception(
        message: "boom",
        status_code: 502,
        response_headers: %{"x-id" => "1"},
        response_body: "upstream down"
      )

    assert PetstoreClient.Errors.ApiError.message(full) ==
             ~s(boom\nHTTP status code: 502\nResponse headers: {"x-id":"1"}\nResponse body: upstream down)

    sorted =
      PetstoreClient.Errors.ApiError.exception(
        message: "boom",
        response_headers: %{"b" => "2", "a" => "1"}
      )

    assert PetstoreClient.Errors.ApiError.message(sorted) ==
             ~s(boom\nResponse headers: {"a":"1","b":"2"})
  end

  test "bhv errors 022 the message omits an empty status, headers and body" do
    bare =
      PetstoreClient.Errors.ApiError.exception(
        message: "boom",
        status_code: 0,
        response_headers: %{},
        response_body: ""
      )

    assert PetstoreClient.Errors.ApiError.message(bare) == "boom"
  end

  test "the message cuts a long response body at 1 KiB and counts the bytes left out" do
    body = String.duplicate("a", 3_000)

    err =
      PetstoreClient.Errors.ApiError.exception(
        message: "bad gateway",
        status_code: 502,
        response_body: body
      )

    assert PetstoreClient.Errors.ApiError.message(err) ==
             "bad gateway\nHTTP status code: 502\nResponse body: " <>
               String.duplicate("a", 1_024) <> "... (1976 more bytes)"

    assert byte_size(err.response_body) == 3_000
  end

  test "the message leaves a body of exactly 1 KiB whole" do
    body = String.duplicate("a", 1_024)
    err = PetstoreClient.Errors.ApiError.exception(message: "boom", response_body: body)

    assert PetstoreClient.Errors.ApiError.message(err) == "boom\nResponse body: " <> body
  end

  test "bhv errors 001 the message escapes control characters in the response body" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        message: "boom",
        response_body: "a\r\nb\tc" <> <<1>> <> "d" <> <<127>>
      )

    assert PetstoreClient.Errors.ApiError.message(err) ==
             "boom\nResponse body: a\\r\\nb\\tc\\x01d\\x7F"

    assert err.response_body == "a\r\nb\tc" <> <<1>> <> "d" <> <<127>>
  end

  test "bhv errors 023 the message masks credential response headers and keeps the real ones on the error" do
    headers = %{
      "authorization" => "Bearer s1",
      "proxy-authorization" => "Basic s2",
      "cookie" => "sid=s3",
      "Set-Cookie" => "sid=s4",
      "www-authenticate" => "Bearer realm=s5",
      "proxy-authenticate" => "Basic realm=s6",
      "x-api-key" => "s7",
      "x-auth-token" => "s8",
      "x-client-secret" => "s9",
      "x-id" => "1"
    }

    err = PetstoreClient.Errors.ApiError.exception(message: "boom", response_headers: headers)
    rendered = PetstoreClient.Errors.ApiError.message(err)

    for secret <- ["s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9"] do
      refute rendered =~ secret
    end

    assert rendered =~ ~s("x-id":"1")
    assert rendered =~ ~s("authorization":"***")
    assert rendered =~ ~s("Set-Cookie":"***")
    assert err.response_headers == headers
  end

  test "bhv errors 011 the message names the request after the status line when both parts are set" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        message: "boom",
        status_code: 502,
        request_method: "GET",
        request_path: "/items/7",
        response_body: "upstream down"
      )

    assert PetstoreClient.Errors.ApiError.message(err) ==
             "boom\nHTTP status code: 502\nRequest: GET /items/7\nResponse body: upstream down"

    for partial <- [[request_method: "GET"], [request_path: "/items/7"], []] do
      bare =
        PetstoreClient.Errors.ApiError.exception([message: "boom", status_code: 502] ++ partial)

      assert PetstoreClient.Errors.ApiError.message(bare) == "boom\nHTTP status code: 502"
    end
  end

  test "bhv errors 007 with_request records the upper-case method and the path without query or fragment" do
    err = PetstoreClient.Errors.ApiError.from_response(404, %{}, nil)

    from_url =
      PetstoreClient.Errors.ApiError.with_request(
        err,
        :get,
        "https://api.example.com/items/7?key=hunter2#top"
      )

    assert from_url.request_method == "GET"
    assert from_url.request_path == "/items/7"
    refute Exception.message(from_url) =~ "hunter2"

    from_path = PetstoreClient.Errors.ApiError.with_request(err, "post", "/items?page=2")
    assert from_path.request_method == "POST"
    assert from_path.request_path == "/items"

    assert PetstoreClient.Errors.ApiError.with_request(err, :get, "https://api.example.com").request_path ==
             "/"

    assert PetstoreClient.Errors.ApiError.with_request(from_url, :delete, "/other") == from_url

    assert PetstoreClient.Errors.ApiError.with_request(:not_an_error, :get, "/items") ==
             :not_an_error
  end

  test "bhv errors 002 every error that specialises ApiError carries the request" do
    modules = [
      PetstoreClient.Errors.ApiError,
      PetstoreClient.Errors.ClientError,
      PetstoreClient.Errors.BadRequestError,
      PetstoreClient.Errors.UnauthorizedError,
      PetstoreClient.Errors.ForbiddenError,
      PetstoreClient.Errors.NotFoundError,
      PetstoreClient.Errors.ConflictError,
      PetstoreClient.Errors.UnprocessableEntityError,
      PetstoreClient.Errors.TooManyRequestsError,
      PetstoreClient.Errors.ServerError,
      PetstoreClient.Errors.InternalServerError,
      PetstoreClient.Errors.NetworkError,
      PetstoreClient.Errors.NetworkTimeoutError
    ]

    for module <- modules do
      err =
        PetstoreClient.Errors.ApiError.with_request(
          module.exception(%{message: "boom"}),
          :put,
          "https://h.example/a/b?k=v"
        )

      assert err.__struct__ == module
      assert err.request_method == "PUT"
      assert err.request_path == "/a/b"
      assert Exception.message(err) =~ "\nRequest: PUT /a/b"
    end
  end

  test "bhv errors 010 from_response keeps a scalar JSON body as the error body" do
    err = PetstoreClient.Errors.ApiError.from_response(500, %{}, ~s("overloaded"))

    assert err.error_body == "overloaded"
    assert err.response_body == ~s("overloaded")
  end

  test "bhv errors 005 from_response leaves the error body nil when the body is not JSON" do
    err = PetstoreClient.Errors.ApiError.from_response(500, %{}, "<html>oops</html>")

    assert err.error_body == nil
    assert err.response_body == "<html>oops</html>"
  end

  test "bhv errors 012 from_response without a request keeps the plain message" do
    err = PetstoreClient.Errors.ApiError.from_response(500, %{}, ~s({"k":"v"}))

    assert err.request_method == nil
    refute Exception.message(err) =~ "Request:"
  end

  test "bhv errors 008 from_response parses an error body that starts with a UTF-8 BOM" do
    body = <<0xEF, 0xBB, 0xBF>> <> ~s({"code":1})
    err = PetstoreClient.Errors.ApiError.from_response(404, %{}, body)

    assert err.error_body == %{"code" => 1}
    assert err.response_body == body
  end

  test "bhv errors 009 from_response refuses a deeply nested error body but keeps the raw one" do
    body = String.duplicate("[", 1_500) <> String.duplicate("]", 1_500)
    err = PetstoreClient.Errors.ApiError.from_response(500, %{}, body)

    assert %PetstoreClient.Errors.InternalServerError{} = err
    assert err.error_body == nil
    assert err.response_body == body
  end

  test "bhv errors 017 typed_error_body raises a SerializationError for a body that is not JSON" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        status_code: 500,
        response_body: "<html>oops</html>",
        response_headers: %{}
      )

    assert_raise PetstoreClient.Errors.SerializationError, fn ->
      PetstoreClient.Errors.ApiError.typed_error_body(err, "Category")
    end
  end

  test "typed_error_body deserializes the body of a specialised error" do
    err = PetstoreClient.Errors.ApiError.from_response(404, %{}, ~s({"id":42,"name":"Dogs"}))

    body = PetstoreClient.Errors.ApiError.typed_error_body(err, "Category")
    assert %PetstoreClient.Models.Category{} = body
    assert body.id == 42
  end

  describe "OAuth2ServerError message" do
    test "bhv errors 046 the message has three forms: raw body, code, and code with description" do
      raw =
        PetstoreClient.Errors.OAuth2ServerError.exception(
          status_code: 502,
          raw_body: "<html>bad gateway</html>"
        )

      code =
        PetstoreClient.Errors.OAuth2ServerError.exception(status_code: 400, code: "invalid_grant")

      described =
        PetstoreClient.Errors.OAuth2ServerError.exception(
          status_code: 400,
          code: "invalid_grant",
          description: "refresh token expired"
        )

      assert Exception.message(raw) ==
               "Token request failed with status 502: <html>bad gateway</html>"

      assert Exception.message(code) == "Token request failed with status 400: invalid_grant"

      assert Exception.message(described) ==
               "Token request failed with status 400: invalid_grant -- refresh token expired"
    end

    test "names the raw body when the response is not an OAuth2 error object" do
      err =
        PetstoreClient.Errors.OAuth2ServerError.exception(
          status_code: 502,
          raw_body: "Bad Gateway"
        )

      assert Exception.message(err) == "Token request failed with status 502: Bad Gateway"
    end

    test "names the error code" do
      err =
        PetstoreClient.Errors.OAuth2ServerError.exception(status_code: 400, code: "invalid_grant")

      assert Exception.message(err) == "Token request failed with status 400: invalid_grant"
    end

    test "names the error code and description" do
      err =
        PetstoreClient.Errors.OAuth2ServerError.exception(
          status_code: 400,
          code: "invalid_grant",
          description: "refresh token expired"
        )

      assert Exception.message(err) ==
               "Token request failed with status 400: invalid_grant -- refresh token expired"
    end
  end

  test "bhv errors 013 typed_error_body deserializes the body into the typed model" do
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

  test "bhv errors 016 typed_error_body returns nil for an empty response body" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        status_code: 500,
        response_body: "",
        response_headers: %{}
      )

    assert PetstoreClient.Errors.ApiError.typed_error_body(err, "Category") == nil
  end

  test "bhv errors 015 typed_error_body returns nil when there is no response body" do
    err =
      PetstoreClient.Errors.ApiError.exception(
        status_code: 500,
        response_body: nil,
        response_headers: %{}
      )

    assert PetstoreClient.Errors.ApiError.typed_error_body(err, "Category") == nil
  end

  test "bhv errors 014 typed_error_body ignores extraneous fields not on the model" do
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
    test "bhv errors 025 ApiError, a typed error and the Serialization error are all recognised" do
      api_error = PetstoreClient.Errors.ApiError.exception(status_code: 500, message: "boom")
      typed_error = PetstoreClient.Errors.BadRequestError.exception(%{message: "nope"})
      serialization_error = %PetstoreClient.Errors.SerializationError{message: "bad json"}

      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(api_error)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(typed_error)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(serialization_error)
    end

    test "bhv errors 045 the OAuth2 errors are not ApiError and the SDK error root recognises them" do
      token = %PetstoreClient.Errors.OAuth2TokenError{message: "missing access_token"}

      server = %PetstoreClient.Errors.OAuth2ServerError{
        status_code: 400,
        message: "invalid_grant"
      }

      refute PetstoreClient.Errors.ApiError.api_error?(token)
      refute PetstoreClient.Errors.ApiError.api_error?(server)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(token)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(server)
    end

    test "bhv errors 047 a typed server error is an ApiError and an SDK error" do
      err = PetstoreClient.Errors.ApiError.from_response(500, %{}, "boom")

      assert %PetstoreClient.Errors.InternalServerError{} = err
      assert PetstoreClient.Errors.ApiError.api_error?(err)
      assert PetstoreClient.Errors.OpenAPIError.open_api_error?(err)
    end

    test "bhv errors 018 every SDK exception module is listed in exceptions/0" do
      modules = PetstoreClient.Errors.OpenAPIError.exceptions()

      assert PetstoreClient.Errors.ApiError in modules
      assert PetstoreClient.Errors.BadRequestError in modules
      assert PetstoreClient.Errors.SerializationError in modules
    end

    test "bhv errors 044 every error lives under the Errors namespace and the package root lists the whole tree" do
      errors = PetstoreClient.errors()

      assert PetstoreClient.Errors.OpenAPIError in errors

      for error <- errors do
        assert Code.ensure_loaded?(error)
        assert ["PetstoreClient", "Errors", _name] = Module.split(error)
      end
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

    test "bhv errors 003 every status-specific error accepts an optional cause" do
      cause = %RuntimeError{message: "root cause"}

      for module <- [
            PetstoreClient.Errors.BadRequestError,
            PetstoreClient.Errors.UnauthorizedError,
            PetstoreClient.Errors.ForbiddenError,
            PetstoreClient.Errors.NotFoundError,
            PetstoreClient.Errors.ConflictError,
            PetstoreClient.Errors.UnprocessableEntityError,
            PetstoreClient.Errors.TooManyRequestsError,
            PetstoreClient.Errors.InternalServerError,
            PetstoreClient.Errors.ClientError,
            PetstoreClient.Errors.ServerError
          ] do
        assert module.exception(%{message: "boom", cause: cause}).cause == cause
        assert module.exception(%{message: "boom"}).cause == nil
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

    test "bhv errors 033 NotFoundError is a ClientError and an ApiError" do
      err = PetstoreClient.Errors.ApiError.from_response(404, %{}, nil)

      assert %PetstoreClient.Errors.NotFoundError{} = err
      assert PetstoreClient.Errors.ClientError.client_error?(err)
      assert PetstoreClient.Errors.ApiError.api_error?(err)
    end

    test "a 429 is a TooManyRequestsError and a ClientError" do
      err = PetstoreClient.Errors.ApiError.from_response(429, %{"retry-after" => "3"}, nil)

      assert %PetstoreClient.Errors.TooManyRequestsError{status_code: 429} = err
      assert err.message == "API returned status code 429"
      assert PetstoreClient.Errors.TooManyRequestsError.exception("slow down").status_code == 429
      assert PetstoreClient.Errors.ClientError.client_error?(err)
      assert PetstoreClient.Errors.ApiError.api_error?(err)
    end

    # The public status factory every generated operation and the OpenID
    # Connect discovery request use: the most specific error for each status,
    # with the status, headers and body kept and a JSON body parsed.
    test "bhv errors 006 from_response maps every status to its error" do
      headers = %{"x-request-id" => "abc"}

      for {status, module} <- [
            {400, PetstoreClient.Errors.BadRequestError},
            {401, PetstoreClient.Errors.UnauthorizedError},
            {403, PetstoreClient.Errors.ForbiddenError},
            {404, PetstoreClient.Errors.NotFoundError},
            {409, PetstoreClient.Errors.ConflictError},
            {422, PetstoreClient.Errors.UnprocessableEntityError},
            {429, PetstoreClient.Errors.TooManyRequestsError},
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

    test "bhv errors 050 response headers render as compact JSON with sorted keys and unescaped slashes" do
      error =
        PetstoreClient.Errors.ApiError.exception(
          message: "boom",
          response_headers: %{"x-zeta" => "2", "x-alpha" => "https://example.com/a/b"}
        )

      assert PetstoreClient.Errors.ApiError.message(error) =~
               ~s(Response headers: {"x-alpha":"https://example.com/a/b","x-zeta":"2"})
    end

    test "bhv errors 053 each named error pins its own HTTP status code" do
      assert PetstoreClient.Errors.NotFoundError.exception(%{status_code: 500}).status_code == 404

      assert PetstoreClient.Errors.TooManyRequestsError.exception(%{status_code: 500}).status_code ==
               429

      assert PetstoreClient.Errors.InternalServerError.exception(%{status_code: 404}).status_code ==
               500
    end

    test "bhv errors 055 status error constructors default the response context to null" do
      error = PetstoreClient.Errors.NotFoundError.exception("gone")

      assert error.response_headers == nil
      assert error.response_body == nil
      assert error.error_body == nil
    end

    test "bhv errors 056 a status at or above 600 yields the base error and not a server error" do
      for status <- [600, 700] do
        error = PetstoreClient.Errors.ApiError.from_response(status, nil, nil)

        assert error.__struct__ == PetstoreClient.Errors.ApiError
        refute PetstoreClient.Errors.ServerError.server_error?(error)
      end
    end

    test "bhv errors 020 network errors carry status 0" do
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
