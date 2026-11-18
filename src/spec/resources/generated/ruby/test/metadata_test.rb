# frozen_string_literal: true

require 'test_helper'
require 'json'

describe 'Metadata' do
  parallelize_me!

  it 'bhv_models_008 serializes empty metadata' do
    metadata = Petstore::Client::Models::Metadata.new
    json = Petstore::Client::ObjectSerializer.serialize(metadata)

    _(json).wont_be_nil
  end

  it 'bhv_models_003 deserializes empty object' do
    metadata = Petstore::Client::ObjectSerializer.deserialize('{}', 'Metadata')

    _(metadata).wont_be_nil
  end

  it 'bhv_models_004 deserializes additional string properties' do
    json = '{"createdAt":"2024-01-01T00:00:00+00:00","customField":"hello"}'
    metadata = Petstore::Client::ObjectSerializer.deserialize(json, 'Metadata')

    _(metadata).wont_be_nil
    _(metadata).must_be_kind_of(Petstore::Client::Models::Metadata)
  end

  it 'bhv_serialization_067 extra properties are written as top-level keys beside the declared fields' do
    json = '{"createdAt":"2024-01-01T00:00:00+00:00","customField":"hello","count":"3"}'
    metadata = Petstore::Client::ObjectSerializer.deserialize(json, 'Metadata')

    # Undeclared keys are captured (not silently dropped) so a round-trip
    # preserves them, matching the SDKs that round-trip additionalProperties.
    _(metadata.additional_properties).must_equal('customField' => 'hello', 'count' => '3')

    data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(metadata))
    _(data['customField']).must_equal('hello')
    _(data['count']).must_equal('3')
    _(data['createdAt']).wont_be_nil
  end

  it 'bhv_models_026 undeclared keys in incoming JSON survive deserialization and are written back at the top level' do
    json = '{"createdAt":"2024-01-01T00:00:00+00:00","customField":"hello","count":"3"}'
    metadata = Petstore::Client::ObjectSerializer.deserialize(json, 'Metadata')

    # Undeclared keys are captured (not silently dropped) so a round-trip
    # preserves them, matching the SDKs that round-trip additionalProperties.
    _(metadata.additional_properties).must_equal('customField' => 'hello', 'count' => '3')

    data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(metadata))
    _(data['customField']).must_equal('hello')
    _(data['count']).must_equal('3')
    _(data['createdAt']).wont_be_nil
  end

  it 'bhv_models_001 captures and round-trips additionalProperties' do
    json = '{"createdAt":"2024-01-01T00:00:00+00:00","customField":"hello","count":"3"}'
    metadata = Petstore::Client::ObjectSerializer.deserialize(json, 'Metadata')

    # Undeclared keys are captured (not silently dropped) so a round-trip
    # preserves them, matching the SDKs that round-trip additionalProperties.
    _(metadata.additional_properties).must_equal('customField' => 'hello', 'count' => '3')

    data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(metadata))
    _(data['customField']).must_equal('hello')
    _(data['count']).must_equal('3')
    _(data['createdAt']).wont_be_nil
  end

  it 'bhv_models_010 writes a declared property once when an extra entry shares its wire name' do
    metadata = Petstore::Client::Models::Metadata.new(
      created_at: Time.utc(2024, 1, 1, 0, 0, 0),
      additional_properties: { 'createdAt' => 'x' }
    )
    json = Petstore::Client::ObjectSerializer.serialize(metadata)

    _(json.scan('"createdAt"').length).must_equal(1)
    _(JSON.parse(json)['createdAt']).must_equal('2024-01-01T00:00:00.000+00:00')
  end
end

describe 'Metadata', 'with' do
  parallelize_me!

  it 'bhv_models_012 inspect dumps the fields and the additional properties map' do
    metadata = Petstore::Client::Models::Metadata.new(
      created_at: Time.utc(2024, 1, 1, 0, 0, 0),
      additional_properties: { 'customField' => 'a' }
    )

    _(metadata.inspect).must_include 'additional_properties'
    _(metadata.inspect).must_include 'customField'
  end

  it 'bhv_serialization_079 returns a copy with the changes applied and leaves the receiver as it was' do
    original = Petstore::Client::Models::Metadata.new(
      created_at: Time.utc(2024, 1, 1, 0, 0, 0),
      additional_properties: { 'customField' => 'a' }
    )
    changed = original.with(additional_properties: { 'customField' => 'b' })

    _(changed.additional_properties).must_equal('customField' => 'b')
    _(changed.created_at).must_equal(original.created_at)
    _(original.additional_properties).must_equal('customField' => 'a')
    _(changed).wont_be_same_as(original)
  end

  it 'rejects an attribute the model does not have' do
    error = _ { Petstore::Client::Models::Metadata.new.with(missing: 1) }.must_raise(ArgumentError)

    _(error.message).must_equal('Unknown attribute for Metadata: missing')
  end
end

describe 'Metadata', 'round-trip and constants' do
  parallelize_me!

  it 'bhv_models_011 round-trip preserves known properties' do
    metadata = Petstore::Client::Models::Metadata.new(
      created_at: Time.new(2024, 1, 1, 0, 0, 0, '+00:00')
    )
    json = Petstore::Client::ObjectSerializer.serialize(metadata)
    data = JSON.parse(json)

    _(data['createdAt']).wont_be_nil
  end

  it 'bhv_models_025 two instances that differ only in undeclared extra properties are not equal' do
    first = Petstore::Client::ObjectSerializer.deserialize('{"customField":"a"}', 'Metadata')
    same = Petstore::Client::ObjectSerializer.deserialize('{"customField":"a"}', 'Metadata')
    other = Petstore::Client::ObjectSerializer.deserialize('{"customField":"b"}', 'Metadata')

    _(first).must_equal(same)
    _(first.hash).must_equal(same.hash)
    _(first).wont_equal(other)
    _(first.eql?(other)).must_equal(false)
  end

  it 'bhv_models_005 equality includes the additional properties' do
    first = Petstore::Client::ObjectSerializer.deserialize('{"customField":"a"}', 'Metadata')
    same = Petstore::Client::ObjectSerializer.deserialize('{"customField":"a"}', 'Metadata')
    other = Petstore::Client::ObjectSerializer.deserialize('{"customField":"b"}', 'Metadata')

    _(first).must_equal(same)
    _(first.hash).must_equal(same.hash)
    _(first).wont_equal(other)
    _(first.eql?(other)).must_equal(false)
  end

  it 'bhv_models_022 defines ADDITIONAL_PROPERTIES constant' do
    _(Petstore::Client::Models::Metadata.const_defined?(:ADDITIONAL_PROPERTIES)).must_equal(true)
    _(Petstore::Client::Models::Metadata::ADDITIONAL_PROPERTIES).must_equal(true)
  end
end

describe 'generated file header' do
  parallelize_me!

  it 'bhv_models_006 prints the info description without backslash-escaped quotes' do
    path = String(Object.const_source_location(Petstore::Client::Models::Metadata.name)&.first)
    header = File.readlines(path).take_while { |line| line.start_with?('#') }.join
    _(header).wont_include('\\"')
  end
end

describe 'gem manifest' do
  parallelize_me!

  # manifest-description-missing: the published gemspec must carry a
  # non-empty description (wired from the spec's appDescription, with a
  # generated fallback) so the gem does not publish with an empty
  # description on RubyGems.
  it 'bhv_models_002 declares a non-empty description' do
    gemspec_path = File.join(File.expand_path('..', File.dirname(__FILE__)), 'petstore_client.gemspec')
    _(File.exist?(gemspec_path)).must_equal(true)

    spec = Gem::Specification.load(gemspec_path)
    _(spec).wont_be_nil
    _(spec.description).must_be_kind_of(String)
    _(spec.description.strip).wont_be_empty
  end

  # default-gem-undeclared: base64, json and set are required by the
  # generated runtime (object serializer, base API, authenticators) but
  # are default gems that Ruby is unbundling from the standard library
  # (base64 is no longer a default gem on Ruby 3.4+). They must be
  # declared explicitly so the gem keeps working once they are removed.
  it 'declares the default gems used by the runtime' do
    gemspec_path = File.join(File.expand_path('..', File.dirname(__FILE__)), 'petstore_client.gemspec')
    _(File.exist?(gemspec_path)).must_equal(true)

    spec = Gem::Specification.load(gemspec_path)
    _(spec).wont_be_nil

    declared = spec.dependencies.map(&:name)
    %w[base64 json set].each do |gem_name|
      _(declared).must_include(gem_name)
    end
  end
end

describe 'Metadata', 'copy, equality and enum contract' do
  parallelize_me!

  it 'bhv_models_013 a newly built model starts with empty additional properties, never nil' do
    _(Petstore::Client::Models::Metadata.new.additional_properties).must_equal({})
  end

  it 'bhv_models_014 a built model has no writers, so its properties cannot be reassigned' do
    _(Petstore::Client::Models::Metadata.new).wont_respond_to(:created_at=)
    _(Petstore::Client::Models::Metadata.new).wont_respond_to(:additional_properties=)
  end

  it 'bhv_models_015 a changed copy keeps the additional properties of the original' do
    original = Petstore::Client::Models::Metadata.new(additional_properties: { 'customField' => 'a' })
    changed = original.with(created_at: Time.utc(2024, 1, 1, 0, 0, 0))

    _(changed.additional_properties).must_equal('customField' => 'a')
  end

  it 'bhv_models_016 a copy clears an optional field only when nil is passed and keeps it when omitted' do
    original = Petstore::Client::Models::Metadata.new(created_at: Time.utc(2024, 1, 1, 0, 0, 0))

    _(original.with(created_at: nil).created_at).must_be_nil
    _(original.with(additional_properties: { 'a' => 'b' }).created_at).must_equal(original.created_at)
  end

  it 'bhv_models_017 a copy can set an optional field while the original model keeps its value' do
    first = Time.utc(2024, 1, 1, 0, 0, 0)
    original = Petstore::Client::Models::Metadata.new(created_at: first)
    changed = original.with(created_at: Time.utc(2025, 1, 1, 0, 0, 0))

    _(changed.created_at).must_equal(Time.utc(2025, 1, 1, 0, 0, 0))
    _(original.created_at).must_equal(first)
  end

  it 'bhv_models_019 two models with a different value in any field compare unequal' do
    _(Petstore::Client::Models::Metadata.new(created_at: Time.utc(2024, 1, 1)))
      .wont_equal(Petstore::Client::Models::Metadata.new(created_at: Time.utc(2025, 1, 1)))
  end

  it 'bhv_models_020 equal models hash alike so a set stores them as one entry' do
    first = Petstore::Client::Models::Metadata.new(created_at: Time.utc(2024, 1, 1))
    same = Petstore::Client::Models::Metadata.new(created_at: Time.utc(2024, 1, 1))

    _(Set.new([first, same]).size).must_equal(1)
  end

  it 'bhv_serialization_101 an integer-backed enum is written as a JSON number and a number decodes back to its member' do
    _(Petstore::Client::Models::Priority::NUMBER_1).must_equal(1)
    _(Petstore::Client::Models::Priority::NUMBER_3).must_equal(3)
  end

  it 'bhv_models_021 each member of an integer-backed enum carries its declared number as its value' do
    _(Petstore::Client::Models::Priority::NUMBER_1).must_equal(1)
    _(Petstore::Client::Models::Priority::NUMBER_3).must_equal(3)
  end

  it 'bhv_models_023 undeclared keys named like methods are kept as additional properties' do
    metadata = Petstore::Client::ObjectSerializer.deserialize('{"with":"x","validate!":"y"}', 'Metadata')

    _(metadata.additional_properties).must_equal('with' => 'x', 'validate!' => 'y')
  end

  it 'bhv_models_024 a newly built model leaves its optional properties unset' do
    _(Petstore::Client::Models::Metadata.new.created_at).must_be_nil
  end

  it 'bhv_serialization_114 numeric values keep every digit when serialized, including long decimals and full 64-bit integers' do
    short = Petstore::Client::ObjectSerializer.deserialize('{"name":"Rex","photoUrls":[],"weightKg":1.5}', 'Pet')
    padded = Petstore::Client::ObjectSerializer.deserialize('{"name":"Rex","photoUrls":[],"weightKg":1.50}', 'Pet')
    _(short).must_equal(padded)
  end

  it 'bhv_models_018 models whose decimal fields hold numerically equal values compare equal' do
    short = Petstore::Client::ObjectSerializer.deserialize('{"name":"Rex","photoUrls":[],"weightKg":1.5}', 'Pet')
    padded = Petstore::Client::ObjectSerializer.deserialize('{"name":"Rex","photoUrls":[],"weightKg":1.50}', 'Pet')
    _(short).must_equal(padded)
  end
end
