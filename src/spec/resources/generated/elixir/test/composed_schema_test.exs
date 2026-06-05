defmodule PetstoreClient.ComposedSchemaTest do
  use ExUnit.Case, async: true

  # -- oneOf with discriminator: PetFood --

  describe "oneOf with discriminator PetFood" do
    test "deserializes to DryFood via discriminator" do
      json = ~s({"foodType":"dry","weightKg":2.5})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")

      assert %PetstoreClient.Models.DryFood{} = result
    end

    test "deserializes to WetFood via discriminator" do
      json = ~s({"foodType":"wet","volumeMl":400})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")

      assert %PetstoreClient.Models.WetFood{} = result
    end

    test "raises ArgumentError for unknown discriminator value" do
      # Gap L: union deserialise with an unmapped discriminator now
      # raises instead of silently returning nil. Aligns Elixir with
      # Python / Swift / Dart / Go / Rust.
      json = ~s({"foodType":"raw","calories":300})

      assert_raise ArgumentError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      end
    end

    test "raises ArgumentError for missing discriminator field" do
      # A payload omitting the discriminator field entirely cannot route to
      # any variant; the union build raises instead of silently returning nil.
      # Aligns Elixir with Python / Swift / PHP / Ruby / Dart / Rust.
      json = ~s({"weightKg":2.5})

      assert_raise ArgumentError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      end
    end

    test "serializes DryFood back to JSON" do
      json = ~s({"foodType":"dry","weightKg":2.5})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      serialized = PetstoreClient.ObjectSerializer.serialize(result)

      assert serialized =~ "dry"
    end

    # 4.7: the discriminator branch must reject a mapping that points
    # at a schema outside the `oneOf` `$ref` list. The DeserializationError
    # exception is declared in ObjectSerializer; verify it exists and is
    # an Elixir exception module so generated discriminator code can
    # raise it.
    test "DeserializationError module is declared as an exception" do
      # Elixir 1.19 loads modules lazily, so `function_exported?/3` reports
      # `false` for a module that has not yet been loaded. Force the load
      # before introspecting it.
      Code.ensure_loaded(PetstoreClient.DeserializationError)
      assert function_exported?(PetstoreClient.DeserializationError, :exception, 1)
      err = PetstoreClient.DeserializationError.exception(message: "boom")
      assert err.message == "boom"
    end
  end

  # -- anyOf without discriminator: PetTreatment --

  describe "anyOf PetTreatment" do
    test "deserializes Medication from anyOf" do
      json = ~s({"drugName":"Amoxicillin","dosage":"500mg"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")

      assert %PetstoreClient.Models.Medication{} = result
      assert result.drug_name == "Amoxicillin"
    end

    test "deserializes Surgery from anyOf" do
      json = ~s({"procedureName":"Spay","durationMinutes":45})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")

      assert %PetstoreClient.Models.Surgery{} = result
    end

    test "serializes round-trip for anyOf" do
      json = ~s({"drugName":"Amoxicillin","dosage":"500mg"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")
      serialized = PetstoreClient.ObjectSerializer.serialize(result)

      assert byte_size(serialized) > 0
    end
  end

  # -- allOf: PetWithOwner --

  describe "allOf PetWithOwner" do
    test "deserializes all properties from allOf composition" do
      json =
        ~s({"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John","ownerEmail":"john@example.com"})

      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetWithOwner")

      assert %PetstoreClient.Models.PetWithOwner{} = result
      assert result.name == "doggie"
      assert result.owner_name == "John"
      assert result.owner_email == "john@example.com"
    end

    test "serializes PetWithOwner to JSON" do
      json =
        ~s({"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"})

      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetWithOwner")
      serialized = PetstoreClient.ObjectSerializer.serialize(result)

      assert serialized =~ "Fido"
      assert serialized =~ "John Doe"
    end

    test "round-trip preserves all fields" do
      json =
        ~s({"name":"Buddy","photoUrls":["http://example.com/buddy.jpg"],"ownerName":"Jane Smith"})

      original = PetstoreClient.ObjectSerializer.deserialize(json, "PetWithOwner")
      serialized = PetstoreClient.ObjectSerializer.serialize(original)
      restored = PetstoreClient.ObjectSerializer.deserialize(serialized, "PetWithOwner")

      assert restored.name == original.name
      assert restored.owner_name == original.owner_name
    end
  end
end
