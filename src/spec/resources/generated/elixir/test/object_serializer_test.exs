defmodule PetstoreClient.ObjectSerializerTest do
  use ExUnit.Case, async: true

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
