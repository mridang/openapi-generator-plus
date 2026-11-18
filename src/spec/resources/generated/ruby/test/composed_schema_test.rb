# frozen_string_literal: true

require 'test_helper'
require 'json'

describe 'Composed Schema' do
  parallelize_me!

  describe 'oneOf with discriminator: PetFood' do
    it 'bhv_unions_012 deserializes to DryFood via discriminator' do
      json = '{"foodType":"dry","weightKg":2.5}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood')

      _(result).must_be_kind_of(Petstore::Client::Models::DryFood)
    end

    it 'bhv_unions_001 offers a typed accessor, predicate and factory per variant' do
      food = Petstore::Client::ObjectSerializer.deserialize('{"foodType":"dry","weightKg":2.5}', 'PetFood')

      _(Petstore::Client::Models::PetFood.as_dry_food(food)).must_be_same_as(food)
      assert_nil(Petstore::Client::Models::PetFood.as_wet_food(food))
      _(Petstore::Client::Models::PetFood.dry_food_variant?(food)).must_equal(true)
      _(Petstore::Client::Models::PetFood.wet_food_variant?(food)).must_equal(false)
    end

    it 'bhv_langruby_026 builds a variant through its typed factory and rejects another type' do
      dry = Petstore::Client::Models::DryFood.new(weight_kg: 2.5)

      _(Petstore::Client::Models::PetFood.from_dry_food(dry)).must_be_same_as(dry)
      error = _ { Petstore::Client::Models::PetFood.from_wet_food(dry) }.must_raise(ArgumentError) # steep:ignore ArgumentTypeMismatch
      _(error.message).must_equal("Expected a WetFood for PetFood, got #{dry.class}")
    end

    it 'bhv_unions_013 deserializes to WetFood via discriminator' do
      json = '{"foodType":"wet","volumeMl":400}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood')

      _(result).must_be_kind_of(Petstore::Client::Models::WetFood)
    end

    it 'bhv_unions_016 raises SerializationError for unknown discriminator value' do
      # Gap L / 4.7: union deserialise with an unmapped discriminator
      # raises SerializationError instead of falling through to the
      # base type. Aligns Ruby with Python / Swift / Dart / Go / Rust.
      json = '{"foodType":"raw","calories":300}'
      _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_serialization_082 a missing or null discriminator property fails decoding and names the missing key' do
      json = '{"weightKg":2.5}'
      _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_unions_015 raises SerializationError for missing discriminator' do
      json = '{"weightKg":2.5}'
      _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_unions_024 raises SerializationError for empty discriminator value' do
      # An empty discriminator value matches no listed mapping and must raise
      # rather than route to a structurally-fitting variant.
      json = '{"foodType":"","weightKg":2.5}'
      _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_unions_026 unknown discriminator error names the offending value' do
      # CS4: the error message must surface the offending discriminator value
      # so callers can diagnose server/spec drift.
      json = '{"foodType":"raw","calories":300}'
      err = _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
            .must_raise Petstore::Client::Errors::SerializationError
      _(err.message).must_include('raw')
    end

    it 'bhv_serializationobject_128 returns the variant the discriminator selects' do
      result = Petstore::Client::ObjectSerializer.deserialize('{"foodType":"dry","weightKg":2.5}', 'PetFood')

      _(result).must_be_kind_of(Petstore::Client::Models::DryFood)
    end

    it 'bhv_serializationobject_129 raises SerializationError when no oneOf variant matches' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{"foodType":"raw","calories":300}', 'PetFood') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_unions_020 serializes DryFood back to JSON' do
      json = '{"foodType":"dry","weightKg":2.5}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood')
      serialized = Petstore::Client::ObjectSerializer.serialize(result)

      _(serialized).must_include('dry')
    end

    it 'bhv_unions_045 two union values built from the same data compare equal' do
      left = Petstore::Client::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')
      right = Petstore::Client::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')
      other = Petstore::Client::ObjectSerializer.deserialize('"BQYHCA=="', 'SetPetAvatarThumbnailRequest')
      _(left).must_equal(right)
      _(left.hash).must_equal(right.hash)
      _(left).wont_equal(other)

      food = Petstore::Client::ObjectSerializer.deserialize('{"foodType":"dry","weightKg":2.5}', 'PetFood')
      same_food = Petstore::Client::ObjectSerializer.deserialize('{"foodType":"dry","weightKg":2.5}', 'PetFood')
      _(food).must_equal(same_food)
      _(food.hash).must_equal(same_food.hash)
    end

    it 'bhv_unions_029 oneOf wrappers have value equality' do
      left = Petstore::Client::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')
      right = Petstore::Client::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')
      other = Petstore::Client::ObjectSerializer.deserialize('"BQYHCA=="', 'SetPetAvatarThumbnailRequest')
      _(left).must_equal(right)
      _(left.hash).must_equal(right.hash)
      _(left).wont_equal(other)

      food = Petstore::Client::ObjectSerializer.deserialize('{"foodType":"dry","weightKg":2.5}', 'PetFood')
      same_food = Petstore::Client::ObjectSerializer.deserialize('{"foodType":"dry","weightKg":2.5}', 'PetFood')
      _(food).must_equal(same_food)
      _(food.hash).must_equal(same_food.hash)
    end

    it 'bhv_misc_294 a subtype payload without its discriminator decodes as the variant the mapping names' do
      food = Petstore::Client::ObjectSerializer.deserialize('{"weightKg":2.5}', 'DryFood')

      _(food).must_be_kind_of(Petstore::Client::Models::DryFood)
      _(food.food_type).must_equal('dry')
    end

    it 'bhv_unions_030 a subtype whose discriminator key is absent is filled with the mapping default' do
      food = Petstore::Client::ObjectSerializer.deserialize('{"weightKg":2.5}', 'DryFood')

      _(food.food_type).must_equal('dry')
    end

    it 'bhv_unions_032 a oneOf wrapper built from a variant holds it and serializes it with its discriminator' do
      dry = Petstore::Client::Models::DryFood.new(weight_kg: 2.5)
      food = Petstore::Client::Models::PetFood.from_dry_food(dry)

      _(food).must_be_same_as(dry)
      serialized = Petstore::Client::ObjectSerializer.serialize(food)
      _(JSON.parse(serialized)).must_equal('foodType' => 'dry', 'weightKg' => 2.5)
    end

    it 'bhv_misc_047 serializing a union holding a value that is not one of its variants fails naming the union' do
      [
        '{"foodType":"raw","calories":300}',
        '{"weightKg":2.5}',
        '{"foodType":"","weightKg":2.5}'
      ].each do |json|
        _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
          .must_raise Petstore::Client::Errors::SerializationError
      end
    end

    it 'bhv_unions_033 an unknown, missing or empty discriminator fails with the SDK serialization error' do
      [
        '{"foodType":"raw","calories":300}',
        '{"weightKg":2.5}',
        '{"foodType":"","weightKg":2.5}'
      ].each do |json|
        _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
          .must_raise Petstore::Client::Errors::SerializationError
      end
    end

    it 'bhv_unions_034 a oneOf wrapper typed accessor returns the held variant and nothing for the other' do
      wet = Petstore::Client::Models::WetFood.new(volume_ml: 400)
      food = Petstore::Client::Models::PetFood.from_wet_food(wet)

      _(Petstore::Client::Models::PetFood.as_wet_food(food)).must_be_same_as(wet)
      assert_nil(Petstore::Client::Models::PetFood.as_dry_food(food))
    end

    it 'bhv_unions_047 a discriminator value not in the mapping, including an empty one, raises a clear error' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{"foodType":"frozen","weightKg":3.0}', 'PetFood') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_unions_035 a discriminator value not listed in the mapping fails even when the payload fits a variant' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{"foodType":"frozen","weightKg":3.0}', 'PetFood') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_unions_037 a union built from one of its variants holds that exact value and returns it unchanged' do
      dry = Petstore::Client::Models::DryFood.new(weight_kg: 2.5)
      food = Petstore::Client::Models::PetFood.from_dry_food(dry)

      _(food).must_be_same_as(dry)
      _(food.weight_kg).must_equal(2.5)
      _(Petstore::Client::Models::PetFood.singleton_methods.grep(/\Aset_|=\z/)).must_be_empty
    end

    it 'bhv_misc_045 a payload that does not satisfy a variant schema is not accepted as that variant' do
      [
        '{"foodType":3,"weightKg":2.5}',
        '{"FoodType":"dry","weightKg":2.5}',
        '{"foodType":"fish","weightKg":2.5}',
        '{"foodType":"dry"}'
      ].each do |json|
        _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
          .must_raise Petstore::Client::Errors::SerializationError
      end
    end

    it 'bhv_unions_038 a union body that cannot be decoded raises the SDK serialization error' do
      [
        '{"foodType":3,"weightKg":2.5}',
        '{"FoodType":"dry","weightKg":2.5}',
        '{"foodType":"fish","weightKg":2.5}',
        '{"foodType":"dry"}'
      ].each do |json|
        _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood') }
          .must_raise Petstore::Client::Errors::SerializationError
      end
    end

    it 'bhv_unions_044 each union variant has a typed factory and accessor that identify the variant' do
      food = Petstore::Client::ObjectSerializer.deserialize('{"foodType":"dry","weightKg":2.5}', 'PetFood')

      _(food).must_be_kind_of(Petstore::Client::Models::DryFood)
      _(Petstore::Client::Models::PetFood.as_dry_food(food)).must_be_same_as(food)
      assert_nil(Petstore::Client::Models::PetFood.as_wet_food(food))
      _(Petstore::Client::Models::PetFood.wet_food_variant?(food)).must_equal(false)
    end

    it 'bhv_unions_040 a typed accessor returns the variant a decoded union holds and nothing for the other' do
      food = Petstore::Client::ObjectSerializer.deserialize('{"foodType":"dry","weightKg":2.5}', 'PetFood')

      _(food).must_be_kind_of(Petstore::Client::Models::DryFood)
      _(Petstore::Client::Models::PetFood.as_dry_food(food)).must_be_same_as(food)
      assert_nil(Petstore::Client::Models::PetFood.as_wet_food(food))
      _(Petstore::Client::Models::PetFood.wet_food_variant?(food)).must_equal(false)
    end

    it 'bhv_unions_042 a union holding no variant answers false to every predicate and nil to every accessor' do
      _(Petstore::Client::Models::PetFood.dry_food_variant?(nil)).must_equal(false)
      _(Petstore::Client::Models::PetFood.wet_food_variant?(nil)).must_equal(false)
      assert_nil(Petstore::Client::Models::PetFood.as_dry_food(nil))
      assert_nil(Petstore::Client::Models::PetFood.as_wet_food(nil))
    end

    it 'bhv_unions_043 a discriminator value already set on a subtype is written as given' do
      food = Petstore::Client::Models::DryFood.new(food_type: 'custom', weight_kg: 2.5)
      serialized = Petstore::Client::ObjectSerializer.serialize(food)

      _(JSON.parse(serialized)['foodType']).must_equal('custom')
    end
  end

  describe 'anyOf without discriminator: PetTreatment' do
    it 'bhv_unions_014 deserializes a medication-only payload to the bare Medication variant' do
      # Single-variant match: exactly one variant decodes, so build returns
      # that bare instance (NOT a composite) — existing single-variant callers
      # are unaffected by the retain-all change.
      json = '{"drugName":"Amoxicillin","dosage":"500mg"}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetTreatment')

      _(result).must_be_kind_of(Petstore::Client::Models::Medication)
      _(result.drug_name).must_equal('Amoxicillin')
    end

    it 'bhv_unions_006 deserializes a surgery-only payload to the bare Surgery variant' do
      json = '{"procedureName":"Spay","durationMinutes":45}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetTreatment')

      _(result).must_be_kind_of(Petstore::Client::Models::Surgery)
      _(result.procedure_name).must_equal('Spay')
      _(result.duration_minutes).must_equal(45)
    end

    it 'bhv_unions_046 each matching anyOf variant reports itself as matched and keeps its value' do
      # PetTreatment is documented as "a medication, a surgery, OR BOTH". A
      # payload satisfying BOTH variants at once must retain BOTH — the old
      # first-match build silently dropped the Surgery fields and lost them on
      # re-encode. Assert every field of both variants is reachable on the
      # decoded value, then re-serialize and assert all four fields survive the
      # round-trip (no silent drop).
      json = '{"drugName":"Amoxicillin","dosage":"250mg","procedureName":"Spay","durationMinutes":45}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetTreatment')

      # Both variants' data are accessible off the single decoded value.
      _(result.drug_name).must_equal('Amoxicillin')
      _(result.dosage).must_equal('250mg')
      _(result.procedure_name).must_equal('Spay')
      _(result.duration_minutes).must_equal(45)

      # Re-serialize: the union of ALL FOUR fields round-trips losslessly.
      serialized = Petstore::Client::ObjectSerializer.serialize(result)
      reparsed = JSON.parse(serialized)
      _(reparsed['drugName']).must_equal('Amoxicillin')
      _(reparsed['dosage']).must_equal('250mg')
      _(reparsed['procedureName']).must_equal('Spay')
      _(reparsed['durationMinutes']).must_equal(45)
    end

    it 'bhv_unions_009 anyOf retains all matching variants (medication and surgery) losslessly' do
      # PetTreatment is documented as "a medication, a surgery, OR BOTH". A
      # payload satisfying BOTH variants at once must retain BOTH — the old
      # first-match build silently dropped the Surgery fields and lost them on
      # re-encode. Assert every field of both variants is reachable on the
      # decoded value, then re-serialize and assert all four fields survive the
      # round-trip (no silent drop).
      json = '{"drugName":"Amoxicillin","dosage":"250mg","procedureName":"Spay","durationMinutes":45}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetTreatment')

      # Both variants' data are accessible off the single decoded value.
      _(result.drug_name).must_equal('Amoxicillin')
      _(result.dosage).must_equal('250mg')
      _(result.procedure_name).must_equal('Spay')
      _(result.duration_minutes).must_equal(45)

      # Re-serialize: the union of ALL FOUR fields round-trips losslessly.
      serialized = Petstore::Client::ObjectSerializer.serialize(result)
      reparsed = JSON.parse(serialized)
      _(reparsed['drugName']).must_equal('Amoxicillin')
      _(reparsed['dosage']).must_equal('250mg')
      _(reparsed['procedureName']).must_equal('Spay')
      _(reparsed['durationMinutes']).must_equal(45)
    end

    it 'bhv_unions_010 anyOf collision keeps the last-declared variant, emitted once' do
      # anyOf-collision-last-wins: both Medication and Surgery declare `notes`.
      # When a PetTreatment retains both members with DIFFERENT `notes` values,
      # the merged encoding keeps the LAST-declared member's value (Surgery) and
      # emits `notes` exactly once — never a duplicate JSON key.
      medication = Petstore::Client::Models::Medication.new(drug_name: 'Amoxicillin', notes: 'from-medication')
      surgery = Petstore::Client::Models::Surgery.new(procedure_name: 'Spay', notes: 'from-surgery')
      composite = Petstore::Client::Models::PetTreatment::Composite.new([medication, surgery])

      serialized = Petstore::Client::ObjectSerializer.serialize(composite)
      reparsed = JSON.parse(serialized)

      # Surgery is the later-declared member, so its `notes` wins the collision.
      _(reparsed['notes']).must_equal('from-surgery')
      # `notes` appears exactly once — no duplicate key.
      _(serialized.scan('"notes"').length).must_equal(1)
    end

    it 'bhv_misc_046 two unions holding equal content compare equal and hash equally' do
      build = lambda do
        medication = Petstore::Client::Models::Medication.new(drug_name: 'Amoxicillin')
        surgery = Petstore::Client::Models::Surgery.new(procedure_name: 'Spay')
        Petstore::Client::Models::PetTreatment::Composite.new([medication, surgery])
      end
      first = build.call
      second = build.call
      _(first).must_equal(second)
      _(first.eql?(second)).must_equal true
      _(first.hash).must_equal(second.hash)
      _([first, second].uniq.length).must_equal 1
      other = Petstore::Client::Models::PetTreatment::Composite.new([Petstore::Client::Models::Surgery.new(procedure_name: 'Spay')])
      _(first).wont_equal(other)
    end

    it 'bhv_unions_011 anyOf composites have value equality and a matching hash' do
      build = lambda do
        medication = Petstore::Client::Models::Medication.new(drug_name: 'Amoxicillin')
        surgery = Petstore::Client::Models::Surgery.new(procedure_name: 'Spay')
        Petstore::Client::Models::PetTreatment::Composite.new([medication, surgery])
      end
      first = build.call
      second = build.call
      _(first).must_equal(second)
      _(first.eql?(second)).must_equal true
      _(first.hash).must_equal(second.hash)
      _([first, second].uniq.length).must_equal 1
      other = Petstore::Client::Models::PetTreatment::Composite.new([Petstore::Client::Models::Surgery.new(procedure_name: 'Spay')])
      _(first).wont_equal(other)
    end

    it 'bhv_misc_293 an anyOf value keeps every matching variant and skips the ones that do not match' do
      both = Petstore::Client::ObjectSerializer.deserialize(
        '{"drugName":"Amoxicillin","dosage":"250mg","procedureName":"Spay","durationMinutes":45}', 'PetTreatment'
      )
      surgery_only = Petstore::Client::ObjectSerializer.deserialize(
        '{"procedureName":"Spay","durationMinutes":45}', 'PetTreatment'
      )

      _(Petstore::Client::Models::PetTreatment.matched_instances(both).map(&:class))
        .must_equal([Petstore::Client::Models::Medication, Petstore::Client::Models::Surgery])
      _(Petstore::Client::Models::PetTreatment.matched_instances(surgery_only).map(&:class))
        .must_equal([Petstore::Client::Models::Surgery])
    end

    it 'bhv_unions_003 exposes actual_instance and matched_instances for anyOf values' do
      both = Petstore::Client::ObjectSerializer.deserialize(
        '{"drugName":"Amoxicillin","dosage":"250mg","procedureName":"Spay","durationMinutes":45}', 'PetTreatment'
      )
      _(Petstore::Client::Models::PetTreatment.matched_instances(both).map(&:class))
        .must_equal([Petstore::Client::Models::Medication, Petstore::Client::Models::Surgery])
      _(Petstore::Client::Models::PetTreatment.actual_instance(both)).must_be_kind_of(Petstore::Client::Models::Medication)

      single = Petstore::Client::ObjectSerializer.deserialize('{"drugName":"Amoxicillin","dosage":"500mg"}', 'PetTreatment')
      _(Petstore::Client::Models::PetTreatment.matched_instances(single)).must_equal([single])
      _(Petstore::Client::Models::PetTreatment.actual_instance(single)).must_be_same_as(single)
      _(Petstore::Client::Models::PetTreatment.actual_instance(nil)).must_be_nil
      _(Petstore::Client::Models::PetTreatment.matched_instances(nil)).must_equal([])
    end

    it 'bhv_unions_008 serializes round-trip for a single-variant anyOf payload' do
      json = '{"drugName":"Amoxicillin","dosage":"500mg"}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetTreatment')
      serialized = Petstore::Client::ObjectSerializer.serialize(result)

      _(serialized).wont_be_empty
      _(serialized).must_include('Amoxicillin')
    end

    it 'bhv_serializationobject_127 raises SerializationError when no anyOf variant matches' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{}', 'PetTreatment') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_serialization_076 a union value that matches none of its candidates raises a serialization error' do
      # oneof-nondiscriminator-no-match-silent: a body matching neither
      # Medication nor Surgery must raise rather than return a silently-empty
      # union. The anyOf build raises on union no-match, and deserialize wraps
      # every schema-validation failure uniformly as SerializationError so a
      # caller can rescue a single branded type.
      json = '{"unrelatedKey":"value","anotherUnknown":123}'
      _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetTreatment') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_unions_007 raises for anyOf payload matching no variant' do
      # oneof-nondiscriminator-no-match-silent: a body matching neither
      # Medication nor Surgery must raise rather than return a silently-empty
      # union. The anyOf build raises on union no-match, and deserialize wraps
      # every schema-validation failure uniformly as SerializationError so a
      # caller can rescue a single branded type.
      json = '{"unrelatedKey":"value","anotherUnknown":123}'
      _ { Petstore::Client::ObjectSerializer.deserialize(json, 'PetTreatment') }
        .must_raise Petstore::Client::Errors::SerializationError
    end
  end

  # D64: the same members as PetTreatment declared in the opposite order. The
  # variants are tried in declaration order, so Surgery comes first and the
  # later declared Medication wins a collision on a shared property.
  describe 'anyOf variants are tried in declaration order: TreatmentRecord' do
    it 'bhv_unions_005 lists the variants in the order the spec declares them' do
      _(Petstore::Client::Models::TreatmentRecord.openapi_any_of).must_equal(%i[Surgery Medication])
    end

    it 'bhv_unions_025 retains the first declared variant first' do
      both = Petstore::Client::ObjectSerializer.deserialize('{"drugName":"a","procedureName":"b"}', 'TreatmentRecord')
      _(Petstore::Client::Models::TreatmentRecord.matched_instances(both).map(&:class))
        .must_equal([Petstore::Client::Models::Surgery, Petstore::Client::Models::Medication])
      _(Petstore::Client::Models::TreatmentRecord.actual_instance(both)).must_be_kind_of(Petstore::Client::Models::Surgery)
    end

    it 'bhv_unions_002 keeps the later declared variant on a collision, emitted once' do
      surgery = Petstore::Client::Models::Surgery.new(procedure_name: 'Spay', notes: 'from-surgery')
      medication = Petstore::Client::Models::Medication.new(drug_name: 'Amoxicillin', notes: 'from-medication')
      composite = Petstore::Client::Models::TreatmentRecord::Composite.new([surgery, medication])

      serialized = Petstore::Client::ObjectSerializer.serialize(composite)

      _(JSON.parse(serialized)['notes']).must_equal('from-medication')
      _(serialized.scan('"notes"').length).must_equal(1)
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

    it 'bhv_serialization_088 byte values are written as base64 strings, including each element of a list' do
      raw = [0x01, 0x02, 0x03, 0x04].pack('C*')
      encoded = Petstore::Client::ObjectSerializer.encode_oneof_body(raw, 'SetPetAvatarThumbnailRequest')
      json = Petstore::Client::ObjectSerializer.serialize(encoded)

      _(json).must_equal('"AQIDBA=="')
      _(json).wont_equal('[1,2,3,4]')
      _(json).wont_include('[')
    end

    it 'bhv_misc_049 a choice between one binary value and a list writes the list as base64 text items' do
      raw = [0x01, 0x02, 0x03, 0x04].pack('C*')
      encoded = Petstore::Client::ObjectSerializer.encode_oneof_body(raw, 'SetPetAvatarThumbnailRequest')
      json = Petstore::Client::ObjectSerializer.serialize(encoded)

      _(json).must_equal('"AQIDBA=="')
      _(json).wont_equal('[1,2,3,4]')
      _(json).wont_include('[')
    end

    it 'bhv_unions_019 serializes the scalar byte variant as a base64 string (not an int-array, not raw)' do
      raw = [0x01, 0x02, 0x03, 0x04].pack('C*')
      encoded = Petstore::Client::ObjectSerializer.encode_oneof_body(raw, 'SetPetAvatarThumbnailRequest')
      json = Petstore::Client::ObjectSerializer.serialize(encoded)

      _(json).must_equal('"AQIDBA=="')
      _(json).wont_equal('[1,2,3,4]')
      _(json).wont_include('[')
    end

    it 'bhv_serialization_089 a base64 string on the wire decodes into a byte value' do
      raw = [0x01, 0x02, 0x03, 0x04].pack('C*')
      result = Petstore::Client::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')

      _(result).must_be_kind_of(String)
      _(result.bytes).must_equal([0x01, 0x02, 0x03, 0x04])
      _(result.b).must_equal(raw.b)
    end

    it 'bhv_unions_023 deserializes a base64 string back to the original scalar bytes (round-trip)' do
      raw = [0x01, 0x02, 0x03, 0x04].pack('C*')
      result = Petstore::Client::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')

      _(result).must_be_kind_of(String)
      _(result.bytes).must_equal([0x01, 0x02, 0x03, 0x04])
      _(result.b).must_equal(raw.b)
    end

    it 'bhv_unions_018 resolves a scalar base64 string to the SCALAR variant, not a list' do
      # Ordering guard: the scalar candidate is trialled before the array
      # candidate, so a lone base64 string decodes to one byte String — it is
      # NOT mis-parsed character/element by element into an Array.
      result = Petstore::Client::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')

      _(result).must_be_kind_of(String)
      _(result).wont_be_kind_of(Array)
    end

    it 'bhv_misc_295 a byte-string variant round-trips through base64 and a non-list value is refused for the array variant' do
      encoded = Petstore::Client::ObjectSerializer.encode_oneof_body([[0x01, 0x02].pack('C*')], 'SetPetAvatarThumbnailRequest')
      json = Petstore::Client::ObjectSerializer.serialize(encoded)
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'SetPetAvatarThumbnailRequest')

      _(json).must_equal('["AQI="]')
      _(result.map(&:bytes)).must_equal([[0x01, 0x02]])
      _ { Petstore::Client::ObjectSerializer.deserialize('{"x":1}', 'SetPetAvatarThumbnailRequest') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_langruby_027 serializes the array byte variant as an array of base64 strings' do
      payload_a = [0x01, 0x02, 0x03, 0x04].pack('C*')
      payload_b = [0x05, 0x06, 0x07, 0x08].pack('C*')
      encoded = Petstore::Client::ObjectSerializer.encode_oneof_body([payload_a, payload_b], 'SetPetAvatarThumbnailRequest')
      json = Petstore::Client::ObjectSerializer.serialize(encoded)

      _(json).must_equal('["AQIDBA==","BQYHCA=="]')
    end
  end

  describe 'allOf: PetWithOwner' do
    it 'bhv_unions_004 deserializes all properties from allOf composition' do
      json = '{"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John","ownerEmail":"john@example.com"}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetWithOwner')

      _(result).must_be_kind_of(Petstore::Client::Models::PetWithOwner)
      _(result.name).must_equal('doggie')
      _(result.owner_name).must_equal('John')
      _(result.owner_email).must_equal('john@example.com')
    end

    it 'bhv_unions_021 serializes PetWithOwner to JSON' do
      json = '{"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetWithOwner')
      serialized = Petstore::Client::ObjectSerializer.serialize(result)

      _(serialized).must_include('Fido')
      _(serialized).must_include('John Doe')
    end

    it 'bhv_unions_017 round-trip preserves all fields' do
      json = '{"name":"Buddy","photoUrls":["http://example.com/buddy.jpg"],"ownerName":"Jane Smith"}'
      original = Petstore::Client::ObjectSerializer.deserialize(json, 'PetWithOwner')
      serialized = Petstore::Client::ObjectSerializer.serialize(original)
      restored = Petstore::Client::ObjectSerializer.deserialize(serialized, 'PetWithOwner')

      _(restored.name).must_equal(original.name)
      _(restored.owner_name).must_equal(original.owner_name)
    end
  end

  describe 'allOf with a discriminator: Vehicle and Shape' do
    it 'bhv_unions_301 decodes the base to the subtype its explicit mapping names' do
      json = '{"vehicleType":"car","plate":"AB-12","doorCount":4}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'Vehicle')

      _(result).must_be_kind_of(Petstore::Client::Models::Car)
      _(result).must_be_kind_of(Petstore::Client::Models::Vehicle)
      _(result.plate).must_equal('AB-12')
      _(result.door_count).must_equal(4)
      _(Petstore::Client::ObjectSerializer.deserialize('{"vehicleType":"truck","plate":"T-1"}', 'Vehicle'))
        .must_be_kind_of(Petstore::Client::Models::Truck)
    end

    it 'bhv_unions_302 decodes the base to the subtype its implicit schema name names' do
      result = Petstore::Client::ObjectSerializer.deserialize('{"shapeType":"Circle","radius":1.5}', 'Shape')

      _(result).must_be_kind_of(Petstore::Client::Models::Circle)
      _(result).must_be_kind_of(Petstore::Client::Models::Shape)
      _(result.radius).must_equal(1.5)
    end

    it 'bhv_unions_303 fails on an unknown discriminator value and the error names it' do
      json = '{"vehicleType":"boat","plate":"B-1"}'
      err = _ { Petstore::Client::ObjectSerializer.deserialize(json, 'Vehicle') }
            .must_raise Petstore::Client::Errors::SerializationError
      _(err.message).must_include('boat')
    end

    it 'bhv_unions_304 fails when the base payload has no discriminator' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{"plate":"AB-12"}', 'Vehicle') }
        .must_raise Petstore::Client::Errors::SerializationError
    end

    it 'bhv_serializationobject_301 writes the discriminator value when a subtype is serialized' do
      car = Petstore::Client::Models::Car.new(plate: 'AB-12', door_count: 4)
      serialized = Petstore::Client::ObjectSerializer.serialize(car)

      _(serialized).must_include('"vehicleType":"car"')
    end
  end

  it 'bhv_unions_022 round-trips an array of byte values through base64 strings' do
    raws = [[0x01, 0x02].pack('C*'), [0x03, 0x04].pack('C*')]
    encoded = Petstore::Client::ObjectSerializer.encode_oneof_body(raws, 'SetPetAvatarThumbnailRequest')
    json = Petstore::Client::ObjectSerializer.serialize(encoded)

    _(json).must_equal('["AQI=","AwQ="]')
    result = Petstore::Client::ObjectSerializer.deserialize(json, 'SetPetAvatarThumbnailRequest')
    _(result.map(&:bytes)).must_equal([[0x01, 0x02], [0x03, 0x04]])
  end

  it 'bhv_unions_041 a oneOf without a discriminator decodes each JSON shape to its own variant' do
    single = Petstore::Client::ObjectSerializer.deserialize('"AQIDBA=="', 'SetPetAvatarThumbnailRequest')
    many = Petstore::Client::ObjectSerializer.deserialize('["AQIDBA==","BQYHCA=="]', 'SetPetAvatarThumbnailRequest')

    _(single).must_be_kind_of(String)
    _(single.bytes).must_equal([0x01, 0x02, 0x03, 0x04])
    _(many).must_be_kind_of(Array)
    _(many.map(&:bytes)).must_equal([[0x01, 0x02, 0x03, 0x04], [0x05, 0x06, 0x07, 0x08]])
  end
end
