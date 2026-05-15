# frozen_string_literal: true

require 'test_helper'
require 'json'

describe 'Composed Schema' do # rubocop:disable Metrics/BlockLength
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

    it 'returns nil for unknown discriminator value' do
      json = '{"foodType":"raw","calories":300}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood')
      _(result).must_be_nil
    end

    it 'serializes DryFood back to JSON' do
      json = '{"foodType":"dry","weightKg":2.5}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood')
      serialized = PetstoreClient::ObjectSerializer.serialize(result)

      _(serialized).must_include('dry')
    end
  end

  describe 'anyOf without discriminator: PetTreatment' do
    it 'deserializes Medication from anyOf' do
      json = '{"drugName":"Amoxicillin","dosage":"500mg"}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetTreatment')

      _(result).must_be_kind_of(PetstoreClient::Models::Medication)
      _(result.drug_name).must_equal('Amoxicillin')
    end

    it 'deserializes Surgery from anyOf' do
      json = '{"procedureName":"Spay","durationMinutes":45}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetTreatment')

      _(result).must_be_kind_of(PetstoreClient::Models::Surgery)
    end

    it 'serializes round-trip for anyOf' do
      json = '{"drugName":"Amoxicillin","dosage":"500mg"}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetTreatment')
      serialized = PetstoreClient::ObjectSerializer.serialize(result)

      _(serialized).wont_be_empty
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
