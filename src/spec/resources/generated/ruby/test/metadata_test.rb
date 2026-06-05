# frozen_string_literal: true

require 'test_helper'
require 'json'

describe PetstoreClient::Models::Metadata do
  parallelize_me!

  it 'serializes empty metadata' do
    metadata = PetstoreClient::Models::Metadata.new
    json = PetstoreClient::ObjectSerializer.serialize(metadata)

    _(json).wont_be_nil
  end

  it 'deserializes empty object' do
    metadata = PetstoreClient::ObjectSerializer.deserialize('{}', 'Metadata')

    _(metadata).wont_be_nil
  end

  it 'deserializes additional string properties' do
    json = '{"createdAt":"2024-01-01T00:00:00+0000","customField":"hello"}'
    metadata = PetstoreClient::ObjectSerializer.deserialize(json, 'Metadata')

    _(metadata).wont_be_nil
    _(metadata).must_be_kind_of(PetstoreClient::Models::Metadata)
  end
end

describe PetstoreClient::Models::Metadata, 'round-trip and constants' do
  parallelize_me!

  it 'round-trip preserves known properties' do
    metadata = PetstoreClient::Models::Metadata.new(
      created_at: Time.new(2024, 1, 1, 0, 0, 0, '+00:00')
    )
    json = PetstoreClient::ObjectSerializer.serialize(metadata)
    data = JSON.parse(json)

    _(data['createdAt']).wont_be_nil
  end

  it 'defines ADDITIONAL_PROPERTIES constant' do
    _(PetstoreClient::Models::Metadata.const_defined?(:ADDITIONAL_PROPERTIES)).must_equal(true)
    _(PetstoreClient::Models::Metadata::ADDITIONAL_PROPERTIES).must_equal(true)
  end
end

describe 'gem manifest' do
  parallelize_me!

  # manifest-description-missing: the published gemspec must carry a
  # non-empty description (wired from the spec's appDescription, with a
  # generated fallback) so the gem does not publish with an empty
  # description on RubyGems.
  it 'declares a non-empty description' do
    gemspec_path = File.join(File.expand_path('..', __dir__), 'petstore_client.gemspec')
    _(File.exist?(gemspec_path)).must_equal(true)

    spec = Gem::Specification.load(gemspec_path)
    _(spec).wont_be_nil
    _(spec.description).must_be_kind_of(String)
    _(spec.description.strip).wont_be_empty
  end
end
