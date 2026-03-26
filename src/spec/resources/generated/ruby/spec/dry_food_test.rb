# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/autorun'
require 'json'
require 'petstore_client'

class DryFoodTest < Minitest::Test
  def test_requires_food_type_field
    assert_raises(Dry::Struct::Error) do
      PetstoreClient::Models::DryFood.new(weight_kg: 2.5)
    end
  end

  def test_requires_weight_kg_field
    assert_raises(Dry::Struct::Error) do
      PetstoreClient::Models::DryFood.new(food_type: 'kibble')
    end
  end

  def test_serializes_to_json
    food = PetstoreClient::Models::DryFood.new(
      food_type: 'kibble',
      weight_kg: 2.5
    )

    json_str = PetstoreClient::ObjectSerializer.serialize(food)
    parsed = JSON.parse(json_str)

    assert_equal 'kibble', parsed['foodType']
    assert_equal 2.5, parsed['weightKg']

    deserialized = PetstoreClient::ObjectSerializer.deserialize(json_str, 'DryFood')
    assert_equal 'kibble', deserialized.food_type
    assert_equal 2.5, deserialized.weight_kg
  end
end
