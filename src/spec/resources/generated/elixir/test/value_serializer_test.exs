defmodule PetstoreClient.ValueSerializerTest do
  use ExUnit.Case, async: true

  describe "path location" do
    test "bhv serializationparam 062 null returns empty string" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :path, "string") == ""
    end

    test "bhv serialization 108 a parameter with no declared style uses the default style for its location" do
      assert PetstoreClient.ValueSerializer.serialize_styled(
               "id",
               ["a", "b"],
               :path,
               "array",
               nil,
               nil,
               false
             ) ==
               PetstoreClient.ValueSerializer.serialize_styled(
                 "id",
                 ["a", "b"],
                 :path,
                 "array",
                 nil,
                 "simple",
                 false
               )
    end

    test "bhv serialization 110 an absent value is sent as nil in the query location and as an empty value elsewhere" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :query, "string") == nil
      assert PetstoreClient.ValueSerializer.serialize(nil, :header, "string") == ""
      assert PetstoreClient.ValueSerializer.serialize(nil, :path, "string") == ""
    end

    test "bhv serializationparam 100 string returns URL-encoded value" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :path, "string") == "hello"
    end

    test "bhv serializationparam 102 string with spaces is URL-encoded" do
      assert PetstoreClient.ValueSerializer.serialize("hello world", :path, "string") ==
               "hello%20world"
    end

    test "bhv serializationparam 101 string with slash is URL-encoded" do
      assert PetstoreClient.ValueSerializer.serialize("a/b", :path, "string") == "a%2Fb"
    end

    test "bhv serializationparam 044 integer returns string" do
      assert PetstoreClient.ValueSerializer.serialize(42, :path, "integer") == "42"
    end

    test "bhv serializationparam 021 boolean true returns true" do
      assert PetstoreClient.ValueSerializer.serialize(true, :path, "boolean") == "true"
    end

    test "bhv serializationparam 018 boolean false returns false" do
      assert PetstoreClient.ValueSerializer.serialize(false, :path, "boolean") == "false"
    end
  end

  describe "query location" do
    test "bhv serializationparam 067 null returns nil" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :query, "string") == nil
    end

    test "bhv serializationparam 096 string returns as-is" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :query, "string") == "hello"
    end

    test "bhv serializationparam 045 integer returns string" do
      assert PetstoreClient.ValueSerializer.serialize(42, :query, "integer") == "42"
    end

    test "bhv serializationparam 022 boolean true returns true" do
      assert PetstoreClient.ValueSerializer.serialize(true, :query, "boolean") == "true"
    end

    test "bhv serializationparam 019 boolean false returns false" do
      assert PetstoreClient.ValueSerializer.serialize(false, :query, "boolean") == "false"
    end

    test "bhv serializationparam 006 array joins with comma by default" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array") == "a,b,c"
    end

    test "bhv misc 250 array items containing a comma keep the literal comma across path and query styles" do
      assert PetstoreClient.ValueSerializer.serialize(["a,b", "c"], :path, "array") == "a,b,c"
      assert PetstoreClient.ValueSerializer.serialize(["a,b", "c"], :query, "array") == "a,b,c"
    end

    test "bhv serializationparam 009 array joins with space for ssv" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array",
               collection_format: :ssv
             ) == "a b c"
    end

    test "bhv serializationparam 010 array joins with tab for tsv" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array",
               collection_format: :tsv
             ) == "a\tb\tc"
    end

    test "bhv serializationparam 008 array joins with pipe for pipes" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array",
               collection_format: :pipes
             ) == "a|b|c"
    end

    test "bhv serializationparam 015 array returns list for multi" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array",
               collection_format: :multi
             ) == ["a", "b", "c"]
    end

    test "bhv serializationparam 007 array joins with comma for csv" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :query, "array",
               collection_format: :csv
             ) == "a,b,c"
    end

    # A nil element is an empty slot: it is neither skipped nor rendered as the
    # word nil.
    test "bhv serializationparam 001 a nil element keeps its slot in every array style" do
      for {format, expected} <- [
            {:csv, "1,,3"},
            {:ssv, "1  3"},
            {:tsv, "1\t\t3"},
            {:pipes, "1||3"},
            {:multi, ["1", "", "3"]}
          ] do
        assert PetstoreClient.ValueSerializer.serialize([1, nil, 3], :query, "array",
                 collection_format: format
               ) == expected
      end
    end

    test "bhv serializationparam 011 leading and trailing nil elements keep their slots" do
      assert PetstoreClient.ValueSerializer.serialize([nil, "x", nil], :query, "array",
               collection_format: :csv
             ) == ",x,"
    end

    test "bhv serializationparam 115 a csv query array keeps an empty slot for a null element" do
      assert PetstoreClient.ValueSerializer.serialize([1, nil, 3], :query, "array",
               collection_format: :csv
             ) == "1,,3"
    end

    test "bhv serializationparam 032 empty array returns empty string for csv" do
      assert PetstoreClient.ValueSerializer.serialize([], :query, "array") == ""
    end

    test "bhv serializationparam 031 empty array returns empty list for multi" do
      assert PetstoreClient.ValueSerializer.serialize([], :query, "array",
               collection_format: :multi
             ) == []
    end

    test "bhv serializationparam 090 single-element array returns single value" do
      assert PetstoreClient.ValueSerializer.serialize(["a"], :query, "array") == "a"
    end

    test "bhv serializationparam 014 array of integers stringifies elements" do
      assert PetstoreClient.ValueSerializer.serialize([1, 2, 3], :query, "array") == "1,2,3"
    end

    test "bhv serializationparam 012 array of booleans stringifies elements" do
      assert PetstoreClient.ValueSerializer.serialize([true, false], :query, "array") ==
               "true,false"
    end
  end

  describe "header location" do
    test "bhv serializationparam 063 null returns empty string" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :header, "string") == ""
    end

    test "bhv serializationparam 097 string returns as-is" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :header, "string") == "hello"
    end

    test "bhv serializationparam 046 integer returns string" do
      assert PetstoreClient.ValueSerializer.serialize(42, :header, "integer") == "42"
    end

    test "bhv serializationparam 023 boolean true returns true" do
      assert PetstoreClient.ValueSerializer.serialize(true, :header, "boolean") == "true"
    end

    test "bhv serializationparam 005 array joins with comma" do
      assert PetstoreClient.ValueSerializer.serialize(["a", "b", "c"], :header, "array") ==
               "a,b,c"
    end

    test "bhv serializationparam 030 empty array joins to empty string" do
      assert PetstoreClient.ValueSerializer.serialize([], :header, "array") == ""
    end

    test "bhv serializationparam 013 array of integers stringifies and joins" do
      assert PetstoreClient.ValueSerializer.serialize([1, 2, 3], :header, "array") == "1,2,3"
    end
  end

  describe "cookie location" do
    test "bhv serializationparam 099 string returns as-is" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :cookie, "string") == "hello"
    end

    test "bhv serializationparam 065 null returns empty string" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :cookie, "string") == ""
    end
  end

  describe "form location" do
    test "bhv serializationparam 064 null returns empty string" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :form, "string") == ""
    end

    test "bhv serializationparam 098 string returns as-is" do
      assert PetstoreClient.ValueSerializer.serialize("hello", :form, "string") == "hello"
    end

    test "bhv serializationparam 047 integer returns string" do
      assert PetstoreClient.ValueSerializer.serialize(42, :form, "integer") == "42"
    end

    test "bhv serializationparam 024 boolean true returns true" do
      assert PetstoreClient.ValueSerializer.serialize(true, :form, "boolean") == "true"
    end

    test "bhv serializationparam 020 boolean false returns false" do
      assert PetstoreClient.ValueSerializer.serialize(false, :form, "boolean") == "false"
    end
  end

  describe "serialize_styled/7" do
    test "bhv serializationparam 058 matrix scalar returns semicolon-prefixed name=value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "blue",
          :path,
          "string",
          nil,
          "matrix",
          true
        )

      assert result == ";color=blue"
    end

    test "bhv serializationparam 055 matrix array with explode false joins with comma" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :path,
          "array",
          nil,
          "matrix",
          false
        )

      assert result == ";color=blue,black"
    end

    test "bhv serializationparam 056 matrix array with explode true repeats name" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :path,
          "array",
          nil,
          "matrix",
          true
        )

      assert result == ";color=blue;color=black"
    end

    test "bhv serializationparam 057 matrix null returns empty string" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          nil,
          :path,
          "string",
          nil,
          "matrix",
          true
        )

      assert result == ""
    end

    test "bhv serializationparam 052 label scalar returns dot-prefixed value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "blue",
          :path,
          "string",
          nil,
          "label",
          true
        )

      assert result == ".blue"
    end

    test "bhv serializationparam 049 label array with explode false joins with comma" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :path,
          "array",
          nil,
          "label",
          false
        )

      assert result == ".blue,black"
    end

    test "bhv serializationparam 050 label array with explode true joins with dot separator" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :path,
          "array",
          nil,
          "label",
          true
        )

      assert result == ".blue.black"
    end

    test "bhv serializationparam 051 label null returns empty string" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          nil,
          :path,
          "string",
          nil,
          "label",
          true
        )

      assert result == ""
    end

    test "bhv serializationparam 093 spaceDelimited array joins with space" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :query,
          "array",
          nil,
          "spaceDelimited",
          false
        )

      assert result == "blue black"
    end

    test "bhv serializationparam 094 spaceDelimited array with explode true returns list" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :query,
          "array",
          nil,
          "spaceDelimited",
          true
        )

      assert result == ["blue", "black"]
    end

    test "bhv serializationparam 095 spaceDelimited scalar returns stringified value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "blue",
          :query,
          "string",
          nil,
          "spaceDelimited",
          false
        )

      assert result == "blue"
    end

    test "bhv serializationparam 074 pipeDelimited array joins with pipe" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :query,
          "array",
          nil,
          "pipeDelimited",
          false
        )

      assert result == "blue|black"
    end

    test "bhv serializationparam 075 pipeDelimited array with explode true returns list" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :query,
          "array",
          nil,
          "pipeDelimited",
          true
        )

      assert result == ["blue", "black"]
    end

    test "bhv serializationparam 076 pipeDelimited scalar returns stringified value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "blue",
          :query,
          "string",
          nil,
          "pipeDelimited",
          false
        )

      assert result == "blue"
    end

    test "bhv serializationparam 037 form array with explode false joins with comma" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :query,
          "array",
          nil,
          "form",
          false
        )

      assert result == "blue,black"
    end

    test "bhv serializationparam 038 form array with explode true returns list" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue", "black"],
          :query,
          "array",
          nil,
          "form",
          true
        )

      assert result == ["blue", "black"]
    end

    test "bhv serializationparam 041 form scalar with explode true returns string not list" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "blue",
          :query,
          "string",
          nil,
          "form",
          true
        )

      assert is_binary(result)
      assert result == "blue"
    end

    test "bhv serializationparam 042 form single-element array with explode true returns list" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["blue"],
          :query,
          "array",
          nil,
          "form",
          true
        )

      assert is_list(result)
      assert result == ["blue"]
    end

    test "bhv serializationparam 040 form null returns nil for query location" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          nil,
          :query,
          "string",
          nil,
          "form",
          true
        )

      assert result == nil
    end

    test "bhv serializationparam 085 simple null returns empty string" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          nil,
          :path,
          "string",
          nil,
          "simple",
          true
        )

      assert result == ""
    end

    test "bhv serializationparam 086 simple scalar returns stringified value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "5",
          :path,
          "string",
          nil,
          "simple",
          false
        )

      assert result == "5"
    end

    test "bhv serializationparam 084 simple array joins with comma" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          ["3", "4", "5"],
          :path,
          "array",
          nil,
          "simple",
          false
        )

      assert result == "3,4,5"
    end

    test "bhv serializationparam 087 simple scalar URL-encodes path value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "hello world",
          :path,
          "string",
          nil,
          "simple",
          false
        )

      assert result == "hello%20world"
    end

    test "bhv serializationparam 105 a form scalar is the bare value, exploded or not, and an address is its canonical text" do
      for explode <- [true, false] do
        result =
          PetstoreClient.ValueSerializer.serialize_styled(
            "color",
            "blue",
            :query,
            "string",
            nil,
            "form",
            explode
          )

        assert result == "blue"
      end

      assert PetstoreClient.ObjectSerializer.stringify({192, 0, 2, 1}) == "192.0.2.1"

      assert PetstoreClient.ObjectSerializer.stringify({0x2001, 0xDB8, 0, 0, 0, 0, 0, 1}) ==
               "2001:db8::1"
    end

    test "bhv serializationparam 106 path with null style behaves like simple" do
      null_style =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "hello world",
          :path,
          "string",
          nil,
          nil,
          false
        )

      simple =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "hello world",
          :path,
          "string",
          nil,
          "simple",
          false
        )

      assert null_style == simple
      assert null_style == "hello%20world"
    end

    test "bhv langelixir 013 null style falls back to location-based serialization" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "5",
          :path,
          "string",
          nil,
          nil,
          false
        )

      assert result == "5"
    end

    test "bhv serializationparam 109 an unknown style falls back to plain value serialization" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "5",
          :query,
          "string",
          nil,
          "unknownStyle",
          false
        )

      assert result == "5"
    end

    test "bhv serializationparam 035 empty style falls back to location-based serialization" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "5",
          :path,
          "string",
          nil,
          "",
          false
        )

      assert result == "5"
    end
  end

  describe "uniqueItems (MapSet) parameters" do
    test "bhv serializationparam 083 a set expands like an array for form style, exploded and not" do
      set = MapSet.new(["red", "blue"])

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "labels",
               set,
               :query,
               "array",
               nil,
               "form",
               false
             ) ==
               "blue,red"

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "labels",
               set,
               :query,
               "array",
               nil,
               "form",
               true
             ) ==
               ["blue", "red"]
    end

    test "bhv serializationparam 117 a set in form style without explode joins its elements with commas" do
      set = MapSet.new(["red", "blue"])

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "labels",
               set,
               :query,
               "array",
               nil,
               "form",
               false
             ) == "blue,red"
    end

    test "bhv serialization 013 a set expands like an array for the unstyled and header paths" do
      set = MapSet.new(["red", "blue"])

      assert PetstoreClient.ValueSerializer.serialize(set, :query, "array",
               collection_format: :multi
             ) == ["blue", "red"]

      assert PetstoreClient.ValueSerializer.serialize(set, :header, "array") == "blue,red"
    end
  end

  describe "object-valued parameters (D47)" do
    test "bhv serialization 017 a model is written by its wire names in declaration order, in every style" do
      criteria = %PetstoreClient.Models.PetCriteria{breed: "lab", min_age: 3}

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "criteria",
               criteria,
               :query,
               "PetCriteria",
               nil,
               "form",
               true
             ) ==
               {:exploded_object, [{"breed", "lab"}, {"min_age", "3"}]}

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "criteria",
               criteria,
               :query,
               "PetCriteria",
               nil,
               "form",
               false
             ) ==
               "breed,lab,min_age,3"

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "criteria",
               criteria,
               :header,
               "PetCriteria",
               nil,
               "simple",
               false
             ) ==
               "breed,lab,min_age,3"

      assert PetstoreClient.ValueSerializer.serialize_deep_object("criteria", criteria) ==
               %{"criteria[breed]" => "lab", "criteria[min_age]" => "3"}
    end

    test "bhv serialization 019 an unset property is left out and a map is written in key order" do
      assert PetstoreClient.ValueSerializer.object_pairs(%PetstoreClient.Models.PetCriteria{
               breed: "lab"
             }) == [{"breed", "lab"}]

      assert PetstoreClient.ValueSerializer.object_pairs(%{"b" => 2, "a" => 1}) == [
               {"a", "1"},
               {"b", "2"}
             ]
    end

    test "bhv serializationparam 108 a nested object or array leaf is a SerializationError" do
      for leaf <- [%{"x" => 1}, [1, 2]] do
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ValueSerializer.serialize_styled(
            "criteria",
            %{"a" => leaf},
            :query,
            "map()",
            nil,
            "form",
            false
          )
        end
      end
    end
  end

  describe "form and simple object expansion" do
    test "bhv serializationparam 036 expands an exploded form query value to one entry per property" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "filter",
          %{"a" => "1", "b" => "2"},
          :query,
          "object",
          nil,
          "form",
          true
        )

      assert result == {:exploded_object, [{"a", "1"}, {"b", "2"}]}
    end

    test "bhv serializationparam 039 form not exploded is one comma separated key and value list" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "filter",
          %{"a" => "1", "b" => "2"},
          :query,
          "object",
          nil,
          "form",
          false
        )

      assert result == "a,1,b,2"
    end

    test "bhv serializationparam 082 serialize_styled writes a simple-style object as a header list" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "X-Filter",
          %{"a" => "1", "b" => "2"},
          :header,
          "object",
          nil,
          "simple",
          false
        )

      assert result == "a,1,b,2"
    end
  end

  describe "date-time parameter" do
    test "bhv serializationparam 048 keeps the time of a date-time parameter that is not a format: date" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "since",
          ~U[2024-01-15 10:30:00Z],
          :path,
          "string",
          nil,
          "simple",
          false
        )

      assert String.starts_with?(result, "2024-01-15T10:30:00")
    end
  end

  describe "object-valued parameters refuse nested values" do
    test "bhv serialization 018 a nested object or array inside an object-valued parameter is refused" do
      for nested <- [%{"c" => 1}, [1, 2]] do
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ValueSerializer.object_pairs(%{"a" => nested})
        end
      end
    end
  end

  describe "format:date parameter given a date-time" do
    test "bhv misc 232 a string path parameter in the simple style percent-encodes slashes" do
      assert PetstoreClient.ValueSerializer.serialize("a/b", :path, "string") == "a%2Fb"
    end

    test "bhv misc 233 a cookie parameter carrying a JSON model is fully percent-encoded, colons and dots included" do
      result = PetstoreClient.ValueSerializer.encode_content_cookie(~s({"a":1.5}))

      assert result == "%7B%22a%22%3A1%2E5%7D"
    end

    test "bhv misc 236 a date-only value is written as yyyy-mm-dd with no time in query, header and cookie locations" do
      date = ~D[2024-01-15]
      assert PetstoreClient.ValueSerializer.serialize(date, :query, "string") == "2024-01-15"
      assert PetstoreClient.ValueSerializer.serialize(date, :header, "string") == "2024-01-15"
      assert PetstoreClient.ValueSerializer.serialize(date, :cookie, "string") == "2024-01-15"
    end

    test "bhv serialization 044 a date-time given for a date parameter is written as its calendar date only" do
      assert PetstoreClient.ValueSerializer.serialize(
               ~U[2024-01-02 23:30:00Z],
               :query,
               "Date.t()"
             ) == "2024-01-02"

      assert PetstoreClient.ValueSerializer.serialize(~N[2024-01-02 10:30:00], :query, "Date.t()") ==
               "2024-01-02"
    end
  end

  describe "serialize_deep_object/2" do
    test "bhv serializationparam 111 a deepObject parameter writes each property as name[key]" do
      result =
        PetstoreClient.ValueSerializer.serialize_deep_object("filter", %{
          "status" => "active",
          "type" => "dog"
        })

      assert result == %{"filter[status]" => "active", "filter[type]" => "dog"}
    end

    test "bhv serializationparam 017 basic map returns bracketed keys" do
      result =
        PetstoreClient.ValueSerializer.serialize_deep_object("filter", %{
          "color" => "blue",
          "size" => "large"
        })

      assert result["filter[color]"] == "blue"
      assert result["filter[size]"] == "large"
    end

    test "bhv serializationparam 061 null returns empty map" do
      result = PetstoreClient.ValueSerializer.serialize_deep_object("filter", nil)
      assert result == %{}
    end
  end

  # Cross-language parity tests for path-segment percent-encoding.
  # Every SDK must produce identical encoded strings for these inputs.
  describe "path encoding parity" do
    test "bhv serializationparam 070 path encoding parity null returns empty" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :path, "string") == ""
    end

    test "bhv serializationparam 016 ASCII-safe pass-through" do
      assert PetstoreClient.ValueSerializer.serialize("abc123", :path, "string") == "abc123"
    end

    test "bhv serializationparam 092 space encoded as %20" do
      assert PetstoreClient.ValueSerializer.serialize("a b", :path, "string") == "a%20b"
    end

    test "bhv serializationparam 091 slash encoded" do
      assert PetstoreClient.ValueSerializer.serialize("a/b", :path, "string") == "a%2Fb"
    end

    test "bhv serializationparam 079 question mark encoded" do
      assert PetstoreClient.ValueSerializer.serialize("a?b", :path, "string") == "a%3Fb"
    end

    test "bhv serializationparam 043 hash encoded" do
      assert PetstoreClient.ValueSerializer.serialize("a#b", :path, "string") == "a%23b"
    end

    test "bhv serializationparam 026 comma preserved (sub-delimiter)" do
      assert PetstoreClient.ValueSerializer.serialize("a,b", :path, "string") == "a,b"
    end

    test "bhv serializationparam 025 colon preserved (sub-delimiter)" do
      assert PetstoreClient.ValueSerializer.serialize("a:b", :path, "string") == "a:b"
    end

    test "bhv serializationparam 077 plus preserved (sub-delimiter)" do
      assert PetstoreClient.ValueSerializer.serialize("a+b", :path, "string") == "a+b"
    end

    # Every sub-delimiter and unreserved character OAS styles use as a
    # separator stays literal in a path segment.
    test "bhv serializationparam 081 each sub-delimiter and unreserved character is preserved" do
      for char <- String.graphemes(";=,:@!$&'()*+~-._") do
        assert PetstoreClient.ValueSerializer.serialize("a" <> char <> "b", :path, "string") ==
                 "a" <> char <> "b"
      end
    end

    test "bhv serializationparam 071 each remaining reserved or unsafe character is percent-encoded" do
      for {char, encoded} <- [
            {" ", "%20"},
            {"/", "%2F"},
            {"?", "%3F"},
            {"#", "%23"},
            {"[", "%5B"},
            {"]", "%5D"},
            {"%", "%25"},
            {"\"", "%22"},
            {"<", "%3C"},
            {">", "%3E"},
            {"|", "%7C"},
            {"^", "%5E"},
            {"`", "%60"},
            {"{", "%7B"},
            {"}", "%7D"},
            {"\\", "%5C"}
          ] do
        assert PetstoreClient.ValueSerializer.serialize("a" <> char <> "b", :path, "string") ==
                 "a" <> encoded <> "b"
      end
    end

    test "bhv serialization 005 a path value keeps unreserved and sub-delimiter characters literal and percent-encodes every other character" do
      literal = "-._~!$&'()*+,;=:@"

      for code <- 0x20..0x7E do
        char = <<code>>
        kept = URI.char_unreserved?(code) or String.contains?(literal, char)
        expected = if kept, do: char, else: "%" <> Base.encode16(char)

        assert PetstoreClient.ValueSerializer.serialize("a" <> char <> "b", :path, "string") ==
                 "a" <> expected <> "b"
      end
    end

    test "bhv serializationparam 119 a tilde is kept literal in a path parameter value" do
      assert PetstoreClient.ValueSerializer.serialize("a~b", :path, "string") == "a~b"
      assert PetstoreClient.ValueSerializer.serialize("~", :path, "string") == "~"
    end

    test "bhv serializationparam 004 a value that already looks percent-encoded is encoded again" do
      assert PetstoreClient.ValueSerializer.serialize("a%20b", :path, "string") == "a%2520b"
    end

    test "bhv serializationparam 103 unicode encoded as UTF-8 percent" do
      assert PetstoreClient.ValueSerializer.serialize("日本", :path, "string") ==
               "%E6%97%A5%E6%9C%AC"
    end

    test "bhv serializationparam 034 empty string preserved" do
      assert PetstoreClient.ValueSerializer.serialize("", :path, "string") == ""
    end

    test "bhv serializationparam 066 null returns empty string in path location" do
      assert PetstoreClient.ValueSerializer.serialize(nil, :path, "string") == ""
    end

    test "bhv serializationparam 089 simple style encodes value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "a b",
          :path,
          "string",
          nil,
          "simple",
          false
        )

      assert result == "a%20b"
    end

    test "bhv serializationparam 088 simple style array encodes each item" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          ["a b", "c?d"],
          :path,
          "array",
          nil,
          "simple",
          false
        )

      assert result == "a%20b,c%3Fd"
    end

    test "bhv serializationparam 069 path_array_item_with_reserved_char_is_percent_encoded" do
      # Gap W1 regression: every per-item path value in a styled array
      # must be percent-encoded BEFORE being joined with the structural
      # separator. Otherwise '/', '?', '#', space leak into the URL.
      items = ["a/b", "c"]

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "name",
               items,
               :path,
               "array",
               nil,
               "simple",
               false
             ) == "a%2Fb,c"

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "name",
               items,
               :path,
               "array",
               nil,
               "label",
               true
             ) == ".a%2Fb.c"

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "name",
               items,
               :path,
               "array",
               nil,
               "matrix",
               false
             ) == ";name=a%2Fb,c"
    end

    test "bhv serializationparam 059 matrix style encodes value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "a b",
          :path,
          "string",
          nil,
          "matrix",
          false
        )

      assert result == ";color=a%20b"
    end

    test "bhv serializationparam 053 label style encodes value" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "a b",
          :path,
          "string",
          nil,
          "label",
          false
        )

      assert result == ".a%20b"
    end

    test "bhv serializationparam 078 query location is not path-encoded" do
      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "color",
          "a b",
          :query,
          "string",
          nil,
          "form",
          false
        )

      assert result == "a b"
    end

    test "bhv serializationparam 033 empty string path param raises ArgumentError" do
      # Gap W — empty-string path values silently produce malformed
      # URLs like `/resource//details`; reject at serialization time so
      # callers see the real error rather than a downstream 404.
      assert_raise ArgumentError, fn ->
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "",
          :path,
          "string",
          nil,
          "simple",
          false
        )
      end
    end

    test "bhv serializationparam 029 dot-segment path values raise ArgumentError" do
      for value <- [".", ".."], style <- [nil, "", "simple"] do
        error =
          assert_raise ArgumentError, fn ->
            PetstoreClient.ValueSerializer.serialize_styled(
              "name",
              value,
              :path,
              "string",
              nil,
              style,
              false
            )
          end

        assert error.message == "Path parameter 'name' must not be '.' or '..'"
      end
    end

    test "bhv serializationparam 072 dot-containing path values that are not dot segments are accepted" do
      assert PetstoreClient.ValueSerializer.serialize_styled(
               "name",
               "a..b",
               :path,
               "string",
               nil,
               "simple",
               false
             ) ==
               "a..b"

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "name",
               ".hidden",
               :path,
               "string",
               nil,
               nil,
               false
             ) ==
               ".hidden"

      assert PetstoreClient.ValueSerializer.serialize_styled(
               "name",
               "..",
               :path,
               "string",
               nil,
               "label",
               false
             ) ==
               "...",
             "label style prefixes the value so it can never be a dot segment"
    end

    test "bhv serializationparam 073 path value is encoded exactly once (no double-encoding)" do
      # path-double-encoding: serialize_styled already percent-encodes the path
      # segment, so the api template must NOT wrap it again. A space must become
      # %20 (never %2520) and a slash %2F (never %252F).
      space =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "a b",
          :path,
          "string",
          nil,
          "simple",
          false
        )

      assert space == "a%20b"
      refute String.contains?(space, "%2520")

      slash =
        PetstoreClient.ValueSerializer.serialize_styled(
          "id",
          "a/b",
          :path,
          "string",
          nil,
          "simple",
          false
        )

      assert slash == "a%2Fb"
      refute String.contains?(slash, "%252F")
    end
  end

  # N3/W3 parity: `format: date` path parameters must emit a date-only
  # string (YYYY-MM-DD), not a full ISO datetime. Elixir models
  # `format: date` as `Date`, whose `Date.to_iso8601/1` already produces
  # YYYY-MM-DD.
  describe "format:date path parameter emits YYYY-MM-DD" do
    test "bhv serializationparam 110 a date value is written as YYYY-MM-DD in every location" do
      date = ~D[2024-01-15]
      assert PetstoreClient.ValueSerializer.serialize(date, :query, "string") == "2024-01-15"
      assert PetstoreClient.ValueSerializer.serialize(date, :header, "string") == "2024-01-15"
      assert PetstoreClient.ValueSerializer.serialize(date, :path, "string") == "2024-01-15"
    end

    test "bhv serializationparam 120 an array of date values is written with each element as YYYY-MM-DD" do
      dates = [~D[2024-01-15], ~D[2024-02-01]]

      assert PetstoreClient.ValueSerializer.serialize(dates, :header, "array") ==
               "2024-01-15,2024-02-01"

      assert PetstoreClient.ValueSerializer.serialize(dates, :query, "array") ==
               "2024-01-15,2024-02-01"
    end

    test "bhv serializationparam 028 Date in path returns YYYY-MM-DD" do
      date = ~D[2024-01-15]
      assert PetstoreClient.ValueSerializer.serialize(date, :path, "string") == "2024-01-15"
    end

    test "bhv serializationparam 054 Date via serialize_styled simple returns YYYY-MM-DD" do
      date = ~D[2024-01-15]

      result =
        PetstoreClient.ValueSerializer.serialize_styled(
          "since",
          date,
          :path,
          "string",
          nil,
          "simple",
          false
        )

      assert result == "2024-01-15"
    end

    test "bhv serializationparam 027 Date at a day/year boundary still emits date-only (no time/offset leak)" do
      # UTC/midnight edge: a date on the year boundary must serialize as a bare
      # YYYY-MM-DD with no time/offset suffix, matching the stringifyDate UTC
      # behaviour of Go/Node/Swift/Dart.
      date = ~D[2024-12-31]
      assert PetstoreClient.ValueSerializer.serialize(date, :path, "string") == "2024-12-31"
    end
  end

  describe "allowReserved query encoding" do
    test "bhv serializationparam 003 allow reserved still percent encodes non-ASCII" do
      assert PetstoreClient.ValueSerializer.encode_query_allowing_reserved("ñ/é a") ==
               "%C3%B1/%C3%A9%20a"
    end

    test "bhv serializationparam 068 only the RFC 3986 query-legal reserved set stays literal" do
      assert PetstoreClient.ValueSerializer.encode_query_allowing_reserved(
               "a:b/c?d[e]f@g!h$i&j'k(l)m*n+o,p;q=r~s#t u"
             ) ==
               "a:b/c?d%5Be%5Df@g!h$i&j'k(l)m*n+o,p;q=r~s%23t%20u"
    end

    test "bhv serializationparam 080 reserved characters are left literal, other characters still encoded" do
      assert PetstoreClient.ValueSerializer.encode_query_allowing_reserved("v1.0/beta:rc1") ==
               "v1.0/beta:rc1"

      # Space is illegal in a URL and must still be percent-encoded (as %20, not
      # +) even when reserved characters are preserved.
      assert PetstoreClient.ValueSerializer.encode_query_allowing_reserved("a b:c") == "a%20b:c"
    end

    test "bhv serializationparam 060 maybe_allow_reserved wraps only when allowReserved is true" do
      assert PetstoreClient.ValueSerializer.maybe_allow_reserved("plain", false) == "plain"

      assert PetstoreClient.ValueSerializer.maybe_allow_reserved("v1/beta", true) ==
               {:allow_reserved, "v1/beta"}

      assert PetstoreClient.ValueSerializer.maybe_allow_reserved(nil, true) == nil
    end
  end

  test "bhv serialization 015 an object-valued parameter is written with the prefix and separators of its style" do
    object = %{"role" => "admin", "firstName" => "Alex"}

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "filter",
             object,
             :path,
             "object",
             nil,
             "label",
             false
           ) ==
             ".firstName,Alex,role,admin"

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "filter",
             object,
             :path,
             "object",
             nil,
             "label",
             true
           ) ==
             ".firstName=Alex.role=admin"

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "filter",
             object,
             :path,
             "object",
             nil,
             "matrix",
             false
           ) ==
             ";filter=firstName,Alex,role,admin"

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "filter",
             object,
             :path,
             "object",
             nil,
             "matrix",
             true
           ) ==
             ";firstName=Alex;role=admin"

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "filter",
             object,
             :query,
             "object",
             nil,
             "form",
             false
           ) ==
             "firstName,Alex,role,admin"
  end

  test "bhv serialization 016 an object-valued parameter in a delimited style is written as alternating keys and values joined by the delimiter" do
    object = %{"role" => "admin", "firstName" => "Alex"}

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "filter",
             object,
             :query,
             "object",
             nil,
             "pipeDelimited",
             false
           ) ==
             "firstName|Alex|role|admin"

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "filter",
             object,
             :query,
             "object",
             nil,
             "spaceDelimited",
             false
           ) ==
             "firstName Alex role admin"
  end

  test "bhv serializationparam 002 path array item with reserved char is percent encoded" do
    items = ["a/b", "c"]

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "name",
             items,
             :path,
             "array",
             nil,
             "simple",
             false
           ) ==
             "a%2Fb,c"

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "name",
             items,
             :path,
             "array",
             nil,
             "label",
             true
           ) ==
             ".a%2Fb.c"

    assert PetstoreClient.ValueSerializer.serialize_styled(
             "name",
             items,
             :path,
             "array",
             nil,
             "matrix",
             false
           ) ==
             ";name=a%2Fb,c"
  end

  describe "content cookie bytes" do
    test "bhv serializationparam 104 JSON is percent-encoded keeping only the unreserved set literal, and a duration is written in the protobuf form" do
      json = PetstoreClient.ObjectSerializer.serialize(%{"k" => "a b~*é"})

      assert PetstoreClient.ValueSerializer.encode_content_cookie(json) ==
               "%7B%22k%22%3A%22a%20b~%2A%C3%A9%22%7D"

      assert PetstoreClient.ObjectSerializer.stringify(
               Duration.new!(second: 3600, microsecond: {0, 6})
             ) == "3600s"
    end

    test "bhv serializationparam 118 a content cookie value is encoded to the shared percent encoded bytes" do
      encoded = PetstoreClient.ValueSerializer.encode_content_cookie(~s({"k":"a b~*\u00E9"}))

      assert encoded == "%7B%22k%22%3A%22a%20b~%2A%C3%A9%22%7D"
      assert encoded =~ ~r/\A[!\x23-\x2B\x2D-\x3A\x3C-\x5B\x5D-\x7E]*\z/
    end
  end

  describe "query and path encoding rules" do
    test "bhv serialization 107 an exploded array query parameter repeats its key once for each element" do
      assert PetstoreClient.ValueSerializer.serialize(["1", "2"], :query, "array",
               collection_format: :multi
             ) ==
               ["1", "2"]
    end

    test "bhv serialization 111 only RFC 3986 unreserved characters stay literal and every other character is percent-encoded as UTF-8" do
      assert PetstoreClient.ValueSerializer.encode_path_segment("a~b c/é") == "a~b%20c%2F%C3%A9"
    end

    test "bhv serialization 113 a string value passes through unchanged without normalising its form" do
      decomposed = "é"

      assert PetstoreClient.ValueSerializer.serialize(decomposed, :path, "string") == "e%CC%81"
    end

    test "bhv serialization 114 an integer serializes with every digit, including a full 64-bit value" do
      assert PetstoreClient.ValueSerializer.serialize(9_223_372_036_854_775_807, :path, "integer") ==
               "9223372036854775807"
    end

    test "bhv serialization 109 a string path parameter is percent-encoded exactly once in the path" do
      assert PetstoreClient.ValueSerializer.encode_path_segment("a b/c") == "a%20b%2Fc"
    end
  end

  describe "misc wire behaviours" do
    test "bhv misc 082 an enum value is sent as its wire value in a path, a query and a header" do
      for location <- [:path, :query, :header] do
        assert PetstoreClient.ValueSerializer.serialize(:pending, location, "string") == "pending"
      end
    end

    test "bhv misc 084 a set optional scalar is sent in every location" do
      for location <- [:path, :query, :header] do
        assert PetstoreClient.ValueSerializer.serialize(5, location, "integer") == "5"
      end
    end
  end

  describe "misc parity deep object" do
    test "bhv misc 120 an object parameter becomes name and value pairs keyed by wire name, with absent values left out" do
      assert PetstoreClient.ValueSerializer.serialize_deep_object("filter", %{
               "b" => "2",
               "c" => nil,
               "a" => "1"
             }) ==
               %{"filter[a]" => "1", "filter[b]" => "2"}
    end
  end
end
