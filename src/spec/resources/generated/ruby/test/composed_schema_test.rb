# frozen_string_literal: true
# rubocop:disable all

require 'test_helper'
require 'json'

describe 'Composed Schema' do
  parallelize_me!

  describe 'oneOf with discriminator: PetFood' do
    it 'deserializes to DryFood via discriminator' do
      json = '{"foodType":"dry","weightKg":2.5}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood')

      _(result).must_be_kind_of(PetstoreClient::Models::DryFood)
    end

    it 'deserializes to WetFood via discriminator' do
      json = '{"foodType":"wet","volumeMl":400}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood')

      _(result).must_be_kind_of(PetstoreClient::Models::WetFood)
    end

    it 'raises SerializationError for unknown discriminator value' do
      # Gap L / 4.7: union deserialise with an unmapped discriminator
      # raises SerializationError instead of falling through to the
      # base type. Aligns Ruby with Python / Swift / Dart / Go / Rust.
      json = '{"foodType":"raw","calories":300}'
      _ { PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood') }
        .must_raise PetstoreClient::SerializationError
    end

    it 'raises SerializationError for missing discriminator' do
      json = '{"weightKg":2.5}'
      _ { PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood') }
        .must_raise PetstoreClient::SerializationError
    end

    it 'raises SerializationError for empty discriminator value' do
      # An empty discriminator value matches no listed mapping and must raise
      # rather than route to a structurally-fitting variant.
      json = '{"foodType":"","weightKg":2.5}'
      _ { PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood') }
        .must_raise PetstoreClient::SerializationError
    end

    it 'unknown discriminator error names the offending value' do
      # CS4: the error message must surface the offending discriminator value
      # so callers can diagnose server/spec drift.
      json = '{"foodType":"raw","calories":300}'
      err = _ { PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood') }
            .must_raise PetstoreClient::SerializationError
      _(err.message).must_include('raw')
    end

    it 'serializes DryFood back to JSON' do
      json = '{"foodType":"dry","weightKg":2.5}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood')
      serialized = PetstoreClient::ObjectSerializer.serialize(result)

      _(serialized).must_include('dry')
    end
  end

  describe 'anyOf without discriminator: PetTreatment' do
    it 'deserializes a medication-only payload to the bare Medication variant' do
      # Single-variant match: exactly one variant decodes, so build returns
      # that bare instance (NOT a composite) — existing single-variant callers
      # are unaffected by the retain-all change.
      json = '{"drugName":"Amoxicillin","dosage":"500mg"}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetTreatment')

      _(result).must_be_kind_of(PetstoreClient::Models::Medication)
      _(result.drug_name).must_equal('Amoxicillin')
    end

    it 'deserializes a surgery-only payload to the bare Surgery variant' do
      json = '{"procedureName":"Spay","durationMinutes":45}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetTreatment')

      _(result).must_be_kind_of(PetstoreClient::Models::Surgery)
      _(result.procedure_name).must_equal('Spay')
      _(result.duration_minutes).must_equal(45)
    end

    it 'anyOf retains all matching variants (medication and surgery) losslessly' do
      # PetTreatment is documented as "a medication, a surgery, OR BOTH". A
      # payload satisfying BOTH variants at once must retain BOTH — the old
      # first-match build silently dropped the Surgery fields and lost them on
      # re-encode. Assert every field of both variants is reachable on the
      # decoded value, then re-serialize and assert all four fields survive the
      # round-trip (no silent drop).
      json = '{"drugName":"Amoxicillin","dosage":"250mg","procedureName":"Spay","durationMinutes":45}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetTreatment')

      # Both variants' data are accessible off the single decoded value.
      _(result.drug_name).must_equal('Amoxicillin')
      _(result.dosage).must_equal('250mg')
      _(result.procedure_name).must_equal('Spay')
      _(result.duration_minutes).must_equal(45)

      # Re-serialize: the union of ALL FOUR fields round-trips losslessly.
      serialized = PetstoreClient::ObjectSerializer.serialize(result)
      reparsed = JSON.parse(serialized)
      _(reparsed['drugName']).must_equal('Amoxicillin')
      _(reparsed['dosage']).must_equal('250mg')
      _(reparsed['procedureName']).must_equal('Spay')
      _(reparsed['durationMinutes']).must_equal(45)
    end

    it 'serializes round-trip for a single-variant anyOf payload' do
      json = '{"drugName":"Amoxicillin","dosage":"500mg"}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetTreatment')
      serialized = PetstoreClient::ObjectSerializer.serialize(result)

      _(serialized).wont_be_empty
      _(serialized).must_include('Amoxicillin')
    end

    it 'raises for anyOf payload matching no variant' do
      # oneof-nondiscriminator-no-match-silent: a body matching neither
      # Medication nor Surgery must raise rather than return a silently-empty
      # union. The anyOf build raises on union no-match, and deserialize wraps
      # every schema-validation failure uniformly as SerializationError so a
      # caller can rescue a single branded type.
      json = '{"unrelatedKey":"value","anotherUnknown":123}'
      _ { PetstoreClient::ObjectSerializer.deserialize(json, 'PetTreatment') }
        .must_raise PetstoreClient::SerializationError
    end
  end

  describe 'setPetAvatarThumbnail byte oneOf round-trips through base64' do
    # SetPetAvatarThumbnailRequest is a oneOf of [ a scalar `format: byte`
    # value, an array of `format: byte` values ]. `format: byte` means the
    # value travels on the wire as a base64 STRING — never a JSON int-array
    # ([1,2,3,4]), never the raw/unencoded bytes. The union must therefore
    # route each byte variant through the same base64 (de)serialization the
    # bare scalar `format: byte` field uses, preserving declared variant order
    # (scalar before array) so a base64 string is not mis-parsed element by
    # element into a list.
    #
    # The serialize side mirrors the API request-body path: a oneOf body is
    # routed through ObjectSerializer.encode_oneof_body before #serialize so
    # the byte variant is base64-encoded (a bare String otherwise serializes
    # as raw text). The deserialize side goes through the normal #deserialize
    # entry point, which dispatches to the union's #build.

    it 'serializes the scalar byte variant as a base64 string (not an int-array, not raw)' do
      raw = [0x01, 0x02, 0x03, 0x04].pack('C*')
      encoded = PetstoreClient::ObjectSerializer.encode_oneof_body(raw, 'SetPetAvatarThumbnailRequest')
      json = PetstoreClient::ObjectSerializer.serialize(encoded)

      _(json).must_equal('"AQIDBA=="')
      _(json).wont_equal('[1,2,3,4]')
      _(json).wont_include('[')
    end

    it 'deserializes a base64 string back to the original scalar bytes (round-trip)' do
      raw = [0x01, 0x02, 0x03, 0x04].pack('C*')
      result = PetstoreClient::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')

      _(result).must_be_kind_of(String)
      _(result.bytes).must_equal([0x01, 0x02, 0x03, 0x04])
      _(result.b).must_equal(raw.b)
    end

    it 'resolves a scalar base64 string to the SCALAR variant, not a list' do
      # Ordering guard: the scalar candidate is trialled before the array
      # candidate, so a lone base64 string decodes to one byte String — it is
      # NOT mis-parsed character/element by element into an Array.
      result = PetstoreClient::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')

      _(result).must_be_kind_of(String)
      _(result).wont_be_kind_of(Array)
    end

    it 'serializes the array byte variant as an array of base64 strings' do
      payload_a = [0x01, 0x02, 0x03, 0x04].pack('C*')
      payload_b = [0x05, 0x06, 0x07, 0x08].pack('C*')
      encoded = PetstoreClient::ObjectSerializer.encode_oneof_body([payload_a, payload_b], 'SetPetAvatarThumbnailRequest')
      json = PetstoreClient::ObjectSerializer.serialize(encoded)

      _(json).must_equal('["AQIDBA==","BQYHCA=="]')
    end

    it 'round-trips the array byte variant preserving both payloads exactly' do
      payload_a = [0x01, 0x02, 0x03, 0x04].pack('C*')
      payload_b = [0x05, 0x06, 0x07, 0x08].pack('C*')
      result = PetstoreClient::ObjectSerializer.deserialize('["AQIDBA==","BQYHCA=="]', 'SetPetAvatarThumbnailRequest')

      _(result).must_be_kind_of(Array)
      _(result.length).must_equal(2)
      _(result[0].bytes).must_equal([0x01, 0x02, 0x03, 0x04])
      _(result[1].bytes).must_equal([0x05, 0x06, 0x07, 0x08])
    end
  end

  describe 'allOf: PetWithOwner' do
    it 'deserializes all properties from allOf composition' do
      json = '{"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John","ownerEmail":"john@example.com"}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetWithOwner')

      _(result).must_be_kind_of(PetstoreClient::Models::PetWithOwner)
      _(result.name).must_equal('doggie')
      _(result.owner_name).must_equal('John')
      _(result.owner_email).must_equal('john@example.com')
    end

    it 'serializes PetWithOwner to JSON' do
      json = '{"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetWithOwner')
      serialized = PetstoreClient::ObjectSerializer.serialize(result)

      _(serialized).must_include('Fido')
      _(serialized).must_include('John Doe')
    end

    it 'round-trip preserves all fields' do
      json = '{"name":"Buddy","photoUrls":["http://example.com/buddy.jpg"],"ownerName":"Jane Smith"}'
      original = PetstoreClient::ObjectSerializer.deserialize(json, 'PetWithOwner')
      serialized = PetstoreClient::ObjectSerializer.serialize(original)
      restored = PetstoreClient::ObjectSerializer.deserialize(serialized, 'PetWithOwner')

      _(restored.name).must_equal(original.name)
      _(restored.owner_name).must_equal(original.owner_name)
    end
  end
end
