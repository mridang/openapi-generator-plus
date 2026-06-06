defmodule PetstoreClient.ApiErrorTest do
  use ExUnit.Case, async: true

  test "exposes status_code, response_body, response_headers and error_body" do
    err =
      PetstoreClient.ApiError.exception(
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
      PetstoreClient.ApiError.exception(
        status_code: 0,
        message: "connection reset",
        response_headers: nil,
        response_body: nil
      )

    assert err.response_headers == nil
    assert err.response_body == nil
  end

  test "is an exception with a message" do
    err = PetstoreClient.ApiError.exception(status_code: 500, message: "boom")

    assert %PetstoreClient.ApiError{} = err
    assert PetstoreClient.ApiError.message(err) != ""
  end

  test "typed_error_body deserializes the body into the typed model" do
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

  test "typed_error_body returns nil when there is no response body" do
    err =
      PetstoreClient.ApiError.exception(
        status_code: 500,
        response_body: "",
        response_headers: %{}
      )

    assert PetstoreClient.ApiError.typed_error_body(err, "Category") == nil
  end

  test "typed_error_body ignores extraneous fields not on the model" do
    err =
      PetstoreClient.ApiError.exception(
        status_code: 422,
        response_body: ~s({"id":1,"name":"Cat","extra":"drop-me"}),
        response_headers: %{}
      )

    body = PetstoreClient.ApiError.typed_error_body(err, "Category")
    assert %PetstoreClient.Models.Category{} = body
    assert body.id == 1
    assert body.name == "Cat"
  end
end
