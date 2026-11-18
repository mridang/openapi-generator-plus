defmodule PetstoreClient.Models.MetadataTest do
  use ExUnit.Case, async: true

  describe "typed additional properties" do
    test "bhv models 008 serializes empty metadata" do
      metadata = %PetstoreClient.Models.Metadata{}
      json = PetstoreClient.ObjectSerializer.serialize(metadata)

      assert json != nil
    end

    test "bhv models 003 deserializes empty object" do
      metadata = PetstoreClient.ObjectSerializer.deserialize("{}", "Metadata")

      assert metadata != nil
    end

    test "bhv models 004 deserializes additional string properties" do
      json = ~s({"createdAt":"2024-01-01T00:00:00+0000","customField":"hello"})
      metadata = PetstoreClient.ObjectSerializer.deserialize(json, "Metadata")

      assert %PetstoreClient.Models.Metadata{} = metadata
      assert metadata.additional_properties["customField"] == "hello"
    end

    test "bhv models 009 additional properties are a string-keyed map of the value type" do
      json = ~s({"createdAt":"2024-01-01T00:00:00+0000","customField":"hello"})
      metadata = PetstoreClient.ObjectSerializer.deserialize(json, "Metadata")

      assert is_map(metadata.additional_properties)
      assert is_binary(metadata.additional_properties["customField"])
      assert PetstoreClient.Models.Metadata.additional_properties_type() == "String.t()"
    end

    test "bhv models 025 an additional property of the wrong JSON type fails the decode" do
      for body <- [~s({"a":2.5}), ~s({"a":"2"})] do
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(body, "Counters")
        end
      end

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(~s({"a":3}), "Metadata")
      end
    end

    test "bhv models 026 an additional property whose value type is a model decodes to that model" do
      roster =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"label":"l","a":{"id":1,"name":"x"}}),
          "Roster"
        )

      assert %PetstoreClient.Models.Category{id: 1, name: "x"} = roster.additional_properties["a"]

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(~s({"a":{"id":"x"}}), "Roster")
      end
    end

    test "bhv models 027 a null additional property is rejected unless the value schema is nullable" do
      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(~s({"a":null}), "Counters")
      end

      sparse = PetstoreClient.ObjectSerializer.deserialize(~s({"a":null}), "Sparse")

      assert sparse.additional_properties == %{"a" => nil}
    end

    test "bhv models 028 a typed additional property is written flat in its wire form" do
      {:ok, at, _} = DateTime.from_iso8601("2024-01-01T00:00:00Z")
      moments = %PetstoreClient.Models.Moments{label: "l", additional_properties: %{"at" => at}}
      data = moments |> PetstoreClient.ObjectSerializer.serialize() |> Jason.decode!()

      assert data["label"] == "l"
      assert data["at"] =~ "2024-01-01T00:00:00"

      round_trip =
        PetstoreClient.ObjectSerializer.deserialize(Jason.encode!(data), "Moments")

      assert DateTime.compare(round_trip.additional_properties["at"], at) == :eq

      blobs = %PetstoreClient.Models.Blobs{additional_properties: %{"b" => "hi"}}

      assert PetstoreClient.ObjectSerializer.serialize(blobs) |> Jason.decode!() == %{
               "b" => "aGk="
             }

      assert PetstoreClient.ObjectSerializer.deserialize(~s({"b":"aGk="}), "Blobs").additional_properties[
               "b"
             ] == "hi"
    end

    test "bhv models 029 a free-form additionalProperties keeps every JSON value as it arrives" do
      freeform =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"a":1,"b":"x","c":[true],"d":null}),
          "Freeform"
        )

      assert freeform.additional_properties == %{"a" => 1, "b" => "x", "c" => [true], "d" => nil}
    end

    test "bhv models 011 round-trip preserves known properties" do
      metadata = %PetstoreClient.Models.Metadata{
        created_at: "2024-01-01T00:00:00+00:00"
      }

      json = PetstoreClient.ObjectSerializer.serialize(metadata)
      data = Jason.decode!(json)
      assert data["createdAt"] != nil
    end

    test "bhv models 001 captures undeclared keys into additional_properties" do
      metadata =
        PetstoreClient.ObjectSerializer.deserialize(~s({"customField":"hello"}), "Metadata")

      assert metadata.additional_properties == %{"customField" => "hello"}
    end

    test "bhv models 010 declared property wins over a colliding additional property" do
      metadata = %PetstoreClient.Models.Metadata{
        created_at: "2024-01-01T00:00:00+00:00",
        additional_properties: %{"createdAt" => "x", "other" => "y"}
      }

      data = metadata |> PetstoreClient.ObjectSerializer.serialize() |> Jason.decode!()

      assert data["createdAt"] =~ "2024-01-01"
      refute data["createdAt"] == "x"
      assert data["other"] == "y"
    end

    test "bhv models 005 equality includes additional_properties" do
      plain = %PetstoreClient.Models.Metadata{}

      with_extra = %PetstoreClient.Models.Metadata{
        additional_properties: %{"customField" => "hello"}
      }

      same_extra = %PetstoreClient.Models.Metadata{
        additional_properties: %{"customField" => "hello"}
      }

      other_extra = %PetstoreClient.Models.Metadata{
        additional_properties: %{"customField" => "other"}
      }

      assert with_extra == same_extra
      refute plain == with_extra
      refute with_extra == other_extra
    end

    test "bhv equality 001 equal values are equal and a change in a nested value makes them unequal" do
      build = fn value ->
        %PetstoreClient.Models.Metadata{
          created_at: "2024-01-01T00:00:00+00:00",
          additional_properties: %{"customField" => value}
        }
      end

      assert build.("hello") == build.("hello")
      refute build.("hello") == build.("other")
      refute build.("hello") == Map.from_struct(build.("hello"))
      refute build.("hello") == nil
    end

    test "bhv models 012 inspect dumps the fields and the additional_properties map" do
      metadata = %PetstoreClient.Models.Metadata{
        created_at: ~U[2024-01-15 10:30:00Z],
        additional_properties: %{"customField" => "hello"}
      }

      text = inspect(metadata)

      assert text =~ "%PetstoreClient.Models.Metadata{"
      assert text =~ "created_at: ~U[2024-01-15 10:30:00Z]"
      assert text =~ ~s(additional_properties: %{"customField" => "hello"})
    end

    test "bhv models 022 defines additional_properties function" do
      assert PetstoreClient.Models.Metadata.additional_properties() == true
    end
  end

  # L1 parity: a property whose schema example is the JSON null literal must
  # not leak a useless `Example: null` / `@example null` doc line into the
  # generated model. The Elixir model template only emits example doc lines
  # from `vendorExtensions.pluralExamples` (rendered as `## <summary>` blocks),
  # so the null-literal placeholder never appears; this asserts that contract
  # holds across every generated model source rather than re-appearing as
  # documentation noise. It is a parity assertion that should already pass and
  # stay passing.
  describe "generated model docblocks (L1 parity)" do
    test "bhv models 007 no generated model carries a literal example null doc line" do
      model_files =
        Path.wildcard(Path.join([File.cwd!(), "lib", "**", "models", "*.ex"]))

      assert model_files != []

      for path <- model_files do
        source = File.read!(path)
        refute String.contains?(source, "Example: null"), "#{path} contains 'Example: null'"
        refute String.contains?(source, "@example null"), "#{path} contains '@example null'"
      end
    end

    # @deprecated is a function attribute and would land on the struct's own
    # function, which nobody calls; the schema is marked in the module docs.
    test "a deprecated schema is marked in its module docs, not on a function" do
      {:docs_v1, _anno, _lang, _format, _moduledoc, metadata, _docs} =
        Code.fetch_docs(PetstoreClient.Models.Tag)

      assert metadata[:deprecated] == "This schema is deprecated."
      assert PetstoreClient.Models.Tag.__info__(:deprecated) == []
    end
  end

  # The file header is the info description, written as comment text.
  describe "generated file header" do
    test "bhv models 006 file header prints the info description without backslash-escaped quotes" do
      [path] = Path.wildcard(Path.join([File.cwd!(), "lib", "**", "models", "metadata.ex"]))
      source = File.read!(path)

      assert String.contains?(source, ~s("quoted" text))
      refute String.contains?(source, ~s(\\"quoted\\"))
    end
  end

  # manifest-description-missing: the Hex package manifest must carry a
  # non-empty description so the package does not publish with an empty
  # description field.
  describe "package manifest" do
    test "bhv models 002 mix project package metadata declares a non-empty description" do
      description = PetstoreClient.MixProject.project()[:package][:description]
      assert is_binary(description)
      assert String.trim(description) != ""
    end
  end

  describe "model construction and copies" do
    test "bhv models 013 a newly built model starts with an empty additional_properties map, never nil" do
      metadata = %PetstoreClient.Models.Metadata{}

      assert metadata.additional_properties == %{}
    end

    test "bhv models 014 a built model cannot be reassigned, and an update leaves the original unchanged" do
      metadata = %PetstoreClient.Models.Metadata{created_at: "2024-01-01T00:00:00+00:00"}

      _updated = %{metadata | created_at: "2025-01-01T00:00:00+00:00"}

      assert metadata.created_at == "2024-01-01T00:00:00+00:00"
    end

    test "bhv models 015 a changed copy keeps the additional properties of the original" do
      metadata = %PetstoreClient.Models.Metadata{
        additional_properties: %{"customField" => "hello"}
      }

      copy = %{metadata | created_at: "2024-01-01T00:00:00+00:00"}

      assert copy.additional_properties == %{"customField" => "hello"}
    end

    test "bhv models 016 a copy clears an optional field only when nil is set and keeps it when omitted" do
      metadata = %PetstoreClient.Models.Metadata{created_at: "2024-01-01T00:00:00+00:00"}

      cleared = %{metadata | created_at: nil}
      kept = %{metadata | additional_properties: %{"x" => "1"}}

      assert cleared.created_at == nil
      assert kept.created_at == "2024-01-01T00:00:00+00:00"
    end

    test "bhv models 017 a copy can set an optional field and the original keeps its value" do
      metadata = %PetstoreClient.Models.Metadata{}

      copy = %{metadata | created_at: "2024-01-01T00:00:00+00:00"}

      assert copy.created_at == "2024-01-01T00:00:00+00:00"
      assert metadata.created_at == nil
    end

    test "bhv models 019 models with a different value in any field compare unequal" do
      first = %PetstoreClient.Models.Metadata{created_at: "2024-01-01T00:00:00+00:00"}
      second = %PetstoreClient.Models.Metadata{created_at: "2025-01-01T00:00:00+00:00"}
      third = %{first | additional_properties: %{"x" => "1"}}

      refute first == second
      refute first == third
    end

    test "bhv models 020 models with the same field values compare equal and hash alike" do
      first = %PetstoreClient.Models.Metadata{created_at: "2024-01-01T00:00:00+00:00"}
      second = %PetstoreClient.Models.Metadata{created_at: "2024-01-01T00:00:00+00:00"}

      assert first == second
      assert :erlang.phash2(first) == :erlang.phash2(second)
      assert MapSet.size(MapSet.new([first, second])) == 1
    end

    test "bhv models 023 undeclared keys named like SDK internals stay additional properties" do
      metadata =
        PetstoreClient.ObjectSerializer.deserialize(~s({"serialize":"hello"}), "Metadata")

      assert metadata.additional_properties == %{"serialize" => "hello"}
    end

    test "bhv models 024 a newly built model leaves its optional properties unset" do
      metadata = %PetstoreClient.Models.Metadata{}

      assert metadata.created_at == nil
    end
  end
end
