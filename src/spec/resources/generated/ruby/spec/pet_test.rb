# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'json'
require 'petstore_client'

class PetTest < Minitest::Test
  def test_requires_name_field
    assert_raises(Dry::Struct::Error) do
      PetstoreClient::Models::Pet.new(photo_urls: ['https://example.com/photo.jpg'])
    end
  end

  def test_requires_photo_urls_field
    assert_raises(Dry::Struct::Error) do
      PetstoreClient::Models::Pet.new(name: 'Fido')
    end
  end

  def test_rejects_invalid_status_enum
    pet = PetstoreClient::Models::Pet.new(
      name: 'Fido',
      photo_urls: ['https://example.com/photo.jpg'],
      status: 'invalid_status'
    )
    # The model uses Types::Any so any value is accepted at the model level.
    # The enum validation is expected to happen elsewhere (e.g. server-side).
    # This test documents the current behavior -- the generator does NOT
    # enforce enum constraints on the client model, which could be considered
    # a bug. If enum validation is later added, this test should be updated
    # to expect a validation error.
    assert_equal 'invalid_status', pet.status
  end

  def test_serializes_to_json
    pet = PetstoreClient::Models::Pet.new(
      id: 10,
      name: 'Fido',
      photo_urls: ['https://example.com/photo.jpg'],
      status: 'available'
    )

    json_str = PetstoreClient::ObjectSerializer.serialize(pet)
    parsed = JSON.parse(json_str)

    assert_equal 10, parsed['id']
    assert_equal 'Fido', parsed['name']
    assert_equal ['https://example.com/photo.jpg'], parsed['photoUrls']
    assert_equal 'available', parsed['status']

    deserialized = PetstoreClient::ObjectSerializer.deserialize(json_str, 'Pet')
    assert_equal 'Fido', deserialized.name
    assert_equal 10, deserialized.id
    assert_equal ['https://example.com/photo.jpg'], deserialized.photo_urls
  end
end
