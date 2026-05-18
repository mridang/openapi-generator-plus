defmodule PetstoreClient.ObjectSerializerTest do
  use ExUnit.Case, async: true

  describe "DateTimeOffsetPreservation" do
    test "UTC datetime serializes containing date-time and UTC offset" do
      dt = ~U[2024-01-01 12:30:45Z]
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert String.contains?(result, "2024-01-01")
      assert String.contains?(result, "12:30:45")
      assert String.contains?(result, "+00:00") or String.ends_with?(result, "Z")
    end

    test "positive timezone offset is preserved in serialized string" do
      {:ok, dt, _} = DateTime.from_iso8601("2024-01-01T12:30:45+05:30")
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert String.contains?(result, "+05:30"), "should contain +05:30: #{result}"
    end

    test "negative timezone offset is preserved in serialized string" do
      {:ok, dt, _} = DateTime.from_iso8601("2024-01-01T12:30:45-08:00")
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert String.contains?(result, "-08:00"), "should contain -08:00: #{result}"
    end

    test "subseconds are not part of seconds-precision output" do
      {:ok, dt, _} = DateTime.from_iso8601("2024-01-01T12:30:45+00:00")
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      refute String.contains?(result, ".123")
    end

    test "date-only serializes as ISO 8601 date without time component" do
      d = ~D[2024-01-01]
      result = PetstoreClient.ObjectSerializer.stringify(d)
      assert result == "2024-01-01"
    end

    test "serialized datetime string contains an offset marker" do
      dt = ~U[2024-01-01 12:30:45Z]
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      has_offset = String.contains?(result, "+") or String.contains?(result, "-") or String.ends_with?(result, "Z")
      assert has_offset, "should contain timezone indicator: #{result}"
    end

    test "round-trip: serialize then deserialize yields equivalent datetime" do
      {:ok, original, _} = DateTime.from_iso8601("2024-01-01T12:30:45+05:30")
      serialized = PetstoreClient.ObjectSerializer.stringify(original)
      {:ok, parsed, _} = DateTime.from_iso8601(serialized)
      assert DateTime.to_unix(original) == DateTime.to_unix(parsed)
    end
  end

  describe "NonAsciiSerialization" do
    test "accented character serializes without unicode escape" do
      result = PetstoreClient.ObjectSerializer.serialize("café")
      assert String.contains?(result, "é"), "should contain literal é: #{result}"
    end

    test "CJK characters serialize without unicode escape" do
      result = PetstoreClient.ObjectSerializer.serialize("日本")
      assert String.contains?(result, "日本"), "should contain literal CJK chars: #{result}"
    end

    test "tab character is properly escaped in JSON" do
      result = PetstoreClient.ObjectSerializer.serialize("a\tb")
      assert String.contains?(result, "\\t"), "tab should be escaped: #{result}"
    end
  end

  describe "DeserializationErrorWrapping" do
    test "truncated JSON raises SerializationError" do
      assert_raise PetstoreClient.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize("{", "Category")
      end
    end

    test "incomplete JSON object raises SerializationError" do
      assert_raise PetstoreClient.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize("{\"name\":", "Category")
      end
    end

    test "thrown SerializationError has a message referencing the failure" do
      try do
        PetstoreClient.ObjectSerializer.deserialize("{", "Category")
        flunk("Expected SerializationError to be raised")
      rescue
        e in PetstoreClient.SerializationError ->
          assert e.message != nil and e.message != ""
      end
    end
  end

  describe "stringify/1" do
    test "returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.stringify(nil) == ""
    end

    test "returns 'true' for boolean true" do
      assert PetstoreClient.ObjectSerializer.stringify(true) == "true"
    end

    test "returns 'false' for boolean false" do
      assert PetstoreClient.ObjectSerializer.stringify(false) == "false"
    end

    test "returns string representation of integer" do
      assert PetstoreClient.ObjectSerializer.stringify(42) == "42"
    end

    test "passes through plain string unchanged" do
      assert PetstoreClient.ObjectSerializer.stringify("hello") == "hello"
    end

    test "returns string representation of float" do
      assert PetstoreClient.ObjectSerializer.stringify(3.14) == "3.14"
    end

    test "returns ISO 8601 date string for Date" do
      assert PetstoreClient.ObjectSerializer.stringify(~D[2024-01-15]) == "2024-01-15"
    end
  end

  describe "to_path_value/1" do
    test "returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.to_path_value(nil) == ""
    end

    test "returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_path_value("hello") == "hello"
    end

    test "converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_path_value(42) == "42"
    end

    test "converts true to 'true'" do
      assert PetstoreClient.ObjectSerializer.to_path_value(true) == "true"
    end

    test "converts false to 'false'" do
      assert PetstoreClient.ObjectSerializer.to_path_value(false) == "false"
    end
  end

  describe "to_query_value/2" do
    test "returns nil for nil" do
      assert PetstoreClient.ObjectSerializer.to_query_value(nil) == nil
    end

    test "returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_query_value("hello") == "hello"
    end

    test "converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_query_value(42) == "42"
    end

    test "converts true to 'true'" do
      assert PetstoreClient.ObjectSerializer.to_query_value(true) == "true"
    end

    test "converts false to 'false'" do
      assert PetstoreClient.ObjectSerializer.to_query_value(false) == "false"
    end

    test "joins array with comma by default" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"]) == "a,b,c"
    end

    test "joins array with comma for csv" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :csv) == "a,b,c"
    end

    test "joins array with space for ssv" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :ssv) == "a b c"
    end

    test "joins array with tab for tsv" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :tsv) == "a\tb\tc"
    end

    test "joins array with pipe for pipes" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :pipes) == "a|b|c"
    end

    test "returns array as-is for multi" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :multi) == ["a", "b", "c"]
    end
  end

  describe "to_header_value/1" do
    test "returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.to_header_value(nil) == ""
    end

    test "returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_header_value("hello") == "hello"
    end

    test "converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_header_value(42) == "42"
    end

    test "joins array with comma" do
      assert PetstoreClient.ObjectSerializer.to_header_value(["a", "b", "c"]) == "a,b,c"
    end
  end

  describe "to_form_value/1" do
    test "returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.to_form_value(nil) == ""
    end

    test "returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_form_value("hello") == "hello"
    end

    test "converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_form_value(42) == "42"
    end

    test "converts true to 'true'" do
      assert PetstoreClient.ObjectSerializer.to_form_value(true) == "true"
    end

    test "converts false to 'false'" do
      assert PetstoreClient.ObjectSerializer.to_form_value(false) == "false"
    end
  end

  describe "to_cookie_value/1" do
    test "returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.to_cookie_value(nil) == ""
    end

    test "returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_cookie_value("hello") == "hello"
    end

    test "converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_cookie_value(42) == "42"
    end
  end

  describe "serialize/1" do
    test "serializes a model to valid JSON" do
      category = %PetstoreClient.Models.Category{id: 1, name: "Dogs"}
      json = PetstoreClient.ObjectSerializer.serialize(category)
      data = Jason.decode!(json)
      assert data["id"] == 1
      assert data["name"] == "Dogs"
    end

    test "handles nil" do
      json = PetstoreClient.ObjectSerializer.serialize(nil)
      assert json == "null"
    end

    test "includes fields explicitly set to default values" do
      category = %PetstoreClient.Models.Category{id: 0, name: ""}
      json = PetstoreClient.ObjectSerializer.serialize(category)
      data = Jason.decode!(json)
      assert Map.has_key?(data, "id"), "serialized JSON should include id field"
      assert data["id"] == 0
      assert Map.has_key?(data, "name"), "serialized JSON should include name field"
      assert data["name"] == ""
    end
  end

  describe "deserialize/2" do
    test "deserializes JSON to typed model" do
      json_str = ~s({"id":1,"name":"Dogs"})
      category = PetstoreClient.ObjectSerializer.deserialize(json_str, "Category")
      assert %PetstoreClient.Models.Category{} = category
      assert category.id == 1
      assert category.name == "Dogs"
    end

    test "returns nil for empty input" do
      assert PetstoreClient.ObjectSerializer.deserialize("", "Category") == nil
    end

    test "returns nil for nil input" do
      assert PetstoreClient.ObjectSerializer.deserialize(nil, "Category") == nil
    end
  end
end
