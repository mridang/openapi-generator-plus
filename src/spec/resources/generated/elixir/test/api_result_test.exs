defmodule PetstoreClient.ApiResultTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.ApiResult

  describe "ApiResult struct" do
    test "bhv results 003 exposes statusCode, data, rawBody and headers" do
      result = %ApiResult{
        status_code: 200,
        data: %{"id" => 1},
        raw_body: ~s({"id":1}),
        headers: %{"content-type" => "application/json"}
      }

      assert result.status_code == 200
      assert result.data == %{"id" => 1}
      assert result.raw_body == ~s({"id":1})
      assert result.headers == %{"content-type" => "application/json"}
    end

    test "bhv results 004 exposes the deserialized data" do
      result = %ApiResult{status_code: 200, data: %{"id" => 1}}

      assert result.data == %{"id" => 1}
    end

    test "bhv results 005 exposes the raw response body" do
      result = %ApiResult{status_code: 200, raw_body: ~s({"id":1})}

      assert result.raw_body == ~s({"id":1})
    end

    test "bhv results 002 exposes the response headers" do
      result = %ApiResult{status_code: 200, headers: %{"content-type" => "application/json"}}

      assert result.headers == %{"content-type" => "application/json"}
      assert Map.get(result.headers, "content-type") == "application/json"
    end

    test "bhv results 001 data and raw_body default to nil and an empty string" do
      result = %ApiResult{status_code: 204}

      assert result.status_code == 204
      assert result.data == nil
      assert result.raw_body == ""
    end

    test "bhv langelixir 057 headers defaults to an empty map" do
      result = %ApiResult{status_code: 200}

      assert result.headers == %{}
    end
  end

  describe "headers" do
    test "bhv results 006 the headers of a result are a copy taken when it is built" do
      source = %{"x-request-id" => "1"}
      result = %ApiResult{status_code: 200, headers: source}

      _changed = Map.put(source, "x-request-id", "2")

      assert result.headers == %{"x-request-id" => "1"}
    end
  end
end
