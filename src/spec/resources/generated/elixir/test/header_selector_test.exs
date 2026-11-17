defmodule PetstoreClient.HeaderSelectorTest do
  use ExUnit.Case, async: true

  describe "json_mime?/1" do
    test "bhv headers 019 returns true for application/json" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/json") == true
    end

    test "bhv headers 020 returns true for application/json with charset" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/json; charset=UTF-8") == true
    end

    test "bhv headers 021 returns true for uppercase APPLICATION/JSON (case insensitive)" do
      assert PetstoreClient.HeaderSelector.json_mime?("APPLICATION/JSON") == true
    end

    test "bhv headers 022 returns true for vendor JSON types" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/vnd.api+json") == true
      assert PetstoreClient.HeaderSelector.json_mime?("application/vnd.company+json") == true
      assert PetstoreClient.HeaderSelector.json_mime?("application/hal+json") == true
    end

    test "bhv headers 016 returns false for text/html" do
      assert PetstoreClient.HeaderSelector.json_mime?("text/html") == false
    end

    test "bhv headers 013 returns false for application/xml" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/xml") == false
    end

    test "bhv headers 015 returns false for nil" do
      assert PetstoreClient.HeaderSelector.json_mime?(nil) == false
    end

    test "bhv headers 014 returns false for empty string" do
      assert PetstoreClient.HeaderSelector.json_mime?("") == false
    end

    test "returns false for text/plain" do
      assert PetstoreClient.HeaderSelector.json_mime?("text/plain") == false
    end

    test "bhv headers 012 returns false for application/octet-stream" do
      assert PetstoreClient.HeaderSelector.json_mime?("application/octet-stream") == false
    end
  end

  describe "select_headers/3" do
    test "bhv headers 024 sets Accept header when accepts provided" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["application/json"],
          "application/json",
          false
        )

      assert headers["Accept"] == "application/json"
    end

    test "bhv headers 004 does not set Accept header when accepts empty" do
      headers = PetstoreClient.HeaderSelector.select_headers([], "application/json", false)
      assert headers["Accept"] == nil
      refute Map.has_key?(headers, "Accept")
    end

    test "bhv headers 005 does not set Accept header when accepts is nil" do
      headers = PetstoreClient.HeaderSelector.select_headers(nil, "application/json", false)
      refute Map.has_key?(headers, "Accept")
    end

    test "bhv headers 006 does not set Accept header when all entries are filtered out" do
      headers = PetstoreClient.HeaderSelector.select_headers(["", nil], "application/json", false)
      refute Map.has_key?(headers, "Accept")
    end

    test "bhv headers 025 sets Content-Type header when not multipart" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["application/json"],
          "application/json",
          false
        )

      assert headers["Content-Type"] == "application/json"
    end

    test "bhv headers 007 does not set Content-Type header when multipart" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["application/json"],
          "application/json",
          true
        )

      assert headers["Content-Type"] == nil
    end

    test "bhv headers 001 defaults Content-Type to application/json when empty" do
      headers = PetstoreClient.HeaderSelector.select_headers(["application/json"], "", false)
      assert headers["Content-Type"] == "application/json"
    end

    test "bhv headers 002 defaults Content-Type to application/json when nil" do
      headers = PetstoreClient.HeaderSelector.select_headers(["application/json"], nil, false)
      assert headers["Content-Type"] == "application/json"
    end

    test "bhv headers 017 returns single accept as-is" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["application/json"],
          "application/json",
          false
        )

      assert headers["Accept"] == "application/json"
    end

    test "bhv headers 018 returns single non-JSON accept as-is" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(["text/html"], "application/json", false)

      assert headers["Accept"] == "text/html"
    end

    test "bhv headers 011 joins media types in declaration order with comma and space" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["image/jpeg", "image/png", "application/json"],
          "application/json",
          false
        )

      assert headers["Accept"] == "image/jpeg, image/png, application/json"
    end

    test "bhv headers 003 does not reorder or weight JSON types" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["text/html", "application/json"],
          "application/json",
          false
        )

      assert headers["Accept"] == "text/html, application/json"
    end

    test "bhv headers 026 joins media types when no JSON types present" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["text/html", "text/plain"],
          "application/json",
          false
        )

      assert headers["Accept"] == "text/html, text/plain"
    end

    test "bhv headers 010 filters out empty entries before joining" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["", "image/png", nil, "application/json"],
          "application/json",
          false
        )

      assert headers["Accept"] == "image/png, application/json"
    end

    test "bhv headers 008 drops whitespace-only entries before joining" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["   ", "application/json"],
          "application/json",
          false
        )

      assert headers["Accept"] == "application/json"
    end

    test "bhv headers 009 returns single entry without separator" do
      headers =
        PetstoreClient.HeaderSelector.select_headers(
          ["application/json"],
          "application/json",
          false
        )

      assert headers["Accept"] == "application/json"
    end
  end
end
