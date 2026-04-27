defmodule PetstoreClient.HeaderSelectorTest do
  use ExUnit.Case, async: true

  describe "json_mime?/1" do
    test "returns true for application/json" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/json") == true
    end

    test "returns true for application/json with charset" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/json; charset=UTF-8") == true
    end

    test "returns true for uppercase APPLICATION/JSON (case insensitive)" do
      assert PetstoreClient.HeaderSelector.json_mime?("APPLICATION/JSON") == true
    end

    test "returns true for vendor JSON types" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/vnd.api+json") == true
      assert PetstoreClient.HeaderSelector.json_mime?("application/vnd.company+json") == true
      assert PetstoreClient.HeaderSelector.json_mime?("application/hal+json") == true
    end

    test "returns false for text/html" do
      assert PetstoreClient.HeaderSelector.json_mime?("text/html") == false
    end

    test "returns false for application/xml" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/xml") == false
    end

    test "returns false for nil" do
      assert PetstoreClient.HeaderSelector.json_mime?(nil) == false
    end

    test "returns false for empty string" do
      assert PetstoreClient.HeaderSelector.json_mime?("") == false
    end
  end

  describe "select_headers/3" do
    test "sets Accept header when accepts provided" do
      headers = PetstoreClient.HeaderSelector.select_headers(["application/json"], "application/json", false)
      assert headers["Accept"] == "application/json"
    end

    test "does not set Accept header when accepts empty" do
      headers = PetstoreClient.HeaderSelector.select_headers([], "application/json", false)
      assert headers["Accept"] == nil
    end

    test "sets Content-Type header when not multipart" do
      headers = PetstoreClient.HeaderSelector.select_headers(["application/json"], "application/json", false)
      assert headers["Content-Type"] == "application/json"
    end

    test "does not set Content-Type header when multipart" do
      headers = PetstoreClient.HeaderSelector.select_headers(["application/json"], "application/json", true)
      assert headers["Content-Type"] == nil
    end

    test "defaults Content-Type to application/json when empty" do
      headers = PetstoreClient.HeaderSelector.select_headers(["application/json"], "", false)
      assert headers["Content-Type"] == "application/json"
    end

    test "defaults Content-Type to application/json when nil" do
      headers = PetstoreClient.HeaderSelector.select_headers(["application/json"], nil, false)
      assert headers["Content-Type"] == "application/json"
    end

    test "returns single accept as-is" do
      headers = PetstoreClient.HeaderSelector.select_headers(["application/json"], "application/json", false)
      assert headers["Accept"] == "application/json"
    end

    test "returns comma-separated list when no JSON types present" do
      headers = PetstoreClient.HeaderSelector.select_headers(["text/html", "text/plain"], "application/json", false)
      assert headers["Accept"] == "text/html,text/plain"
    end

    test "prioritizes application/json with quality weight" do
      headers = PetstoreClient.HeaderSelector.select_headers(["text/html", "application/json"], "application/json", false)
      accept = headers["Accept"]
      assert String.starts_with?(accept, "application/json")
      assert String.contains?(accept, "text/html")
    end

    test "filters out empty entries" do
      headers = PetstoreClient.HeaderSelector.select_headers(["", "application/json", nil], "application/json", false)
      assert headers["Accept"] == "application/json"
    end
  end

  describe "get_next_weight/2" do
    test "returns standard weight sequence for <= 28 headers" do
      assert PetstoreClient.HeaderSelector.get_next_weight(1000, false) == 900
      assert PetstoreClient.HeaderSelector.get_next_weight(900, false) == 800
      assert PetstoreClient.HeaderSelector.get_next_weight(100, false) == 90
    end

    test "returns 1-by-1 decrement for > 28 headers" do
      assert PetstoreClient.HeaderSelector.get_next_weight(1000, true) == 999
      assert PetstoreClient.HeaderSelector.get_next_weight(999, true) == 998
    end

    test "returns 1 when weight is 1 or less" do
      assert PetstoreClient.HeaderSelector.get_next_weight(1, false) == 1
      assert PetstoreClient.HeaderSelector.get_next_weight(0, false) == 1
    end
  end
end
