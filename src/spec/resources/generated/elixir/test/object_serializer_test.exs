# enum-unknown-value-silent-vs-throw test helper. Defined at the top level
# (NOT inside the test module) so it lives at its real
# PetstoreClient.Models.TestEnumStatus address that ObjectSerializer.
# convert_to_type/2 resolves. Defining it inside the test module would
# register a PetstoreClient.Models namespace local to the test and shadow
# real PetstoreClient.Models.* struct references elsewhere in the file.
defmodule PetstoreClient.Models.TestEnumStatus do
  def available, do: :available
  def pending, do: :pending
  def sold, do: :sold
  def all_values, do: [available(), pending(), sold()]

  # Mirror a generated enum's wire->atom inverse (used by ObjectSerializer's
  # atomize_enum). Wire values here equal the atom names, so the match is a
  # case-sensitive 1:1; an unknown/mis-cased value returns {:error, value}.
  def from_value("available"), do: :available
  def from_value("pending"), do: :pending
  def from_value("sold"), do: :sold
  def from_value(value), do: {:error, value}
end

defmodule PetstoreClient.ObjectSerializerTest do
  use ExUnit.Case, async: true

  describe "DateTimeOffsetPreservation" do
    test "bhv serializationobject 174 UTC datetime serializes containing date-time and UTC offset" do
      dt = ~U[2024-01-01 12:30:45Z]
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert result == "2024-01-01T12:30:45.000+00:00"
    end

    test "bhv serializationobject 116 positive timezone offset is preserved in serialized string" do
      # DateTime.from_iso8601/1 normalizes to UTC, so we construct the struct
      # directly to preserve the +05:30 offset in the serialized output.
      dt = %DateTime{
        year: 2024,
        month: 1,
        day: 1,
        hour: 12,
        minute: 30,
        second: 45,
        microsecond: {0, 0},
        utc_offset: 19_800,
        std_offset: 0,
        zone_abbr: "+05:30",
        time_zone: "Etc/UTC+5",
        calendar: Calendar.ISO
      }

      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert result == "2024-01-01T12:30:45.000+05:30"
    end

    test "bhv serializationobject 107 negative timezone offset is preserved in serialized string" do
      # DateTime.from_iso8601/1 normalizes to UTC, so we construct the struct
      # directly to preserve the -08:00 offset in the serialized output.
      dt = %DateTime{
        year: 2024,
        month: 1,
        day: 1,
        hour: 12,
        minute: 30,
        second: 45,
        microsecond: {0, 0},
        utc_offset: -28_800,
        std_offset: 0,
        zone_abbr: "-08:00",
        time_zone: "Etc/UTC-8",
        calendar: Calendar.ISO
      }

      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert result == "2024-01-01T12:30:45.000-08:00"
    end

    test "bhv serializationobject 046 date-time serialization preserves sub-second precision" do
      # 2020-01-02T03:04:05.123Z carries millisecond precision (the cross-SDK
      # common denominator). The encoder must keep the ".123" so the wire form
      # round-trips losslessly with the decoder, which accepts fractions.
      {:ok, dt, _} = DateTime.from_iso8601("2020-01-02T03:04:05.123Z")
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert result == "2020-01-02T03:04:05.123+00:00"

      {:ok, parsed, _} = DateTime.from_iso8601(result)
      assert DateTime.compare(parsed, dt) == :eq
    end

    test "bhv serializationobject 179 whole-second datetime serializes with a fixed three-digit fraction" do
      {:ok, dt, _} = DateTime.from_iso8601("2024-01-01T12:30:45+00:00")
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert result == "2024-01-01T12:30:45.000+00:00"
    end

    test "bhv serializationobject 044 date-only serializes as ISO 8601 date without time component" do
      d = ~D[2024-01-01]
      result = PetstoreClient.ObjectSerializer.stringify(d)
      assert result == "2024-01-01"
    end

    test "bhv serializationobject 149 serialized datetime string contains an offset marker" do
      dt = ~U[2024-01-01 12:30:45Z]
      result = PetstoreClient.ObjectSerializer.stringify(dt)
      assert result == "2024-01-01T12:30:45.000+00:00"
      refute String.ends_with?(result, "Z")
    end

    test "bhv serializationobject 144 round-trip: serialize then deserialize yields equivalent datetime" do
      {:ok, original, _} = DateTime.from_iso8601("2024-01-01T12:30:45+05:30")
      serialized = PetstoreClient.ObjectSerializer.stringify(original)
      {:ok, parsed, _} = DateTime.from_iso8601(serialized)
      assert DateTime.to_unix(original) == DateTime.to_unix(parsed)
    end

    test "bhv serializationobject 045 model date-time field serialization preserves sub-second precision" do
      # Canonical cross-SDK regression: a model carrying a format:date-time
      # field (PhotoMetadata.takenAt) must keep millisecond precision through
      # a full JSON-body serialize, and deserialize back to the same instant.
      {:ok, instant, _} = DateTime.from_iso8601("2020-01-02T03:04:05.123Z")
      model = %PetstoreClient.Models.PhotoMetadata{taken_at: instant}

      json = PetstoreClient.ObjectSerializer.serialize(model)

      assert String.contains?(json, "\"takenAt\":\"2020-01-02T03:04:05.123+00:00\""),
             "milliseconds must survive in body: #{json}"

      decoded = PetstoreClient.ObjectSerializer.deserialize(json, "PhotoMetadata")
      assert DateTime.compare(decoded.taken_at, instant) == :eq
    end

    test "bhv serializationobject 014 a readOnly property is populated on decode" do
      decoded =
        PetstoreClient.ObjectSerializer.deserialize(~s({"id":7,"username":"u"}), "Account")

      assert decoded.id == 7
    end

    test "bhv serializationobject 018 a writeOnly property is written on encode" do
      json =
        PetstoreClient.ObjectSerializer.serialize(%PetstoreClient.Models.Account{
          username: "u",
          password: "p"
        })

      assert Jason.decode!(json)["password"] == "p"
    end

    test "bhv serializationobject 017 a writeOnly property is kept on decode" do
      decoded =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"id":7,"username":"u","password":"p"}),
          "Account"
        )

      assert decoded.password == "p"
    end

    test "prepare_body passes a model without its own serialize/1 through unchanged" do
      account = %PetstoreClient.Models.Account{username: "u", password: "p"}

      assert PetstoreClient.ObjectSerializer.prepare_body(account, PetstoreClient.Models.Account) ==
               account
    end

    test "bhv serializationobject 007 a format: password property is left out of inspect" do
      account = %PetstoreClient.Models.Account{username: "u", password: "hunter2"}

      refute inspect(account) =~ "hunter2"
      assert inspect(account) =~ "username"
    end

    test "multipart model part is serialized through the configured serializer with wire names" do
      # Cross-SDK parity regression: addPetPhotos sends a multipart/form-data
      # body whose `metadata` part is a PhotoMetadata MODEL. That part must go
      # through the SDK's configured ObjectSerializer so it carries WIRE
      # property names (isPrimary / takenAt), NOT the snake_case struct field
      # names (is_primary / taken_at), and the date-time uses the SDK's
      # ISO-8601 format with sub-second precision preserved. We drive the
      # lowest-level multipart-building path (DefaultApiClient.build_multipart_body,
      # the exact function prepare_body/2 uses for multipart bodies) so the
      # assertion captures the genuine outgoing wire body.
      {:ok, instant, _} = DateTime.from_iso8601("2020-01-02T03:04:05.123Z")
      metadata = %PetstoreClient.Models.PhotoMetadata{is_primary: true, taken_at: instant}

      body =
        PetstoreClient.DefaultApiClient.build_multipart_body(
          %{"metadata" => metadata},
          "boundary123"
        )

      # The model part is emitted as an application/json part.
      assert String.contains?(body, "Content-Type: application/json")

      # WIRE property names appear; snake_case struct field names do NOT.
      assert String.contains?(body, "\"isPrimary\":true")
      assert String.contains?(body, "\"takenAt\":")
      refute String.contains?(body, "is_primary"), "must use wire name isPrimary: #{body}"
      refute String.contains?(body, "taken_at"), "must use wire name takenAt: #{body}"

      # The date-time carries the proper ISO-8601 string with milliseconds.
      assert String.contains?(body, "\"takenAt\":\"2020-01-02T03:04:05.123+00:00\""),
             "takenAt must be the wire date-time string: #{body}"
    end
  end

  describe "NonAsciiSerialization" do
    test "bhv serializationobject 019 accented character serializes without unicode escape" do
      result = PetstoreClient.ObjectSerializer.serialize("café")
      assert String.contains?(result, "é"), "should contain literal é: #{result}"
    end

    test "bhv serializationobject 031 CJK characters serialize without unicode escape" do
      result = PetstoreClient.ObjectSerializer.serialize("日本")
      assert String.contains?(result, "日本"), "should contain literal CJK chars: #{result}"
    end

    test "bhv serializationobject 160 tab character is properly escaped in JSON" do
      result = PetstoreClient.ObjectSerializer.serialize("a\tb")
      assert String.contains?(result, "\\t"), "tab should be escaped: #{result}"
    end
  end

  describe "deserialize wraps decode failures" do
    test "bhv serializationobject 170 truncated JSON raises SerializationError" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize("{", "Category")
      end
    end

    test "bhv serializationobject 089 incomplete JSON object raises SerializationError" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize("{\"name\":", "Category")
      end
    end

    # Every wire-shape failure is a SerializationError and part of the
    # OpenAPIError hierarchy: malformed JSON, a wrong primitive type, a
    # missing required field, an unknown enum value and a malformed date-time
    # or duration.
    test "bhv serializationobject 077 every wire-shape failure is a SerializationError" do
      for {json, model} <- [
            {"{", "Pet"},
            {~s({"name":7,"photoUrls":[]}), "Pet"},
            {~s({"photoUrls":[]}), "Pet"},
            {~s({"status":"lost"}), "Order"},
            {~s({"shipDate":"not-a-date"}), "Order"},
            {~s({"retryAfter":"PT1H"}), "EdgeCases"}
          ] do
        err =
          assert_raise PetstoreClient.Errors.SerializationError, fn ->
            PetstoreClient.ObjectSerializer.deserialize(json, model)
          end

        assert PetstoreClient.Errors.OpenAPIError.open_api_error?(err),
               "#{model} #{json} must raise an SDK error"
      end
    end

    # A payload the converter does not check for fails inside a stdlib call (a
    # JSON object cannot be turned into a date-time string); it still surfaces
    # as a SerializationError that carries the original exception as its cause.
    test "an object where a date-time string is expected raises SerializationError with the cause" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(~s({"shipDate":{"a":1}}), "Order")
        end

      assert %Protocol.UndefinedError{protocol: String.Chars} = err.cause

      assert err.message ==
               "Failed to convert to Order: " <> Exception.message(err.cause)
    end

    test "bhv serializationobject 080 a bare object where a date-time string is expected raises SerializationError" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(~s({"a":1}), "DateTime")
        end

      assert %Protocol.UndefinedError{protocol: String.Chars} = err.cause
      assert err.message == "Failed to convert to DateTime: " <> Exception.message(err.cause)
    end

    test "a string where an object model is expected raises, naming the model" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(~s("oops"), "Category")
        end

      assert err.message ==
               "Expected a JSON object for PetstoreClient.Models.Category, got \"oops\""
    end

    test "an array where an object model is expected raises, naming the model" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize("[1,2]", "Category")
        end

      assert err.message ==
               "Expected a JSON object for PetstoreClient.Models.Category, got [1, 2]"
    end

    test "bhv serializationobject 118 a scalar where a nested model is expected raises instead of dropping the field" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(
            ~s({"name":"x","photoUrls":[],"category":5}),
            "Pet"
          )
        end

      assert err.message == "Expected a JSON object for PetstoreClient.Models.Category, got 5"
    end

    test "a string where a set is expected raises instead of passing the raw string" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(
            ~s({"name":"x","photoUrls":"abc"}),
            "Pet"
          )
        end

      assert err.message == "Expected a list for array type, got \"abc\""
    end

    test "a list where a string-keyed map is expected raises" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.convert_to_type([1], "%{String.t() => integer()}")
        end

      assert err.message == "Expected a JSON object for map type, got [1]"
    end

    test "bhv serializationobject 122 a fractional, quoted or boolean element of a map of integers raises" do
      for {json, shown} <- [
            {~s({"a":1.5}), "1.5"},
            {~s({"a":"1"}), "\"1\""},
            {~s({"a":true}), "true"}
          ] do
        err =
          assert_raise PetstoreClient.Errors.SerializationError, fn ->
            PetstoreClient.ObjectSerializer.deserialize(json, "%{String.t() => integer()}")
          end

        assert err.message == "Expected Integer, got " <> shown
      end
    end

    test "a fractional, quoted or boolean leaf of a grid of integers raises" do
      for {json, shown} <- [
            {"[[1,2],[3,2.5]]", "2.5"},
            {"[[1],[\"7\"]]", "\"7\""},
            {"[[false]]", "false"}
          ] do
        err =
          assert_raise PetstoreClient.Errors.SerializationError, fn ->
            PetstoreClient.ObjectSerializer.deserialize(json, "[[integer()]]")
          end

        assert err.message == "Expected Integer, got " <> shown
      end
    end

    test "every integer element of a map and of a grid decodes" do
      assert PetstoreClient.ObjectSerializer.deserialize(
               ~s({"a":1,"b":-2}),
               "%{String.t() => integer()}"
             ) ==
               %{"a" => 1, "b" => -2}

      assert PetstoreClient.ObjectSerializer.deserialize("[[1,2],[3]]", "[[integer()]]") == [
               [1, 2],
               [3]
             ]
    end

    test "bhv serializationobject 162 thrown SerializationError has a message referencing the failure" do
      try do
        PetstoreClient.ObjectSerializer.deserialize("{", "Category")
        flunk("Expected SerializationError to be raised")
      rescue
        e in PetstoreClient.Errors.SerializationError ->
          assert e.message != nil and e.message != ""
      end
    end
  end

  # enum-unknown-value-silent-vs-throw: an out-of-spec enum wire value must
  # raise on deserialize, not be silently retained. The TestEnumStatus
  # helper module is defined at the top of this file (see note there).
  describe "enum unknown value" do
    test "bhv serializationobject 069 known enum value coerces to its atom" do
      assert PetstoreClient.ObjectSerializer.convert_to_type("available", "TestEnumStatus") ==
               :available
    end

    test "bhv serializationobject 172 unknown enum value raises SerializationError" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.convert_to_type("banana", "TestEnumStatus")
      end
    end

    test "mis-cased enum value raises (case-sensitive match)" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.convert_to_type("AVAILABLE", "TestEnumStatus")
      end
    end
  end

  describe "stringify/1" do
    test "bhv serializationobject 110 returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.stringify(nil) == ""
    end

    test "bhv serializationobject 028 returns 'true' for boolean true" do
      assert PetstoreClient.ObjectSerializer.stringify(true) == "true"
    end

    test "bhv serializationobject 027 returns 'false' for boolean false" do
      assert PetstoreClient.ObjectSerializer.stringify(false) == "false"
    end

    test "bhv serializationobject 091 returns string representation of integer" do
      assert PetstoreClient.ObjectSerializer.stringify(42) == "42"
    end

    test "bhv serializationobject 114 passes through plain string unchanged" do
      assert PetstoreClient.ObjectSerializer.stringify("hello") == "hello"
    end

    test "bhv serializationobject 078 returns string representation of float" do
      assert PetstoreClient.ObjectSerializer.stringify(3.14) == "3.14"
    end

    test "bhv serializationobject 134 returns ISO 8601 date string for Date" do
      assert PetstoreClient.ObjectSerializer.stringify(~D[2024-01-15]) == "2024-01-15"
    end
  end

  describe "to_path_value/1" do
    test "bhv serializationobject 130 returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.to_path_value(nil) == ""
    end

    test "bhv serializationobject 141 returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_path_value("hello") == "hello"
    end

    test "bhv serializationobject 036 converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_path_value(42) == "42"
    end

    test "bhv serializationobject 043 converts true to 'true'" do
      assert PetstoreClient.ObjectSerializer.to_path_value(true) == "true"
    end

    test "bhv serializationobject 033 converts false to 'false'" do
      assert PetstoreClient.ObjectSerializer.to_path_value(false) == "false"
    end
  end

  describe "to_query_value/2" do
    test "bhv serializationobject 137 returns nil for nil" do
      assert PetstoreClient.ObjectSerializer.to_query_value(nil) == nil
    end

    test "bhv serializationobject 139 returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_query_value("hello") == "hello"
    end

    test "bhv serializationobject 037 converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_query_value(42) == "42"
    end

    test "bhv serializationobject 041 converts true to 'true'" do
      assert PetstoreClient.ObjectSerializer.to_query_value(true) == "true"
    end

    test "bhv serializationobject 034 converts false to 'false'" do
      assert PetstoreClient.ObjectSerializer.to_query_value(false) == "false"
    end

    test "bhv serializationobject 097 joins array with comma by default" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"]) == "a,b,c"
    end

    test "bhv serializationobject 098 joins array with comma for csv" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :csv) == "a,b,c"
    end

    test "bhv serializationobject 100 joins array with space for ssv" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :ssv) == "a b c"
    end

    test "bhv serializationobject 101 joins array with tab for tsv" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :tsv) == "a\tb\tc"
    end

    test "bhv serializationobject 099 joins array with pipe for pipes" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :pipes) == "a|b|c"
    end

    test "bhv serializationobject 135 returns array as-is for multi" do
      assert PetstoreClient.ObjectSerializer.to_query_value(["a", "b", "c"], :multi) == [
               "a",
               "b",
               "c"
             ]
    end
  end

  describe "to_header_value/1" do
    test "bhv serializationobject 131 returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.to_header_value(nil) == ""
    end

    test "bhv serializationobject 142 returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_header_value("hello") == "hello"
    end

    test "bhv serializationobject 038 converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_header_value(42) == "42"
    end

    test "bhv serializationobject 096 joins array with comma" do
      assert PetstoreClient.ObjectSerializer.to_header_value(["a", "b", "c"]) == "a,b,c"
    end
  end

  describe "to_form_value/1" do
    test "bhv serializationobject 132 returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.to_form_value(nil) == ""
    end

    test "bhv serializationobject 140 returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_form_value("hello") == "hello"
    end

    test "bhv serializationobject 039 converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_form_value(42) == "42"
    end

    test "bhv serializationobject 042 converts true to 'true'" do
      assert PetstoreClient.ObjectSerializer.to_form_value(true) == "true"
    end

    test "bhv serializationobject 035 converts false to 'false'" do
      assert PetstoreClient.ObjectSerializer.to_form_value(false) == "false"
    end
  end

  describe "to_cookie_value/1" do
    test "bhv serializationobject 133 returns empty string for nil" do
      assert PetstoreClient.ObjectSerializer.to_cookie_value(nil) == ""
    end

    test "bhv serializationobject 143 returns the string for a string value" do
      assert PetstoreClient.ObjectSerializer.to_cookie_value("hello") == "hello"
    end

    test "bhv serializationobject 040 converts integer to string" do
      assert PetstoreClient.ObjectSerializer.to_cookie_value(42) == "42"
    end
  end

  describe "serialize/1" do
    test "bhv serializationobject 152 serializes a model to valid JSON" do
      category = %PetstoreClient.Models.Category{id: 1, name: "Dogs"}
      json = PetstoreClient.ObjectSerializer.serialize(category)
      data = Jason.decode!(json)
      assert data["id"] == 1
      assert data["name"] == "Dogs"
    end

    test "bhv serializationobject 024 an inline enum atom outside the declared values raises instead of being sent" do
      pet = %PetstoreClient.Models.Pet{name: "x", photo_urls: [], status: :bogus}

      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.serialize(pet)
        end

      assert err.message ==
               ~s(Unknown enum value "bogus"; allowed values: ["available", "pending", "sold"])
    end

    test "a standalone enum atom outside the declared values raises a SerializationError" do
      item = %PetstoreClient.Models.StockItem{priority: :bogus}

      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.serialize(item)
        end

      assert err.message ==
               "Unknown enum value :bogus for PetstoreClient.Models.Priority"
    end

    test "a declared inline enum atom serializes to its name" do
      pet = %PetstoreClient.Models.Pet{name: "x", photo_urls: [], status: :sold}

      assert Jason.decode!(PetstoreClient.ObjectSerializer.serialize(pet))["status"] == "sold"
    end

    test "bhv serializationobject 015 a model's keys are written in schema declaration order" do
      order = %PetstoreClient.Models.Order{
        id: 1,
        pet_id: 2,
        quantity: 3,
        ship_date: ~U[2024-01-15 10:30:00.000Z],
        status: :approved,
        complete: true
      }

      assert PetstoreClient.ObjectSerializer.serialize(order) ==
               ~s({"id":1,"petId":2,"quantity":3,"shipDate":"2024-01-15T10:30:00.000+00:00","status":"approved","complete":true})
    end

    test "nested models and list elements keep declaration order" do
      order = %PetstoreClient.Models.Order{id: 1, quantity: 3, complete: true}

      assert PetstoreClient.ObjectSerializer.serialize([order]) ==
               ~s([{"id":1,"quantity":3,"status":"placed","complete":true}])

      pet = %PetstoreClient.Models.Pet{
        id: 4,
        name: "n",
        category: %PetstoreClient.Models.Category{id: 1, name: "c"},
        photo_urls: ["u"]
      }

      assert PetstoreClient.ObjectSerializer.serialize(pet) ==
               ~s({"id":4,"name":"n","category":{"id":1,"name":"c"},"photoUrls":["u"]})

      assert PetstoreClient.Models.Order.json_key_order() ==
               ["id", "petId", "quantity", "trackingId", "shipDate", "status", "complete"]
    end

    test "bhv serializationobject 087 handles nil" do
      json = PetstoreClient.ObjectSerializer.serialize(nil)
      assert json == "null"
    end

    test "bhv serializationobject 088 includes fields explicitly set to default values" do
      category = %PetstoreClient.Models.Category{id: 0, name: ""}
      json = PetstoreClient.ObjectSerializer.serialize(category)
      data = Jason.decode!(json)
      assert Map.has_key?(data, "id"), "serialized JSON should include id field"
      assert data["id"] == 0
      assert Map.has_key?(data, "name"), "serialized JSON should include name field"
      assert data["name"] == ""
    end
  end

  describe "inline enum with non-identifier wire values (EdgeCases.phase)" do
    test "bhv serializationobject 161 each wire value decodes to a distinct member atom and re-encodes unchanged" do
      for {wire, member} <- [
            {"In-Stock", :in_stock},
            {"on-hold", :on_hold},
            {"OUT_OF_STOCK", :out_of_stock}
          ] do
        decoded =
          PetstoreClient.ObjectSerializer.deserialize(
            Jason.encode!(%{"phase" => wire}),
            "EdgeCases"
          )

        assert decoded.phase == member
        assert Jason.decode!(PetstoreClient.ObjectSerializer.serialize(decoded))["phase"] == wire
      end
    end

    test "bhv serializationobject 076 the member name is not accepted as a wire value" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(~s({"phase":"on_hold"}), "EdgeCases")
        end

      assert err.message ==
               ~s(Unknown enum value "on_hold"; allowed values: ["In-Stock", "on-hold", "OUT_OF_STOCK"])
    end

    test "a member atom set by the caller is sent as its wire value" do
      edge = %PetstoreClient.Models.EdgeCases{phase: :on_hold}

      assert Jason.decode!(PetstoreClient.ObjectSerializer.serialize(edge))["phase"] == "on-hold"
    end
  end

  describe "format: byte (ByteArray)" do
    test "decodes base64 wire value to raw bytes" do
      bytes = PetstoreClient.ObjectSerializer.convert_to_type("aGVsbG8=", "ByteArray")
      assert bytes == "hello"
    end

    test "bhv serializationobject 102 rejects malformed base64 with SerializationError" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.convert_to_type("not-base64!!!", "ByteArray")
      end
    end

    test "bhv serializationobject 153 rejects non-string payload with SerializationError" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.convert_to_type(123, "ByteArray")
      end
    end

    test "round-trip via base64 encoding" do
      original = <<1, 2, 3, 255, 0, 128>>
      encoded = Base.encode64(original)
      decoded = PetstoreClient.ObjectSerializer.convert_to_type(encoded, "ByteArray")
      assert decoded == original
    end
  end

  describe "BinaryVault byte-array equality" do
    defp decode_vault(seal, shards, labels) do
      json = Jason.encode!(%{"seal" => seal, "shards" => shards, "labels" => labels})
      PetstoreClient.ObjectSerializer.deserialize(json, "BinaryVault")
    end

    test "bhv serializationobject 090 two vaults decoded from equal bytes are equal, whatever the shape" do
      left = decode_vault("c2VhbA==", ["YQ==", "Yg=="], %{"k" => "YQ=="})
      right = decode_vault("c2VhbA==", ["YQ==", "Yg=="], %{"k" => "YQ=="})

      assert left.seal == "seal"
      assert left.shards == ["a", "b"]
      assert left.labels == %{"k" => "a"}
      assert left == right
    end

    test "bhv serializationobject 075 a different byte in the bare array, the list or the map makes the vaults differ" do
      base = decode_vault("c2VhbA==", ["YQ==", "Yg=="], %{"k" => "YQ=="})

      refute base == decode_vault("eA==", ["YQ==", "Yg=="], %{"k" => "YQ=="})
      refute base == decode_vault("c2VhbA==", ["YQ==", "eA=="], %{"k" => "YQ=="})
      refute base == decode_vault("c2VhbA==", ["YQ==", "Yg=="], %{"k" => "eA=="})
    end

    test "bhv serializationobject 009 a list of byte arrays is distinct from a map of them" do
      listed = decode_vault("c2VhbA==", ["YQ=="], %{})
      mapped = decode_vault("c2VhbA==", [], %{"0" => "YQ=="})

      refute listed == mapped
    end
  end

  describe "encode_base64_param/1" do
    test "bhv serializationobject 156 encodes bytes as padded standard-alphabet base64" do
      assert PetstoreClient.ObjectSerializer.encode_base64_param("hi") == "aGk="
      assert PetstoreClient.ObjectSerializer.encode_base64_param(<<0xFB, 0xFF>>) == "+/8="
    end

    test "encodes each element of a list or set and keeps nil as nil" do
      assert PetstoreClient.ObjectSerializer.encode_base64_param(["hi", "yo"]) == ["aGk=", "eW8="]
      assert PetstoreClient.ObjectSerializer.encode_base64_param(MapSet.new(["hi"])) == ["aGk="]
      assert PetstoreClient.ObjectSerializer.encode_base64_param(nil) == nil
    end
  end

  describe "format: uuid (UUID)" do
    test "bhv serializationobject 175 accepts canonical lower-case UUID" do
      uuid = "550e8400-e29b-41d4-a716-446655440000"
      assert PetstoreClient.ObjectSerializer.convert_to_type(uuid, "UUID") == uuid
    end

    test "accepts upper-case UUID" do
      uuid = "550E8400-E29B-41D4-A716-446655440000"

      assert PetstoreClient.ObjectSerializer.convert_to_type(uuid, "UUID") ==
               String.downcase(uuid)
    end

    test "bhv serializationobject 148 encode_uuid_param writes lowercase and rejects malformed values" do
      assert PetstoreClient.ObjectSerializer.encode_uuid_param(
               "550E8400-E29B-41D4-A716-446655440000"
             ) ==
               "550e8400-e29b-41d4-a716-446655440000"

      assert PetstoreClient.ObjectSerializer.encode_uuid_param(nil) == nil

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.encode_uuid_param("not-a-uuid")
      end
    end

    test "bhv serializationobject 048 rejects malformed UUID" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.convert_to_type("not-a-uuid", "UUID")
      end
    end

    test "rejects UUID with wrong segment lengths" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.convert_to_type(
          "550e8400-e29b-41d4-a716-44665544",
          "UUID"
        )
      end
    end

    test "rejects non-string payload" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.convert_to_type(42, "UUID")
      end
    end
  end

  describe "format: uri (URI)" do
    test "bhv serializationobject 021 accepts absolute URIs of any scheme and returns the original string" do
      for uri <- [
            "https://example.com/a?b=c",
            "ftp://x.org/f",
            "mailto:a@b.com",
            "urn:isbn:0451450523"
          ] do
        assert PetstoreClient.ObjectSerializer.convert_to_type(uri, "URI") == uri
      end
    end

    test "bhv serializationobject 008 rejects relative references, blanks and whitespace" do
      for bad <- ["/relative", "", " ", "http://a b", "http://a\nb", "no scheme"] do
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.convert_to_type(bad, "URI")
        end
      end
    end

    test "rejects a non-string payload" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.convert_to_type(42, "URI")
      end
    end
  end

  describe "deserialize/2" do
    test "bhv serializationobject 070 deserializes JSON to typed model" do
      json_str = ~s({"id":1,"name":"Dogs"})
      category = PetstoreClient.ObjectSerializer.deserialize(json_str, "Category")
      assert %PetstoreClient.Models.Category{} = category
      assert category.id == 1
      assert category.name == "Dogs"
    end

    # P2 (BOM tolerance): RFC 8259 §8.1 forbids a leading UTF-8 BOM in JSON
    # text and Jason rejects it, but Windows-generated payloads often include
    # one. deserialize/2 strips it transparently (parity with Java Jackson /
    # C# System.Text.Json), so a BOM-prefixed body still yields a typed model.
    test "bhv serializationobject 060 strips a leading UTF-8 BOM before deserializing" do
      json_str = <<0xEF, 0xBB, 0xBF>> <> ~s({"id":1,"name":"Dogs"})
      category = PetstoreClient.ObjectSerializer.deserialize(json_str, "Category")
      assert %PetstoreClient.Models.Category{} = category
      assert category.id == 1
      assert category.name == "Dogs"
    end

    test "bhv serializationobject 121 rejects a null element in an array of models" do
      for type <- ["[Category]", "Array<Category>"] do
        error =
          assert_raise PetstoreClient.Errors.SerializationError, fn ->
            PetstoreClient.ObjectSerializer.deserialize(~s([null,{"id":1,"name":"Dogs"}]), type)
          end

        assert error.message == "Expected an object for Category, got null"
      end
    end

    test "bhv serializationobject 123 rejects a null value in a map of models" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(~s({"a":null}), "%{String.t() => Category}")
      end
    end

    test "keeps a null element in an array of primitives" do
      assert PetstoreClient.ObjectSerializer.deserialize(~s(["a",null]), "[String.t()]") == [
               "a",
               nil
             ]
    end

    test "bhv serializationobject 136 returns nil for empty input" do
      assert PetstoreClient.ObjectSerializer.deserialize("", "Category") == nil
    end

    test "bhv serializationobject 138 returns nil for nil input" do
      assert PetstoreClient.ObjectSerializer.deserialize(nil, "Category") == nil
    end

    # Defaults contract (canonical across all 12 SDKs): a schema `default`
    # applies ONLY when the property is absent from the payload. An explicit
    # JSON null is a provided value and is preserved (the field stays nil),
    # never replaced by the default. The Defaults model declares
    # retries (int, default 3), mode (enum, default medium — not the first
    # variant), and label (nullable string, default "untitled").
    test "bhv serializationobject 025 deserialize applies defaults for an empty object" do
      defaults = PetstoreClient.ObjectSerializer.deserialize(~s({}), "Defaults")
      assert %PetstoreClient.Models.Defaults{} = defaults
      assert defaults.retries == 3
      assert defaults.mode == :medium
      assert defaults.label == "untitled"
      assert defaults.greeting == "it's \"quoted\" \\ back\tslash\nnext $USD \#{x} a*/b"
    end

    test "bhv serializationobject 111 deserialize renders the numeric defaults as their exact typed values" do
      defaults = PetstoreClient.ObjectSerializer.deserialize(~s({}), "Defaults")
      assert defaults.ratio === 1.5
      assert defaults.scale === 0.25
      assert defaults.ceiling === 9_000_000_000
    end

    test "bhv serializationobject 056 deserialize preserves an explicit null over the default" do
      json_str = ~s({"label":null,"retries":7})
      defaults = PetstoreClient.ObjectSerializer.deserialize(json_str, "Defaults")
      assert %PetstoreClient.Models.Defaults{} = defaults
      # label is present-but-null on the wire: the null is preserved, NOT
      # replaced by the "untitled" default (absent-vs-null distinction).
      assert defaults.label == nil
      assert defaults.retries == 7
    end

    # Recursive self-referential model: TreeNode.child is a $ref back to
    # TreeNode. Deserialization must decode the nested child into a typed
    # TreeNode struct (not a raw map) at every level, with the absent
    # grandchild left nil.
    test "bhv serializationobject 068 deserializes a self-referential TreeNode and decodes the nesting" do
      json_str = ~s({"value":"root","child":{"value":"leaf"}})
      top = PetstoreClient.ObjectSerializer.deserialize(json_str, "TreeNode")
      assert %PetstoreClient.Models.TreeNode{} = top
      assert top.value == "root"
      assert %PetstoreClient.Models.TreeNode{} = top.child
      assert top.child.value == "leaf"
      assert top.child.child == nil
    end
  end

  # default-on-deserialize: Order.status carries a schema `default: placed`.
  # When the wire payload omits "status", deserialize must fall back to that
  # default rather than leaving the field nil — the struct's `status: :placed`
  # default survives because the reducer skips absent keys. status is an enum,
  # so the default is the atom `:placed`, identical to the value the
  # deserializer produces for a present "status" wire string. Canonical across
  # the product; the asserted value is the schema default placed.
  test "bhv serializationobject 053 deserialize applies schema default for absent field" do
    json_str = ~s({"id":10,"petId":198772})
    order = PetstoreClient.ObjectSerializer.deserialize(json_str, "Order")
    assert %PetstoreClient.Models.Order{} = order
    assert order.status == :placed
  end

  # Divergence #10: deserialization must hard-fail when a required,
  # non-nullable field is missing or explicitly null, instead of silently
  # building a partial struct. Pet declares `name` and `photoUrls` as
  # required non-nullable fields. Optional fields (id, status, ...) stay
  # untouched. Canonical across the product; parity with Go/PHP/Kotlin/
  # Swift/Rust which all reject a missing/null required field.
  describe "required field strictness (Divergence #10)" do
    test "bhv serializationobject 061 deserializing a complete object still succeeds" do
      json = ~s({"name":"Rex","photoUrls":["http://example.com/rex.jpg"]})
      pet = PetstoreClient.ObjectSerializer.deserialize(json, "Pet")
      assert %PetstoreClient.Models.Pet{} = pet
      assert pet.name == "Rex"
      assert pet.photo_urls == MapSet.new(["http://example.com/rex.jpg"])
    end

    test "bhv serializationobject 063 missing required field raises SerializationError" do
      # photoUrls (required, non-nullable) is absent.
      json = ~s({"name":"Rex"})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "Pet")
      end
    end

    test "bhv serializationobject 084 explicitly-null required field raises SerializationError" do
      json = ~s({"name":"Rex","photoUrls":null})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "Pet")
      end
    end

    test "bhv serializationobject 062 null on a required field other than the first also raises" do
      # Guard against only the first required field being checked: here
      # `name` is null while `photoUrls` is present and valid.
      json = ~s({"name":null,"photoUrls":["http://example.com/rex.jpg"]})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "Pet")
      end
    end

    test "omitting an optional field does not raise" do
      # `status` is optional; its absence must not trip the required check.
      json = ~s({"name":"Rex","photoUrls":["http://example.com/rex.jpg"]})
      pet = PetstoreClient.ObjectSerializer.deserialize(json, "Pet")
      assert pet.status == nil
    end

    # Gap AJ: JSON null on a required, non-nullable field. The canonical
    # cross-SDK payload `{"name":null,"photoUrls":["u"]}` (name required +
    # non-nullable, present-but-null) MUST raise the SDK's deserialization
    # error. go zero-inits (""), python (pydantic) accepts, kotlin
    # (explicitNulls=false) accepts → silent contract violation; the other 9
    # (elixir among them) throw. Canonical = throw. GREEN in elixir — the
    # assert_required_present guard rejects an explicitly-null required field.
    test "bhv serializationobject 086 Gap AJ: null on required non-nullable name raises SerializationError" do
      json = ~s({"name":null,"photoUrls":["u"]})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "Pet")
      end
    end
  end

  describe "required and nullable property" do
    test "bhv serializationobject 049 an explicit null decodes to nil" do
      model = PetstoreClient.ObjectSerializer.deserialize(~s({"note":null}), "RequiredNullable")
      assert model.note == nil
    end

    test "a value decodes as given" do
      model = PetstoreClient.ObjectSerializer.deserialize(~s({"note":"x"}), "RequiredNullable")
      assert model.note == "x"
    end

    test "bhv serializationobject 126 an absent key raises SerializationError" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize("{}", "RequiredNullable")
      end
    end

    test "bhv serializationobject 125 a nil value is written as an explicit null" do
      model = %PetstoreClient.Models.RequiredNullable{note: nil}
      assert PetstoreClient.ObjectSerializer.serialize(model) == ~s({"note":null})
    end

    test "a value is written as given" do
      model = %PetstoreClient.Models.RequiredNullable{note: "x"}
      assert PetstoreClient.ObjectSerializer.serialize(model) == ~s({"note":"x"})
    end
  end

  describe "format: time round-tripping" do
    test "stringify emits HH:MM:SS for a Time" do
      assert PetstoreClient.ObjectSerializer.stringify(~T[13:45:30]) == "13:45:30"
    end

    test "bhv serializationobject 164 stringify keeps a non-zero millisecond part and truncates finer precision" do
      assert PetstoreClient.ObjectSerializer.stringify(~T[10:30:00.123456]) == "10:30:00.123"
      assert PetstoreClient.ObjectSerializer.stringify(~T[10:30:00.9996]) == "10:30:00.999"
    end

    test "stringify drops a zero fractional part" do
      assert PetstoreClient.ObjectSerializer.stringify(~T[10:30:00.000000]) == "10:30:00"
      assert PetstoreClient.ObjectSerializer.stringify(~T[10:30:00.000999]) == "10:30:00"
    end

    test "serialize keeps the millisecond part of a Time inside a map" do
      json = PetstoreClient.ObjectSerializer.serialize(%{"opens_at" => ~T[09:00:00.250]})
      assert Jason.decode!(json)["opens_at"] == "09:00:00.250"
    end

    test "bhv serializationobject 005 serialize emits HH:MM:SS for a Time inside a map" do
      json = PetstoreClient.ObjectSerializer.serialize(%{"opens_at" => ~T[09:00:00]})
      assert Jason.decode!(json)["opens_at"] == "09:00:00"
    end
  end

  describe "format: duration round-tripping" do
    test "bhv serializationobject 158 stringify emits canonical protobuf duration for a Duration" do
      d = Duration.new!(hour: 1, minute: 30)
      assert PetstoreClient.ObjectSerializer.stringify(d) == "5400s"
    end

    test "bhv serializationobject 146 serialize emits the canonical duration string inside a map" do
      d = Duration.new!(minute: 5)
      json = PetstoreClient.ObjectSerializer.serialize(%{"ttl" => d})
      assert Jason.decode!(json)["ttl"] == "300s"
    end
  end

  # Integer-backed enum (Priority) must round-trip as a JSON NUMBER, not a
  # quoted string. Priority is `type: integer` with enum [1, 2, 3]; the
  # generated module maps atom -> int via value/1 and int -> atom via
  # from_value/1. Serialize must emit the bare integer (2, never "2") and
  # deserialize must invert it back to the member atom.
  describe "integer-backed enum (Priority) round-trips as a JSON number" do
    test "bhv serializationobject 002 serialize emits the bare integer wire value" do
      # StockItem.priority is typed "Priority", so sanitize routes the atom
      # through Priority.value/1 and emits the int. matrix is omitted (nil)
      # so only the required priority field surfaces.
      item = %PetstoreClient.Models.StockItem{priority: :number_2}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(item))
      assert data["priority"] == 2
      assert is_integer(data["priority"])
      refute data["priority"] == "2"
    end

    test "bhv serializationobject 092 deserialize maps the JSON number back to the member atom" do
      json = ~s({"priority":2})
      item = PetstoreClient.ObjectSerializer.deserialize(json, "StockItem")
      assert %PetstoreClient.Models.StockItem{} = item
      assert item.priority == :number_2
    end

    test "convert_to_type inverts the integer wire value via from_value" do
      assert PetstoreClient.ObjectSerializer.convert_to_type(3, "Priority") == :number_3
    end
  end

  # Non-lowercase string enum (Availability) must round-trip with its wire
  # casing preserved: "Available", "Sold", and the hyphenated "on-hold".
  # The atom names are sanitized (:available / :sold / :on_hold) but value/1
  # restores the exact wire string and from_value/1 inverts it. An unknown
  # wire value is spec drift and must raise.
  describe "non-lowercase string enum (Availability) round-trips with wire casing" do
    test "serialize restores the exact non-lowercase wire string" do
      item = %PetstoreClient.Models.StockItem{priority: :number_1, availability: :available}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(item))
      assert data["availability"] == "Available"
    end

    test "serialize restores the hyphenated wire string" do
      item = %PetstoreClient.Models.StockItem{priority: :number_1, availability: :on_hold}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(item))
      assert data["availability"] == "on-hold"
    end

    test "deserialize maps the wire casing back to the member atom" do
      item =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"priority":1,"availability":"Available"}),
          "StockItem"
        )

      assert item.availability == :available
    end

    test "convert_to_type inverts each wire value via from_value" do
      assert PetstoreClient.ObjectSerializer.convert_to_type("Available", "Availability") ==
               :available

      assert PetstoreClient.ObjectSerializer.convert_to_type("Sold", "Availability") == :sold

      assert PetstoreClient.ObjectSerializer.convert_to_type("on-hold", "Availability") ==
               :on_hold
    end
  end

  # Scenario 10: a model field typed as a REFERENCED enum component must reject
  # an unknown wire value on deserialize rather than mint an arbitrary atom.
  # StockItem.availability is a $ref to the Availability enum; an out-of-spec
  # value reaches atomize_enum via the field's declared type and raises.
  describe "referenced enum field rejects unknown wire value" do
    test "bhv serializationobject 059 unknown Availability wire value on a model field raises SerializationError" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"priority":1,"availability":"Discontinued"}),
          "StockItem"
        )
      end
    end

    test "unknown Priority wire value on a model field raises SerializationError" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(~s({"priority":99}), "StockItem")
      end
    end
  end

  # StockItem.availability is a $ref to the Availability enum with a default
  # written beside it (an OpenAPI 3.1 sibling keyword): the property starts at
  # that default when the struct is built and when the key is absent on decode,
  # and an explicit value on the wire replaces it.
  describe "a $ref property with a sibling default starts at that default" do
    test "building the struct without the property gives the default" do
      item = %PetstoreClient.Models.StockItem{priority: :number_1}
      assert item.availability == :on_hold
    end

    test "bhv serializationobject 050 decoding a payload without the property gives the default" do
      item = PetstoreClient.ObjectSerializer.deserialize(~s({"priority":1}), "StockItem")
      assert item.availability == :on_hold
    end

    test "decoding an explicit value replaces the default" do
      item =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"priority":1,"availability":"Sold"}),
          "StockItem"
        )

      assert item.availability == :sold
    end
  end

  # Scenario 7: a nested container property (array-of-array<int>) must
  # deep-round-trip to typed integer leaves, not stop at the outer list.
  # StockItem.matrix is typed "[[integer()]]"; convert_to_type peels each
  # container level so every leaf is a strict integer.
  describe "nested container (array-of-array int) deep round-trips" do
    test "bhv serializationobject 108 deserialize decodes every leaf to a typed integer" do
      json = ~s({"priority":1,"matrix":[[1,2],[3,4,5]]})
      item = PetstoreClient.ObjectSerializer.deserialize(json, "StockItem")
      assert item.matrix == [[1, 2], [3, 4, 5]]
      assert Enum.all?(List.flatten(item.matrix), &is_integer/1)
    end

    test "serialize re-emits the nested integer grid" do
      item = %PetstoreClient.Models.StockItem{priority: :number_1, matrix: [[7, 8], [9]]}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(item))
      assert data["matrix"] == [[7, 8], [9]]
    end
  end

  # Scenario 4: a double/number-typed field must deserialize from an INTEGRAL
  # JSON value (e.g. {"lat":5}, not 5.0) without crashing. PhotoMetadataLocation
  # lat/lng are typed "float()"; convert_to_type's int->float clause coerces a
  # JSON integer into a float so a server emitting a whole number does not trip
  # the strict primitive guard.
  describe "double field deserializes from an integral JSON value" do
    test "integral lat/lng coerce to floats" do
      json = ~s({"lat":5,"lng":-8})
      loc = PetstoreClient.ObjectSerializer.deserialize(json, "PhotoMetadataLocation")
      assert %PetstoreClient.Models.PhotoMetadataLocation{} = loc
      assert loc.lat == 5.0
      assert is_float(loc.lat)
      assert loc.lng == -8.0
      assert is_float(loc.lng)
    end

    test "convert_to_type coerces a bare integer to a float" do
      assert PetstoreClient.ObjectSerializer.convert_to_type(5, "float()") == 5.0
    end
  end

  # Scenario 5: additionalProperties round-trip. An undeclared wire key is
  # captured on deserialize into the struct's additional_properties map and
  # re-emitted at the TOP level on serialize (never nested under an
  # "additionalProperties"/"additional_properties" key). Metadata declares
  # additional_properties/0 == true with a single declared field createdAt.
  describe "additionalProperties round-trip at the top level" do
    test "bhv serializationobject 058 deserialize captures an undeclared key into additional_properties" do
      json = ~s({"createdAt":"2024-01-01T00:00:00Z","region":"eu","weight":42})
      meta = PetstoreClient.ObjectSerializer.deserialize(json, "Metadata")
      assert %PetstoreClient.Models.Metadata{} = meta
      assert meta.additional_properties["region"] == "eu"
      assert meta.additional_properties["weight"] == 42
    end

    test "bhv serializationobject 020 serialize re-emits the extra key at the top level" do
      meta = %PetstoreClient.Models.Metadata{additional_properties: %{"region" => "eu"}}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(meta))
      assert data["region"] == "eu"
      refute Map.has_key?(data, "additionalProperties")
      refute Map.has_key?(data, "additional_properties")
    end

    test "an extra key survives a full deserialize -> serialize round-trip" do
      json = ~s({"region":"eu"})
      meta = PetstoreClient.ObjectSerializer.deserialize(json, "Metadata")
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(meta))
      assert data["region"] == "eu"
      refute Map.has_key?(data, "additional_properties")
    end
  end

  # Scenario 3 (array-of-byte): a format:byte ARRAY field must round-trip
  # through base64 just like a scalar byte field. PetPassport.scans is typed
  # "[binary()]"; each element decodes from base64 on deserialize and re-encodes
  # on serialize, symmetric with the scalar ByteArray path tested above.
  describe "format: byte array field round-trips through base64" do
    test "deserialize decodes each base64 scan element to raw bytes" do
      raw_a = <<1, 2, 3>>
      raw_b = <<255, 0, 128>>

      json =
        Jason.encode!(%{"scans" => [Base.encode64(raw_a), Base.encode64(raw_b)]})

      passport = PetstoreClient.ObjectSerializer.deserialize(json, "PetPassport")
      assert %PetstoreClient.Models.PetPassport{} = passport
      assert passport.scans == [raw_a, raw_b]
    end

    test "serialize re-encodes each raw scan element to base64" do
      raw = <<10, 20, 30>>
      passport = %PetstoreClient.Models.PetPassport{scans: [raw]}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(passport))
      assert data["scans"] == [Base.encode64(raw)]
    end

    test "bhv serializationobject 079 scalar and array byte fields round-trip together" do
      thumb = <<0, 1, 2>>
      scan = <<3, 4, 5>>
      passport = %PetstoreClient.Models.PetPassport{thumbnail: thumb, scans: [scan]}
      json = PetstoreClient.ObjectSerializer.serialize(passport)
      decoded = PetstoreClient.ObjectSerializer.deserialize(json, "PetPassport")
      assert decoded.thumbnail == thumb
      assert decoded.scans == [scan]
    end
  end

  # ── Gap K — discriminator auto-injection on subtype serialize ──

  describe "discriminator auto-injection" do
    test "bhv serializationobject 072 dry subtype serialize auto-emits the discriminator" do
      # The discriminator is an enforced key, so the caller always supplies it
      # and it is the value that reaches the wire.
      dry = %PetstoreClient.Models.DryFood{weight_kg: 2.5, food_type: "dry"}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(dry))
      assert data["foodType"] == "dry"
      assert data["weightKg"] == 2.5
    end

    test "bhv serializationobject 178 wet subtype serialize auto-emits the discriminator" do
      wet = %PetstoreClient.Models.WetFood{volume_ml: 350, food_type: "wet"}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(wet))
      assert data["foodType"] == "wet"
    end

    test "bhv serializationobject 055 deserializing the oneOf parent routes to the subtype by discriminator" do
      # A discriminator-tagged payload deserialized against the parent
      # PetFood oneOf must come back as the concrete DryFood subtype, not
      # the raw map — PetFood.build resolves foodType="dry" to DryFood.
      json = ~s({"foodType":"dry","weightKg":2.5})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      assert %PetstoreClient.Models.DryFood{} = result
      assert result.weight_kg == 2.5
    end

    # Gap AU-residual: a PetFood payload missing the `foodType` discriminator
    # property MUST raise rather than wrap the raw map in a union container.
    # python/php wrap (silent contract violation); the other 10 (elixir among
    # them) throw. Canonical = throw. GREEN in elixir — PetFood.build raises
    # SerializationError ("Missing discriminator 'foodType' for PetFood") when
    # the discriminator value is absent, before any subtype can be resolved.
    test "bhv serializationobject 085 Gap AU-resid: missing discriminator on PetFood raises" do
      json = ~s({"weightKg":5.0})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      end
    end
  end

  # ── Gap #13 — null fields omitted on serialize ──

  describe "nil discard on serialize" do
    test "bhv serializationobject 023 omits model fields that are nil" do
      # Category's id is optional; left nil it must be dropped from the
      # payload rather than emitted as "id": null.
      category = %PetstoreClient.Models.Category{id: nil, name: "Dogs"}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(category))
      refute Map.has_key?(data, "id")
      assert data["name"] == "Dogs"
    end
  end

  # type:number (no format) field must serialize as an UNQUOTED JSON number,
  # never a quoted string. Pet.weightKg is `type: number` with no `format`;
  # the Elixir model types it as a native `float()`, so sanitize emits the bare
  # value and Jason encodes `1.5` (not "1.5"). Cross-SDK parity regression: the
  # python/node SDKs modelled this as an arbitrary-precision Decimal and emitted
  # a quoted string, diverging from the spec's `type: number`. This SDK is
  # correct because it carries the value as a plain float through the wire.
  describe "type:number field serializes as an unquoted JSON number" do
    test "bhv serializationobject 171 weightKg set to 1.5 emits a bare JSON number, not a quoted string" do
      pet = %PetstoreClient.Models.Pet{
        name: "Rex",
        photo_urls: ["http://example.com/rex.jpg"],
        weight_kg: 1.5
      }

      json = PetstoreClient.ObjectSerializer.serialize(pet)

      # Raw wire form: unquoted number, never the quoted-string spelling.
      assert String.contains?(json, "\"weightKg\":1.5"),
             "weightKg must be an unquoted number: #{json}"

      refute String.contains?(json, "\"weightKg\":\"1.5\""),
             "weightKg must NOT be a quoted string: #{json}"

      # Decoded back, the value is a JSON number (float), not a string.
      data = Jason.decode!(json)
      assert data["weightKg"] == 1.5
      assert is_float(data["weightKg"])
    end

    test "bhv serializationobject 177 weightKg survives a serialize -> deserialize round-trip as a number" do
      pet = %PetstoreClient.Models.Pet{
        name: "Rex",
        photo_urls: ["http://example.com/rex.jpg"],
        weight_kg: 1.5
      }

      json = PetstoreClient.ObjectSerializer.serialize(pet)
      decoded = PetstoreClient.ObjectSerializer.deserialize(json, "Pet")

      assert %PetstoreClient.Models.Pet{} = decoded
      assert decoded.weight_kg == 1.5
      assert is_float(decoded.weight_kg)
    end
  end

  describe "oneOf/anyOf no-match" do
    test "bhv serializationobject 128 resolve_one_of returns the first matching variant" do
      candidates = [
        fn _ -> raise "variant A does not match" end,
        fn data -> {:matched, data} end
      ]

      result = PetstoreClient.ObjectSerializer.resolve_one_of(%{"k" => "v"}, candidates)
      assert result == {:matched, %{"k" => "v"}}
    end

    test "bhv serializationobject 129 resolve_one_of raises when no variant matches" do
      # A payload matching none of the declared variants is a contract violation
      # and must fail loudly rather than be silently returned as nil.
      candidates = [
        fn _ -> raise "variant A does not match" end,
        fn _ -> raise "variant B does not match" end
      ]

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.resolve_one_of(%{"unexpected" => true}, candidates)
      end
    end

    test "bhv serializationobject 127 resolve_any_of raises when no variant matches" do
      candidates = [fn _ -> raise "no match" end]

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.resolve_any_of(%{}, candidates)
      end
    end

    test "match_any_of reports no match without raising" do
      candidates = [fn _ -> raise "no match" end]
      assert PetstoreClient.ObjectSerializer.match_any_of(%{}, candidates) == :no_match
    end
  end

  # A wrong primitive type, and a malformed date, date-time, time or duration,
  # is a SerializationError. None of them is passed through as the raw value.
  describe "wire-shape failures raise SerializationError" do
    test "bhv serializationobject 180 wrong primitive types" do
      for {value, type} <- [
            {42, "String"},
            {"42", "Integer"},
            {"4.2", "Float"},
            {"true", "Boolean"},
            {"not-a-list", "[String]"}
          ] do
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.convert_to_type(value, type)
        end
      end
    end

    test "malformed temporal values" do
      for {value, type} <- [
            {"not-a-date", "Date"},
            {"2024-13-45", "Date.t()"},
            {"not-a-date-time", "DateTime"},
            {"2024-01-01", "DateTime.t()"},
            {"25:99:00", "Time"},
            {"PT1H", "Duration"},
            {"3600", "Duration.t()"}
          ] do
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.convert_to_type(value, type)
        end
      end
    end
  end

  # ── canonical serde behaviours (cross-SDK) ──
  #
  # A bare JSON null body decodes to nil, and the 1000-deep JSON nesting cap
  # rejects an over-deep payload. Invalid base64 on a format:byte field is
  # covered above; a non-finite float is not representable on the BEAM (Elixir
  # floats are always finite), so NaN/Infinity-on-encode does not apply here.
  describe "canonical serde behaviours" do
    test "bhv serializationobject 067 deserializes a literal null body to nil" do
      assert PetstoreClient.ObjectSerializer.deserialize("null", "Category") == nil
    end

    test "bhv serializationobject 051 decodes a payload within the depth cap but rejects one past it" do
      within = String.duplicate("[", 600) <> String.duplicate("]", 600)
      assert PetstoreClient.ObjectSerializer.deserialize(within, "Object") != nil

      beyond = String.duplicate("[", 1500) <> String.duplicate("]", 1500)

      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(beyond, "Object")
        end

      assert err.message ==
               "Failed to deserialize JSON: JSON nesting depth 1500 exceeds limit 1000"
    end

    test "bhv serializationobject 004 a payload that is not JSON raises with the parser's detail" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize("{not json", "Object")
        end

      assert err.message =~ "Failed to deserialize JSON: "
    end

    test "a value the encoder rejects raises Failed to serialize object to JSON" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.serialize(%{"bytes" => <<255>>})
        end

      assert err.message =~ "Failed to serialize object to JSON: "
    end
  end

  describe "fixture rows every SDK pins (EdgeCases, Order, Availability, Pet)" do
    test "the escape-sensitive Availability members serialize to their wire strings and decode back" do
      for {atom, wire} <- [oclock: "o'clock", a_b: "a\\b", eq: "$eq", x: "\#{x}"] do
        item = %PetstoreClient.Models.StockItem{priority: :number_1, availability: atom}
        data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(item))
        assert data["availability"] == wire

        decoded =
          PetstoreClient.ObjectSerializer.deserialize(
            Jason.encode!(%{"priority" => 1, "availability" => wire}),
            "StockItem"
          )

        assert decoded.availability == atom
      end
    end

    test "EdgeCases.cutoff (format: time) keeps its milliseconds on the wire and decodes back" do
      edge = %PetstoreClient.Models.EdgeCases{cutoff: ~T[10:30:00.123456]}
      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(edge))
      assert data["cutoff"] == "10:30:00.123"

      whole = %PetstoreClient.Models.EdgeCases{cutoff: ~T[10:30:00]}

      assert Jason.decode!(PetstoreClient.ObjectSerializer.serialize(whole))["cutoff"] ==
               "10:30:00"

      decoded =
        PetstoreClient.ObjectSerializer.deserialize(~s({"cutoff":"10:30:00.123"}), "EdgeCases")

      assert Time.compare(decoded.cutoff, ~T[10:30:00.123]) == :eq
    end

    test "bhv serializationobject 052 an undeclared key on a non-strict model is ignored and the declared fields survive" do
      category =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"id":7,"name":"cats","unexpected":"x"}),
          "Category"
        )

      assert %PetstoreClient.Models.Category{id: 7, name: "cats"} = category

      pet =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"name":"Rex","photoUrls":[],"unexpected":{"a":1}}),
          "Pet"
        )

      assert pet.name == "Rex"
    end

    test "Order.trackingId is written lowercase, decoded lowercase and rejected when malformed" do
      upper = "E621E1F8-C36C-495A-93FC-0C247A3E6E5F"
      lower = String.downcase(upper)

      order = %PetstoreClient.Models.Order{tracking_id: upper}

      assert Jason.decode!(PetstoreClient.ObjectSerializer.serialize(order))["trackingId"] ==
               lower

      decoded =
        PetstoreClient.ObjectSerializer.deserialize(
          Jason.encode!(%{"trackingId" => upper}),
          "Order"
        )

      assert decoded.tracking_id == lower

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(~s({"trackingId":"not-a-uuid"}), "Order")
      end
    end

    test "bhv serializationobject 012 the prefixItems array Pet.location keeps its mixed items in order" do
      pet = %PetstoreClient.Models.Pet{
        name: "Rex",
        photo_urls: MapSet.new(),
        location: [1.5, 2.5, "label"]
      }

      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(pet))
      assert data["location"] == [1.5, 2.5, "label"]

      decoded =
        PetstoreClient.ObjectSerializer.deserialize(
          Jason.encode!(%{"name" => "Rex", "photoUrls" => [], "location" => [1.5, 2.5, "label"]}),
          "Pet"
        )

      assert decoded.location == [1.5, 2.5, "label"]
    end

    test "bhv serializationobject 117 a digit-leading and a keyword-named property keep their wire keys" do
      edge = %PetstoreClient.Models.EdgeCases{
        n2fa: true,
        class: "c",
        return: "r",
        _and: true,
        _or: false,
        _not: true
      }

      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(edge))

      assert data["2fa"] == true
      assert data["class"] == "c"
      assert data["return"] == "r"
      assert data["and"] == true
      assert data["or"] == false
      assert data["not"] == true

      decoded =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"2fa":true,"class":"c","return":"r","and":true,"or":false,"not":true,"null":"n","true":true,"false":false}),
          "EdgeCases"
        )

      assert decoded.n2fa == true
      assert decoded.class == "c"
      assert decoded.return == "r"
      assert decoded._and == true
      assert decoded._or == false
      assert decoded._not == true
      assert decoded.null == "n"
      assert decoded._true == true
      assert decoded._false == false
    end

    test "bhv serializationobject 104 the Error and Result schemas round-trip through their models" do
      error = PetstoreClient.ObjectSerializer.deserialize(~s({"code":1,"message":"m"}), "Error")
      assert %PetstoreClient.Models.Error{code: 1, message: "m"} = error

      assert Jason.decode!(PetstoreClient.ObjectSerializer.serialize(error)) == %{
               "code" => 1,
               "message" => "m"
             }

      result = PetstoreClient.ObjectSerializer.deserialize(~s({"ok":true}), "Result")
      assert %PetstoreClient.Models.Result{ok: true} = result
      assert Jason.decode!(PetstoreClient.ObjectSerializer.serialize(result)) == %{"ok" => true}
    end

    test "bhv serializationobject 115 PetPassport.biometricChip (contentEncoding base64) round-trips as bytes" do
      chip = <<1, 2, 3, 255>>
      passport = %PetstoreClient.Models.PetPassport{biometric_chip: chip}

      data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(passport))
      assert data["biometricChip"] == Base.encode64(chip)

      decoded =
        PetstoreClient.ObjectSerializer.deserialize(
          Jason.encode!(%{"biometricChip" => Base.encode64(chip)}),
          "PetPassport"
        )

      assert decoded.biometric_chip == chip
    end

    test "bhv serializationobject 006 a duplicate object key resolves to the last value, even when the first has the wrong type" do
      pet =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"name":"a","name":"b","photoUrls":[]}),
          "Pet"
        )

      assert pet.name == "b"

      retyped =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"name":5,"name":"b","photoUrls":[]}),
          "Pet"
        )

      assert retyped.name == "b"
    end

    test "bhv serializationobject 074 EdgeCases.slug accepts a value matching its pattern" do
      edge = PetstoreClient.ObjectSerializer.deserialize(~s({"slug":"12/ab"}), "EdgeCases")
      assert edge.slug == "12/ab"
    end

    test "bhv serializationobject 010 a nickname longer than its declared maximum still decodes and round-trips" do
      json = ~s({"name":"n","photoUrls":[],"nickname":"ZZZZZZZZZZ"})
      decoded = PetstoreClient.ObjectSerializer.deserialize(json, "Pet")

      assert decoded.nickname == "ZZZZZZZZZZ"

      assert Jason.decode!(PetstoreClient.ObjectSerializer.serialize(decoded))["nickname"] ==
               "ZZZZZZZZZZ"
    end

    test "a quantity above its declared maximum still decodes" do
      decoded = PetstoreClient.ObjectSerializer.deserialize(~s({"quantity":500}), "Order")

      assert decoded.quantity == 500
    end

    test "bhv serializationobject 003 a date-time without an offset is rejected whatever the host zone" do
      for value <- ["2024-01-01T10:00:00", "2024-01-01T10:00:00.123", "2024-01-01T10:00"] do
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.convert_to_type(value, "DateTime")
        end

        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(
            Jason.encode!(%{"expiresAt" => value}),
            "EdgeCases"
          )
        end
      end
    end

    test "bhv serializationobject 124 U+2028 and U+2029 are written literally and only quote, backslash and controls are escaped" do
      json = PetstoreClient.ObjectSerializer.serialize(%{"k" => "a\u2028b\u2029c\"d\\e\nf"})

      assert json =~ "a\u2028b\u2029c"
      refute json =~ "\\u2028"
      refute json =~ "\\u2029"
      assert json =~ ~S(c\"d\\e\nf)
    end

    test "a protobuf duration decodes within its bound and is rejected beyond it" do
      one_hour = PetstoreClient.ObjectSerializer.convert_to_type("3600s", "Duration")
      assert PetstoreClient.ObjectSerializer.stringify(one_hour) == "3600s"

      for ok <- ["315576000000s", "-315576000000s"] do
        assert %Duration{} = PetstoreClient.ObjectSerializer.convert_to_type(ok, "Duration")
      end

      for bad <- ["315576000001s", "-315576000001s"] do
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.convert_to_type(bad, "Duration")
        end
      end
    end

    test "the Separator enum members round-trip through their wire strings" do
      for {atom, wire} <- [space_1: " ", value_0040_0040: "@@", a_b: "a-b", a_b_2: "a_b"] do
        assert PetstoreClient.Models.Separator.value(atom) == wire
        assert PetstoreClient.Models.Separator.from_value(wire) == atom

        edge = %PetstoreClient.Models.EdgeCases{separator: atom}
        data = Jason.decode!(PetstoreClient.ObjectSerializer.serialize(edge))
        assert data["separator"] == wire

        decoded =
          PetstoreClient.ObjectSerializer.deserialize(
            Jason.encode!(%{"separator" => wire}),
            "EdgeCases"
          )

        assert decoded.separator == atom
      end
    end
  end

  describe "inline enum modules" do
    test "Defaults.ModeEnum exposes its members" do
      mode = PetstoreClient.Models.Defaults.ModeEnum
      assert mode.all_values() == [:low, :medium, :high]
      assert mode.value(:high) == "high"
      assert mode.from_value("nope") == {:error, "nope"}
    end

    test "bhv serializationobject 022 EdgeCases.PhaseEnum maps non-identifier wire values" do
      phase = PetstoreClient.Models.EdgeCases.PhaseEnum
      assert phase.value(:on_hold) == "on-hold"
      assert phase.from_value("OUT_OF_STOCK") == :out_of_stock
      assert phase.from_value("x") == {:error, "x"}

      decoded =
        PetstoreClient.ObjectSerializer.deserialize(~s({"phase":"on-hold"}), "EdgeCases")

      assert decoded.phase == :on_hold
    end

    test "Order.StatusEnum exposes its members and still decodes the field" do
      status = PetstoreClient.Models.Order.StatusEnum
      assert status.all_values() == [:placed, :approved, :delivered]
      assert status.value(:placed) == "placed"
      assert status.from_value("approved") == :approved
      assert status.from_value("bogus") == {:error, "bogus"}

      decoded = PetstoreClient.ObjectSerializer.deserialize(~s({"status":"delivered"}), "Order")
      assert decoded.status == :delivered
    end

    test "Pet.StatusEnum and PetWithOwner.StatusEnum expose their members" do
      for mod <- [
            PetstoreClient.Models.Pet.StatusEnum,
            PetstoreClient.Models.PetWithOwner.StatusEnum
          ] do
        assert mod.all_values() == [:available, :pending, :sold]
        assert mod.value(:pending) == "pending"
        assert mod.from_value("sold") == :sold
        assert mod.from_value("banana") == {:error, "banana"}
      end
    end
  end

  # F316: building a model never validates. A value the schema would refuse (a
  # wrong type, an atom outside an enum) builds fine; the unknown enum string
  # fails loudly, and only when it is decoded.
  describe "constructing a model does not validate, decoding does" do
    test "bhv serializationobject 030 wrong-typed and out-of-range values build without raising" do
      assert %PetstoreClient.Models.Order{
               id: "not a number",
               quantity: :many,
               status: :no_such_member
             } =
               %PetstoreClient.Models.Order{
                 id: "not a number",
                 quantity: :many,
                 status: :no_such_member
               }

      assert %PetstoreClient.Models.StockItem{priority: 99, availability: "no-such-member"} =
               %PetstoreClient.Models.StockItem{priority: 99, availability: "no-such-member"}

      assert %PetstoreClient.Models.Pet{name: 7, photo_urls: "not a list"} =
               %PetstoreClient.Models.Pet{name: 7, photo_urls: "not a list"}
    end

    test "an unknown enum string fails when it is decoded" do
      error =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(~s({"status":"no-such-member"}), "Order")
        end

      assert Exception.message(error) =~ "no-such-member"
    end

    test "a wrong-typed property fails when it is decoded" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(~s({"id":"not a number"}), "Order")
      end
    end
  end

  describe "discriminator is an enforced key" do
    test "building a subtype without the discriminator raises" do
      error =
        assert_raise ArgumentError, fn ->
          Code.eval_string("%PetstoreClient.Models.DryFood{weight_kg: 1.0}")
        end

      assert Exception.message(error) =~ ":food_type"
    end
  end

  describe "strict models (unevaluatedProperties:false)" do
    test "bhv serializationobject 154 an undeclared key raises SerializationError naming the key" do
      error =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(~s({"id":1,"extra":true}), "StrictTag")
        end

      assert Exception.message(error) =~ "extra"
    end

    test "bhv serializationobject 047 declared keys decode" do
      decoded = PetstoreClient.ObjectSerializer.deserialize(~s({"id":1,"name":"a"}), "StrictTag")
      assert decoded.id == 1
      assert decoded.name == "a"
    end

    test "the model exposes no public from_map_strict" do
      refute function_exported?(PetstoreClient.Models.StrictTag, :from_map_strict, 1)
      assert PetstoreClient.Models.StrictTag.strict_properties?()
    end
  end
end
