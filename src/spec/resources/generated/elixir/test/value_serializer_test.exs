defmodule PetstoreClient.ValueSerializerTest do
  use ExUnit.Case, async: true

  describe "path location" do
    test "null returns empty string" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :path, "string") == ""
    end

    test "string returns URL-encoded value" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :path, "string") == "hello"
    end

    test "string with spaces is URL-encoded" do
      assert PetstoreClient.ValueSerializer.serialize("hello world", :path, "string") == "hello%20world"
    end

    test "string with slash is URL-encoded" do
      assert PetstoreClient.ValueSerializer.serialize("a/b", :path, "string") == "a%2Fb"
    end

    test "integer returns string" do
      assert PetstoreClient.ValueSerializer.serialize(42, :path, "integer") == "42"
    end

    test "boolean true returns true" do
      assert PetstoreClient.ValueSerializer.serialize(true, :path, "boolean") == "true"
    end

    test "boolean false returns false" do
      assert PetstoreClient.ValueSerializer.serialize(false, :path, "boolean") == "false"
    end
  end

  describe "query location" do
    test "null returns nil" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :query, "string") == nil
    end

    test "string returns as-is" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :query, "string") == "hello"
    end

    test "integer returns string" do
      assert PetstoreClient.ValueSerializer.serialize(42, :query, "integer") == "42"
    end

    test "boolean true returns true" do
      assert PetstoreClient.ValueSerializer.serialize(true, :query, "boolean") == "true"
    end

    test "boolean false returns false" do
      assert PetstoreClient.ValueSerializer.serialize(false, :query, "boolean") == "false"
    end

    test "array joins with comma by default" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array") == "a,b,c"
    end

    test "array joins with space for ssv" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array", collection_format: :ssv) == "a b c"
    end

    test "array joins with tab for tsv" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array", collection_format: :tsv) == "a\tb\tc"
    end

    test "array joins with pipe for pipes" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array", collection_format: :pipes) == "a|b|c"
    end

    test "array returns list for multi" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array", collection_format: :multi) == ["a", "b", "c"]
    end

    test "array joins with comma for csv" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array", collection_format: :csv) == "a,b,c"
    end

    test "empty array returns empty string for csv" do
      assert PetstoreClient.ValueSerializer.serialize([], :query, "array") == ""
    end

    test "empty array returns empty list for multi" do
      assert PetstoreClient.ValueSerializer.serialize([], :query, "array", collection_format: :multi) == []
    end

    test "single-element array returns single value" do
      assert PetstoreClient.ValueSerializer.serialize(["a"], :query, "array") == "a"
    end

    test "array of integers stringifies elements" do
      assert PetstoreClient.ValueSerializer.serialize([1, 2, 3], :query, "array") == "1,2,3"
    end

    test "array of booleans stringifies elements" do
      assert PetstoreClient.ValueSerializer.serialize([true, false], :query, "array") == "true,false"
    end
  end

  describe "header location" do
    test "null returns empty string" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :header, "string") == ""
    end

    test "string returns as-is" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :header, "string") == "hello"
    end

    test "integer returns string" do
      assert PetstoreClient.ValueSerializer.serialize(42, :header, "integer") == "42"
    end

    test "boolean true returns true" do
      assert PetstoreClient.ValueSerializer.serialize(true, :header, "boolean") == "true"
    end

    test "array joins with comma" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :header, "array") == "a,b,c"
    end

    test "empty array joins to empty string" do
      assert PetstoreClient.ValueSerializer.serialize([], :header, "array") == ""
    end

    test "array of integers stringifies and joins" do
      assert PetstoreClient.ValueSerializer.serialize([1, 2, 3], :header, "array") == "1,2,3"
    end
  end

  describe "cookie location" do
    test "string returns as-is" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :cookie, "string") == "hello"
    end

    test "null returns empty string" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :cookie, "string") == ""
    end
  end

  describe "form location" do
    test "null returns empty string" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :form, "string") == ""
    end

    test "string returns as-is" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :form, "string") == "hello"
    end

    test "integer returns string" do
      assert PetstoreClient.ValueSerializer.serialize(42, :form, "integer") == "42"
    end

    test "boolean true returns true" do
      assert PetstoreClient.ValueSerializer.serialize(true, :form, "boolean") == "true"
    end

    test "boolean false returns false" do
      assert PetstoreClient.ValueSerializer.serialize(false, :form, "boolean") == "false"
    end
  end

  describe "serialize_styled/7" do
    test "matrix scalar returns semicolon-prefixed name=value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "blue", :path, "string", nil, "matrix", true)
      assert result == ";color=blue"
    end

    test "matrix array with explode false joins with comma" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue", "black"], :path, "array", nil, "matrix", false)
      assert result == ";color=blue,black"
    end

    test "matrix array with explode true repeats name" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue", "black"], :path, "array", nil, "matrix", true)
      assert result == ";color=blue;color=black"
    end

    test "matrix null returns empty string" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", nil, :path, "string", nil, "matrix", true)
      assert result == ""
    end

    test "label scalar returns dot-prefixed value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "blue", :path, "string", nil, "label", true)
      assert result == ".blue"
    end

    test "label array with explode false joins with comma" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue", "black"], :path, "array", nil, "label", false)
      assert result == ".blue,black"
    end

    test "label array with explode true joins with dot separator" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue", "black"], :path, "array", nil, "label", true)
      assert result == ".blue.black"
    end

    test "label null returns empty string" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", nil, :path, "string", nil, "label", true)
      assert result == ""
    end

    test "spaceDelimited array joins with space" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue", "black"], :query, "array", nil, "spaceDelimited", false)
      assert result == "blue black"
    end

    test "spaceDelimited scalar returns stringified value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "blue", :query, "string", nil, "spaceDelimited", false)
      assert result == "blue"
    end

    test "pipeDelimited array joins with pipe" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue", "black"], :query, "array", nil, "pipeDelimited", false)
      assert result == "blue|black"
    end

    test "pipeDelimited scalar returns stringified value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "blue", :query, "string", nil, "pipeDelimited", false)
      assert result == "blue"
    end

    test "form array with explode false joins with comma" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue", "black"], :query, "array", nil, "form", false)
      assert result == "blue,black"
    end

    test "form array with explode true returns list" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue", "black"], :query, "array", nil, "form", true)
      assert result == ["blue", "black"]
    end

    test "form scalar with explode true returns string not list" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "blue", :query, "string", nil, "form", true)
      assert is_binary(result)
      assert result == "blue"
    end

    test "form single-element array with explode true returns list" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["blue"], :query, "array", nil, "form", true)
      assert is_list(result)
      assert result == ["blue"]
    end

    test "form null returns nil for query location" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", nil, :query, "string", nil, "form", true)
      assert result == nil
    end

    test "simple null returns empty string" do
      result = PetstoreClient.ValueSerializer.serialize_styled("id", nil, :path, "string", nil, "simple", true)
      assert result == ""
    end

    test "simple scalar returns stringified value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("id", "5", :path, "string", nil, "simple", false)
      assert result == "5"
    end

    test "simple array joins with comma" do
      result = PetstoreClient.ValueSerializer.serialize_styled("id", ["3", "4", "5"], :path, "array", nil, "simple", false)
      assert result == "3,4,5"
    end

    test "simple scalar URL-encodes path value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("id", "hello world", :path, "string", nil, "simple", false)
      assert result == "hello%20world"
    end

    test "null style falls back to location-based serialization" do
      result = PetstoreClient.ValueSerializer.serialize_styled("id", "5", :path, "string", nil, nil, false)
      assert result == "5"
    end

    test "empty style falls back to location-based serialization" do
      result = PetstoreClient.ValueSerializer.serialize_styled("id", "5", :path, "string", nil, "", false)
      assert result == "5"
    end
  end

  describe "serialize_deep_object/2" do
    test "basic map returns bracketed keys" do
      result = PetstoreClient.ValueSerializer.serialize_deep_object("filter", %{"color" => "blue", "size" => "large"})
      assert result["filter[color]"] == "blue"
      assert result["filter[size]"] == "large"
    end

    test "null returns empty map" do
      result = PetstoreClient.ValueSerializer.serialize_deep_object("filter", nil)
      assert result == %{}
    end
  end

  # Cross-language parity tests for path-segment percent-encoding.
  # Every SDK must produce identical encoded strings for these inputs.
  describe "path encoding parity" do
    test "ASCII-safe pass-through" do
      assert PetstoreClient.ValueSerializer.serialize("abc123", :path, "string") == "abc123"
    end

    test "space encoded as %20" do
      assert PetstoreClient.ValueSerializer.serialize("a b", :path, "string") == "a%20b"
    end

    test "slash encoded" do
      assert PetstoreClient.ValueSerializer.serialize("a/b", :path, "string") == "a%2Fb"
    end

    test "question mark encoded" do
      assert PetstoreClient.ValueSerializer.serialize("a?b", :path, "string") == "a%3Fb"
    end

    test "hash encoded" do
      assert PetstoreClient.ValueSerializer.serialize("a#b", :path, "string") == "a%23b"
    end

    test "comma preserved (sub-delimiter)" do
      assert PetstoreClient.ValueSerializer.serialize("a,b", :path, "string") == "a,b"
    end

    test "colon preserved (sub-delimiter)" do
      assert PetstoreClient.ValueSerializer.serialize("a:b", :path, "string") == "a:b"
    end

    test "plus preserved (sub-delimiter)" do
      assert PetstoreClient.ValueSerializer.serialize("a+b", :path, "string") == "a+b"
    end

    test "unicode encoded as UTF-8 percent" do
      assert PetstoreClient.ValueSerializer.serialize("日本", :path, "string") == "%E6%97%A5%E6%9C%AC"
    end

    test "empty string preserved" do
      assert PetstoreClient.ValueSerializer.serialize("", :path, "string") == ""
    end

    test "null returns empty string in path location" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :path, "string") == ""
    end

    test "simple style encodes value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "a b", :path, "string", nil, "simple", false)
      assert result == "a%20b"
    end

    test "simple style array encodes each item" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", ["a b", "c?d"], :path, "array", nil, "simple", false)
      assert result == "a%20b,c%3Fd"
    end

    test "matrix style encodes value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "a b", :path, "string", nil, "matrix", false)
      assert result == ";color=a%20b"
    end

    test "label style encodes value" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "a b", :path, "string", nil, "label", false)
      assert result == ".a%20b"
    end

    test "query location is not path-encoded" do
      result = PetstoreClient.ValueSerializer.serialize_styled("color", "a b", :query, "string", nil, "form", false)
      assert result == "a b"
    end

    test "empty string path param raises ArgumentError" do
      # Gap W — empty-string path values silently produce malformed
      # URLs like `/pet//details`; reject at serialization time so
      # callers see the real error rather than a downstream 404.
      assert_raise ArgumentError, fn ->
        PetstoreClient.ValueSerializer.serialize_styled("id", "", :path, "string", nil, "simple", false)
      end
    end
  end

  # N3/W3 parity: `format: date` path parameters must emit a date-only
  # string (YYYY-MM-DD), not a full ISO datetime. Elixir models
  # `format: date` as `Date`, whose `Date.to_iso8601/1` already produces
  # YYYY-MM-DD.
  describe "format:date path parameter emits YYYY-MM-DD" do
    test "Date in path returns YYYY-MM-DD" do
      date = ~D[2024-01-15]
      assert PetstoreClient.ValueSerializer.serialize(date, :path, "string") == "2024-01-15"
    end

    test "Date via serialize_styled simple returns YYYY-MM-DD" do
      date = ~D[2024-01-15]
      result = PetstoreClient.ValueSerializer.serialize_styled("since", date, :path, "string", nil, "simple", false)
      assert result == "2024-01-15"
    end
  end
end
