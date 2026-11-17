defmodule PetstoreClient.ComposedSchemaTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.Models.SetPetAvatarThumbnailRequest

  # -- oneOf with discriminator: PetFood --

  describe "oneOf with discriminator PetFood" do
    test "bhv unions 012 deserializes to DryFood via discriminator" do
      json = ~s({"foodType":"dry","weightKg":2.5})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")

      assert %PetstoreClient.Models.DryFood{} = result
    end

    test "bhv unions 013 deserializes to WetFood via discriminator" do
      json = ~s({"foodType":"wet","volumeMl":400})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")

      assert %PetstoreClient.Models.WetFood{} = result
    end

    test "bhv unions 016 raises SerializationError for unknown discriminator value" do
      # Gap L: union deserialise with an unmapped discriminator now
      # raises instead of silently returning nil. Aligns Elixir with
      # Python / Swift / Dart / Go / Rust.
      json = ~s({"foodType":"raw","calories":300})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      end
    end

    test "bhv unions 015 raises SerializationError for missing discriminator field" do
      # A payload omitting the discriminator field entirely cannot route to
      # any variant; the union build raises instead of silently returning nil.
      # Aligns Elixir with Python / Swift / PHP / Ruby / Dart / Rust.
      json = ~s({"weightKg":2.5})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      end
    end

    test "raises SerializationError for an array body instead of an Access error" do
      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize("[1,2]", "PetFood")
        end

      assert err.message == "Expected a JSON object for PetFood, got [1, 2]"
    end

    test "bhv unions 024 raises SerializationError for empty discriminator value" do
      # An empty discriminator value matches no listed mapping and must raise
      # rather than route to a structurally-fitting variant.
      json = ~s({"foodType":"","weightKg":2.5})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      end
    end

    test "bhv unions 026 unknown discriminator error names the offending value" do
      # CS4: the error message must surface the offending discriminator value
      # so callers can diagnose server/spec drift.
      json = ~s({"foodType":"raw","calories":300})

      err =
        assert_raise PetstoreClient.Errors.SerializationError, fn ->
          PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
        end

      assert err.message =~ "raw"
    end

    test "bhv unions 020 serializes DryFood back to JSON" do
      json = ~s({"foodType":"dry","weightKg":2.5})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")
      serialized = PetstoreClient.ObjectSerializer.serialize(result)

      assert serialized =~ "dry"
    end
  end

  # -- anyOf without discriminator: PetTreatment --

  describe "anyOf PetTreatment" do
    test "bhv unions 014 deserializes Medication from anyOf" do
      json = ~s({"drugName":"Amoxicillin","dosage":"500mg"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")

      assert %PetstoreClient.Models.Medication{} = result
      assert result.drug_name == "Amoxicillin"
    end

    test "bhv unions 006 deserializes Surgery from anyOf" do
      json = ~s({"procedureName":"Spay","durationMinutes":45})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")

      assert %PetstoreClient.Models.Surgery{} = result
    end

    test "bhv unions 001 a factory wraps its variant and only its is accessor answers true" do
      medication = %PetstoreClient.Models.Medication{drug_name: "Amoxicillin", dosage: "500mg"}

      wrapped = PetstoreClient.Models.PetTreatment.from_medication(medication)

      assert wrapped == medication
      assert PetstoreClient.Models.PetTreatment.medication?(wrapped)
      refute PetstoreClient.Models.PetTreatment.surgery?(wrapped)
      assert PetstoreClient.Models.PetTreatment.as_medication(wrapped) == medication
    end

    test "bhv unions 011 anyOf wrappers have value equality and a matching hash" do
      json =
        ~s({"drugName":"Amoxicillin","dosage":"250mg","procedureName":"Spay","durationMinutes":45})

      first = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")
      second = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")

      other =
        PetstoreClient.ObjectSerializer.deserialize(
          ~s({"drugName":"Doxycycline","dosage":"100mg"}),
          "PetTreatment"
        )

      assert first == second
      assert :erlang.phash2(first) == :erlang.phash2(second)
      refute first == other
    end

    test "bhv unions 003 a single-variant anyOf is its own actual and matched instance" do
      json = ~s({"drugName":"Amoxicillin","dosage":"500mg"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")

      assert PetstoreClient.Models.PetTreatment.matched_instances(result) == [result]
      assert PetstoreClient.Models.PetTreatment.actual_instance(result) == result
    end

    test "bhv unions 008 serializes round-trip for anyOf" do
      json = ~s({"drugName":"Amoxicillin","dosage":"500mg"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")
      serialized = PetstoreClient.ObjectSerializer.serialize(result)

      assert byte_size(serialized) > 0
    end

    test "bhv unions 009 anyOf retains all matching variants (medication and surgery) losslessly" do
      # PetTreatment is documented as matching a medication, a surgery, OR
      # BOTH. A payload satisfying both variants at once must retain the
      # data of BOTH — not silently drop the second variant's fields on the
      # first match. The decoded value is an AnyOfComposite holding each
      # matched variant struct, and re-serializing it must emit the union of
      # all four fields so the co-satisfied payload round-trips losslessly.
      json =
        ~s({"drugName":"Amoxicillin","dosage":"250mg","procedureName":"Spay","durationMinutes":45})

      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")

      # Both variant structs are retained on the composite.
      assert %PetstoreClient.AnyOfComposite{instances: instances} = result

      assert [%PetstoreClient.Models.Medication{}, %PetstoreClient.Models.Surgery{}] =
               PetstoreClient.Models.PetTreatment.matched_instances(result)

      assert %PetstoreClient.Models.Medication{} =
               PetstoreClient.Models.PetTreatment.actual_instance(result)

      medication =
        Enum.find(instances, &match?(%PetstoreClient.Models.Medication{}, &1))

      surgery =
        Enum.find(instances, &match?(%PetstoreClient.Models.Surgery{}, &1))

      refute is_nil(medication)
      refute is_nil(surgery)

      # Medication data accessible.
      assert medication.drug_name == "Amoxicillin"
      assert medication.dosage == "250mg"

      # Surgery data accessible.
      assert surgery.procedure_name == "Spay"
      assert surgery.duration_minutes == 45

      # Every matched variant's field is also flattened onto the composite.
      assert PetstoreClient.AnyOfComposite.get(result, :drug_name) == "Amoxicillin"
      assert PetstoreClient.AnyOfComposite.get(result, :dosage) == "250mg"
      assert PetstoreClient.AnyOfComposite.get(result, :procedure_name) == "Spay"
      assert PetstoreClient.AnyOfComposite.get(result, :duration_minutes) == 45

      # Re-serialize: ALL FOUR fields survive the round-trip; nothing dropped.
      serialized = PetstoreClient.ObjectSerializer.serialize(result)
      assert serialized =~ "drugName"
      assert serialized =~ "Amoxicillin"
      assert serialized =~ "dosage"
      assert serialized =~ "250mg"
      assert serialized =~ "procedureName"
      assert serialized =~ "Spay"
      assert serialized =~ "durationMinutes"
      assert serialized =~ "45"
    end

    test "bhv unions 010 anyOf collision keeps the last-declared variant, emitted once" do
      # anyOf-collision-last-wins: both Medication and Surgery declare `notes`.
      # When a PetTreatment retains both members with DIFFERENT `notes` values,
      # the merged encoding keeps the LAST-declared member's value (Surgery) and
      # emits `notes` exactly once — never a duplicate JSON key.
      composite = %PetstoreClient.AnyOfComposite{
        instances: [
          %PetstoreClient.Models.Medication{drug_name: "Amoxicillin", notes: "from-medication"},
          %PetstoreClient.Models.Surgery{procedure_name: "Spay", notes: "from-surgery"}
        ]
      }

      serialized = PetstoreClient.ObjectSerializer.serialize(composite)

      # Surgery is the later-declared member, so its `notes` wins the collision.
      assert serialized =~ "from-surgery"
      refute serialized =~ "from-medication"
      # `notes` appears exactly once — no duplicate key.
      assert length(String.split(serialized, ~s("notes"))) == 2
    end

    test "bhv unions 005 an anyOf keeps its variants in declaration order, not alphabetical order" do
      # TreatmentRecord declares Surgery before Medication, the reverse of
      # alphabetical order: the first declared match is the actual instance.
      json = ~s({"drugName":"a","procedureName":"b"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "TreatmentRecord")

      assert [%PetstoreClient.Models.Surgery{}, %PetstoreClient.Models.Medication{}] =
               PetstoreClient.Models.TreatmentRecord.matched_instances(result)

      assert %PetstoreClient.Models.Surgery{} =
               PetstoreClient.Models.TreatmentRecord.actual_instance(result)
    end

    test "bhv unions 002 a shared property of two matched anyOf variants takes the later declared value" do
      json = ~s({"drugName":"a","procedureName":"b","notes":"shared"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "TreatmentRecord")
      serialized = PetstoreClient.ObjectSerializer.serialize(result)

      assert serialized =~ "shared"
      assert length(String.split(serialized, ~s("notes"))) == 2
    end

    test "bhv unions 007 raises for anyOf payload matching no variant" do
      # oneof-nondiscriminator-no-match-silent: a body matching neither
      # Medication nor Surgery must raise rather than return a silently-empty
      # union. resolve_any_of raises SerializationError on union no-match.
      json = ~s({"unrelatedKey":"value","anotherUnknown":123})

      assert_raise PetstoreClient.Errors.SerializationError, fn ->
        PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")
      end
    end
  end

  # -- oneOf of byte variants: SetPetAvatarThumbnailRequest --

  # SetPetAvatarThumbnailRequest is a non-discriminated `oneOf` of
  #   [ a scalar `format: byte` value, an array of `format: byte` values ].
  # `format: byte` travels on the wire as a base64 STRING. The Elixir union
  # holds the native byte form (a bare `binary()`, or a list of them); the
  # `format: byte` marker must survive INSIDE the union so each byte variant
  # round-trips through base64 — the exact path the bare scalar `format: byte`
  # field already uses. Guards against: a scalar byte value emitted raw/
  # unencoded (Elixir `binary()` is indistinguishable from a plain string at
  # the generic serializer), and a scalar base64 string mis-parsed element-by-
  # element into a list because the codegen lists the array variant first.
  describe "setPetAvatarThumbnail byte oneOf round-trips through base64" do
    test "bhv unions 025 variants are tried in schema declaration order, not alphabetical order" do
      assert SetPetAvatarThumbnailRequest.openapi_one_of() == [:"binary()", :"[binary()]"]
    end

    test "bhv unions 019 scalar byte variant serializes to a base64 STRING, not raw bytes or an int-array" do
      raw = <<0x01, 0x02, 0x03, 0x04>>

      wire = SetPetAvatarThumbnailRequest.serialize(raw)

      # The wire value is the base64 STRING "AQIDBA==" — NOT the raw bytes,
      # NOT a JSON int-array [1,2,3,4].
      assert is_binary(wire)
      assert wire == "AQIDBA=="
      assert wire == Base.encode64(raw)
      refute is_list(wire)

      # Encoding the sanitized value as JSON yields a quoted base64 string,
      # never a numeric array.
      assert Jason.encode!(wire) == ~s("AQIDBA==")
    end

    test "bhv unions 023 scalar base64 string deserializes back to the original 4 bytes (scalar before array)" do
      # A scalar base64 string must resolve to the SCALAR variant (one
      # `binary()`), NOT be mis-parsed element-by-element into a list. The
      # array variant "[binary()]" is listed first, so this also pins the
      # ordering fix.
      raw = <<0x01, 0x02, 0x03, 0x04>>

      restored = SetPetAvatarThumbnailRequest.build("AQIDBA==")

      assert is_binary(restored)
      refute is_list(restored)
      assert restored == raw
      assert byte_size(restored) == 4
    end

    test "bhv unions 018 scalar byte variant round-trips serialize -> deserialize -> identical bytes" do
      raw = <<0x01, 0x02, 0x03, 0x04>>

      wire = SetPetAvatarThumbnailRequest.serialize(raw)
      restored = SetPetAvatarThumbnailRequest.build(wire)

      assert restored == raw
    end

    test "bhv unions 022 array-of-byte variant serializes to an array of base64 strings and round-trips" do
      first = <<0x01, 0x02, 0x03, 0x04>>
      second = <<0x05, 0x06>>

      wire = SetPetAvatarThumbnailRequest.serialize([first, second])

      # An array of base64 STRINGS — NOT an array of int-arrays nor raw bytes.
      assert wire == ["AQIDBA==", "BQY="]
      assert Enum.all?(wire, &is_binary/1)

      restored = SetPetAvatarThumbnailRequest.build(wire)

      assert restored == [first, second]
    end
  end

  # -- allOf: PetWithOwner --

  describe "allOf PetWithOwner" do
    test "bhv unions 004 deserializes all properties from allOf composition" do
      json =
        ~s({"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John","ownerEmail":"john@example.com"})

      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetWithOwner")

      assert %PetstoreClient.Models.PetWithOwner{} = result
      assert result.name == "doggie"
      assert result.owner_name == "John"
      assert result.owner_email == "john@example.com"
    end

    test "bhv unions 021 serializes PetWithOwner to JSON" do
      json =
        ~s({"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"})

      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetWithOwner")
      serialized = PetstoreClient.ObjectSerializer.serialize(result)

      assert serialized =~ "Fido"
      assert serialized =~ "John Doe"
    end

    test "bhv unions 017 round-trip preserves all fields" do
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
