# frozen_string_literal: true

# rubocop:disable Metrics/BlockLength, Lint/MissingCopEnableDirective

require 'test_helper'
require 'base64'
require 'iso8601'
require 'tod'

# Stub enum modules for ObjectSerializer enum-handling tests.
# Defined at file scope to avoid Lint/ConstantDefinitionInBlock and to ensure
# constants like VALUES are attached to the named module rather than leaking
# into the enclosing scope (which happens with `Class.new do ... end`).
module PetstoreClient
  module Models
    unless const_defined?(:TestStatusEnumDeserialize)
      module TestStatusEnumDeserialize
        VALUES = %w[placed approved delivered].freeze
      end
    end

    unless const_defined?(:TestStatusEnumValidate)
      module TestStatusEnumValidate
        PLACED = 'placed'
        APPROVED = 'approved'
        DELIVERED = 'delivered'
        VALUES = [PLACED, APPROVED, DELIVERED].freeze

        def self.validate!(value)
          return value if value.nil? || VALUES.include?(value)

          raise ArgumentError, "Unknown enum value: #{value.inspect}"
        end
      end
    end

    # Stub model for format: byte / format: uuid surface tests. Mirrors the
    # ATTRIBUTE_MAP / OPENAPI_TYPES / OPENAPI_FORMATS triplet emitted by the
    # real model template so that ObjectSerializer treats it identically.
    unless const_defined?(:TestFormatModel)
      class TestFormatModel < Dry::Struct
        ATTRIBUTE_MAP = { blob: 'blob', identifier: 'identifier', note: 'note' }.freeze
        JSON_KEY_MAP = ATTRIBUTE_MAP.invert.freeze
        OPENAPI_TYPES = { blob: 'String', identifier: 'String', note: 'String' }.freeze
        OPENAPI_FORMATS = { blob: 'byte', identifier: 'uuid' }.freeze

        transform_keys do |key|
          JSON_KEY_MAP[key.to_s] || key.to_sym
        end

        attribute :blob, Types::Any.optional.meta(omittable: true)
        attribute :identifier, Types::Any.optional.meta(omittable: true)
        attribute :note, Types::Any.optional.meta(omittable: true)
      end
    end
  end
end

describe PetstoreClient::ObjectSerializer do
  parallelize_me!

  describe 'DateTimeOffsetPreservation' do
    it 'UTC datetime serializes containing date-time and offset' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '+00:00')
      result = PetstoreClient::ObjectSerializer.stringify(t)
      _(result).must_include('2024-01-01')
      _(result).must_include('12:30:45')
      assert(result.include?('+00:00') || result.include?('Z') || result.end_with?('Z'),
        "should contain UTC offset: #{result}")
    end

    it 'positive offset is preserved in serialized string' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '+05:30')
      result = PetstoreClient::ObjectSerializer.stringify(t)
      _(result).must_include('+05:30')
    end

    it 'negative offset is preserved in serialized string' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '-08:00')
      result = PetstoreClient::ObjectSerializer.stringify(t)
      _(result).must_include('-08:00')
    end

    it 'subseconds are dropped from serialized datetime' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '+00:00')
      result = PetstoreClient::ObjectSerializer.stringify(t)
      _(result).wont_include('.123')
    end

    it 'date-only serializes as ISO 8601 date without time component' do
      d = Date.new(2024, 1, 1)
      result = PetstoreClient::ObjectSerializer.stringify(d)
      _(result).must_equal('2024-01-01')
    end

    it 'serialized datetime string contains an offset' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '+00:00')
      result = PetstoreClient::ObjectSerializer.stringify(t)
      assert(result.match?(/[+-]\d{2}:\d{2}$|Z$/), "should end with offset: #{result}")
    end

    it 'round-trip datetime yields equivalent instant' do
      original = Time.new(2024, 1, 1, 12, 30, 45, '+05:30')
      serialized = PetstoreClient::ObjectSerializer.stringify(original)
      parsed = Time.parse(serialized)
      _(original.to_i).must_equal(parsed.to_i)
    end
  end

  describe 'NonAsciiSerialization' do
    it 'accented character serializes without unicode escape' do
      result = PetstoreClient::ObjectSerializer.serialize('café')
      _(result).must_include('é')
    end

    it 'CJK characters serialize without unicode escape' do
      result = PetstoreClient::ObjectSerializer.serialize('日本')
      _(result).must_include('日本')
    end

    it 'tab character is properly escaped in JSON' do
      result = PetstoreClient::ObjectSerializer.serialize("a\tb")
      _(result).must_include('\t')
    end
  end

  describe 'DeserializationErrorWrapping' do
    it 'truncated JSON raises SerializationError not a raw parse error' do
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize('{', 'Category')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'incomplete JSON object raises SerializationError' do
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize('{"name":', 'Category')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'thrown SerializationError has a cause referencing original error' do
      PetstoreClient::ObjectSerializer.deserialize('{', 'Category')
      flunk 'Expected SerializationError to be raised'
    rescue PetstoreClient::SerializationError => e
      _(e.cause).wont_be_nil
    end
  end

  describe '.stringify' do
    it 'returns empty string for nil' do
      _(PetstoreClient::ObjectSerializer.stringify(nil)).must_equal('')
    end

    it 'returns "true" for boolean true' do
      _(PetstoreClient::ObjectSerializer.stringify(true)).must_equal('true')
    end

    it 'returns "false" for boolean false' do
      _(PetstoreClient::ObjectSerializer.stringify(false)).must_equal('false')
    end

    it 'returns string representation of integer' do
      _(PetstoreClient::ObjectSerializer.stringify(42)).must_equal('42')
    end

    it 'returns ISO 8601 string for Time' do
      t = Time.new(2024, 1, 15, 10, 30, 0, '+00:00')
      result = PetstoreClient::ObjectSerializer.stringify(t)
      _(result).must_include('2024-01-15')
      _(result).must_include('10:30:00')
    end

    it 'passes through plain string unchanged' do
      _(PetstoreClient::ObjectSerializer.stringify('hello')).must_equal('hello')
    end

    it 'returns string representation of float' do
      _(PetstoreClient::ObjectSerializer.stringify(3.14)).must_equal('3.14')
    end

    it 'returns ISO 8601 date string for Date' do
      _(PetstoreClient::ObjectSerializer.stringify(Date.new(2024, 1, 15))).must_equal('2024-01-15')
    end
  end

  describe '.to_path_value' do
    it 'returns empty string for nil' do
      _(PetstoreClient::ObjectSerializer.to_path_value(nil)).must_equal('')
    end

    it 'returns the string for a string value' do
      _(PetstoreClient::ObjectSerializer.to_path_value('hello')).must_equal('hello')
    end

    it 'converts integer to string' do
      _(PetstoreClient::ObjectSerializer.to_path_value(42)).must_equal('42')
    end

    it 'converts true to "true"' do
      _(PetstoreClient::ObjectSerializer.to_path_value(true)).must_equal('true')
    end

    it 'converts false to "false"' do
      _(PetstoreClient::ObjectSerializer.to_path_value(false)).must_equal('false')
    end
  end

  describe '.to_query_value' do
    it 'returns nil for nil' do
      _(PetstoreClient::ObjectSerializer.to_query_value(nil)).must_be_nil
    end

    it 'returns the string for a string value' do
      _(PetstoreClient::ObjectSerializer.to_query_value('hello')).must_equal('hello')
    end

    it 'converts integer to string' do
      _(PetstoreClient::ObjectSerializer.to_query_value(42)).must_equal('42')
    end

    it 'converts true to "true"' do
      _(PetstoreClient::ObjectSerializer.to_query_value(true)).must_equal('true')
    end

    it 'converts false to "false"' do
      _(PetstoreClient::ObjectSerializer.to_query_value(false)).must_equal('false')
    end

    it 'joins array with comma by default' do
      _(PetstoreClient::ObjectSerializer.to_query_value(%w[a b c])).must_equal('a,b,c')
    end

    it 'joins array with comma for csv' do
      _(PetstoreClient::ObjectSerializer.to_query_value(%w[a b c], :csv)).must_equal('a,b,c')
    end

    it 'joins array with space for ssv' do
      _(PetstoreClient::ObjectSerializer.to_query_value(%w[a b c], :ssv)).must_equal('a b c')
    end

    it 'joins array with tab for tsv' do
      _(PetstoreClient::ObjectSerializer.to_query_value(%w[a b c], :tsv)).must_equal("a\tb\tc")
    end

    it 'joins array with pipe for pipes' do
      _(PetstoreClient::ObjectSerializer.to_query_value(%w[a b c], :pipes)).must_equal('a|b|c')
    end

    it 'returns array as-is for multi' do
      _(PetstoreClient::ObjectSerializer.to_query_value(%w[a b c], :multi)).must_equal(%w[a b c])
    end
  end

  describe '.to_header_value' do
    it 'returns empty string for nil' do
      _(PetstoreClient::ObjectSerializer.to_header_value(nil)).must_equal('')
    end

    it 'returns the string for a string value' do
      _(PetstoreClient::ObjectSerializer.to_header_value('hello')).must_equal('hello')
    end

    it 'converts integer to string' do
      _(PetstoreClient::ObjectSerializer.to_header_value(42)).must_equal('42')
    end

    it 'joins array with comma' do
      _(PetstoreClient::ObjectSerializer.to_header_value(%w[a b c])).must_equal('a,b,c')
    end
  end

  describe '.to_form_value' do
    it 'returns empty string for nil' do
      _(PetstoreClient::ObjectSerializer.to_form_value(nil)).must_equal('')
    end

    it 'returns the string for a string value' do
      _(PetstoreClient::ObjectSerializer.to_form_value('hello')).must_equal('hello')
    end

    it 'converts integer to string' do
      _(PetstoreClient::ObjectSerializer.to_form_value(42)).must_equal('42')
    end

    it 'converts true to "true"' do
      _(PetstoreClient::ObjectSerializer.to_form_value(true)).must_equal('true')
    end

    it 'converts false to "false"' do
      _(PetstoreClient::ObjectSerializer.to_form_value(false)).must_equal('false')
    end
  end

  describe '.to_cookie_value' do
    it 'returns empty string for nil' do
      _(PetstoreClient::ObjectSerializer.to_cookie_value(nil)).must_equal('')
    end

    it 'returns the string for a string value' do
      _(PetstoreClient::ObjectSerializer.to_cookie_value('hello')).must_equal('hello')
    end

    it 'converts integer to string' do
      _(PetstoreClient::ObjectSerializer.to_cookie_value(42)).must_equal('42')
    end
  end

  describe '.serialize' do
    it 'serializes a model to valid JSON' do
      category = PetstoreClient::Models::Category.new(id: 1, name: 'Dogs')
      json = PetstoreClient::ObjectSerializer.serialize(category)
      data = JSON.parse(json)
      _(data['id']).must_equal(1)
      _(data['name']).must_equal('Dogs')
    end

    it 'handles nil' do
      json = PetstoreClient::ObjectSerializer.serialize(nil)
      _(json).must_equal('null')
    end

    it 'includes fields explicitly set to default values' do
      category = PetstoreClient::Models::Category.new(id: 0, name: '')
      json = PetstoreClient::ObjectSerializer.serialize(category)
      data = JSON.parse(json)
      _(data).must_include('id')
      _(data['id']).must_equal(0)
      _(data).must_include('name')
      _(data['name']).must_equal('')
    end
  end

  describe '.deserialize' do
    it 'deserializes JSON to typed model' do
      json_str = '{"id":1,"name":"Dogs"}'
      category = PetstoreClient::ObjectSerializer.deserialize(json_str, 'Category')
      _(category).must_be_kind_of(PetstoreClient::Models::Category)
      _(category.id).must_equal(1)
      _(category.name).must_equal('Dogs')
    end

    it 'returns nil for empty input' do
      _(PetstoreClient::ObjectSerializer.deserialize('', 'Category')).must_be_nil
    end

    it 'returns nil for nil input' do
      _(PetstoreClient::ObjectSerializer.deserialize(nil, 'Category')).must_be_nil
    end
  end

  # ── required field hard-fail on deserialize (#10) ──
  #
  # A required, non-nullable model field that is ABSENT or explicitly NULL
  # in the input JSON must raise the SDK serialization error rather than
  # producing a partial object. Canonical across the product — matches
  # Kotlin / PHP / Swift / Rust / Go / Python / Dart. Pet declares
  # `name` and `photoUrls` as required.

  describe 'required field hard-fail on deserialize' do
    it 'raises when a required field is absent from the JSON' do
      # photoUrls present, name missing.
      json = '{"id":1,"photoUrls":["http://example.com/p.jpg"]}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'raises when a second required field is absent from the JSON' do
      # name present, photoUrls missing.
      json = '{"id":1,"name":"doggie"}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'raises when a required field is explicitly null in the JSON' do
      json = '{"id":1,"name":null,"photoUrls":["http://example.com/p.jpg"]}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'deserializes successfully when all required fields are present' do
      json = '{"id":1,"name":"doggie","photoUrls":["http://example.com/p.jpg"]}'
      pet = PetstoreClient::ObjectSerializer.deserialize(json, 'Pet')
      _(pet).must_be_kind_of(PetstoreClient::Models::Pet)
      _(pet.name).must_equal('doggie')
      _(pet.photo_urls.to_a).must_equal(['http://example.com/p.jpg'])
    end

    it 'leaves optional fields untouched when omitted' do
      # Only the required fields are supplied; optional `id`/`status` stay nil.
      json = '{"name":"doggie","photoUrls":["http://example.com/p.jpg"]}'
      pet = PetstoreClient::ObjectSerializer.deserialize(json, 'Pet')
      _(pet.name).must_equal('doggie')
      _(pet.id).must_be_nil
      _(pet.status).must_be_nil
    end
  end

  # ── nil discard on serialize (#13) ──

  describe 'nil discard on serialize' do
    it 'omits hash entries with nil values from serialized JSON' do
      input = { 'present' => 'value', 'absent' => nil }
      json = PetstoreClient::ObjectSerializer.serialize(input)
      data = JSON.parse(json)
      _(data).must_include 'present'
      _(data['present']).must_equal 'value'
      _(data).wont_include 'absent'
    end

    it 'omits nested nil hash values from serialized JSON' do
      input = { 'outer' => { 'a' => 1, 'b' => nil, 'c' => 3 } }
      json = PetstoreClient::ObjectSerializer.serialize(input)
      data = JSON.parse(json)
      _(data['outer']).must_include 'a'
      _(data['outer']).wont_include 'b'
      _(data['outer']).must_include 'c'
    end

    it 'keeps nil array elements (only hashes filter)' do
      json = PetstoreClient::ObjectSerializer.serialize([1, nil, 3])
      _(JSON.parse(json)).must_equal [1, nil, 3]
    end
  end

  # ── enum unknown value throws on deserialize (#12) ──

  describe 'enum unknown value detection' do
    it 'raises ArgumentError when deserializing unknown enum value' do
      assert_raises(ArgumentError) do
        PetstoreClient::ObjectSerializer.deserialize('"shipped"', 'TestStatusEnumDeserialize')
      end
    end

    it 'returns value when deserializing valid enum value' do
      result = PetstoreClient::ObjectSerializer.deserialize('"approved"', 'TestStatusEnumDeserialize')
      _(result).must_equal 'approved'
    end
  end

  # ── enum frozen const + VALUES validation (#11) ──

  describe 'enum frozen consts' do
    it 'enum constants are frozen strings' do
      _(PetstoreClient::Models::TestStatusEnumValidate::PLACED).must_be :frozen?
      _(PetstoreClient::Models::TestStatusEnumValidate::APPROVED).must_be :frozen?
    end

    it 'VALUES contains all enum constants and is frozen' do
      values = PetstoreClient::Models::TestStatusEnumValidate::VALUES
      _(values).must_be :frozen?
      _(values).must_include 'placed'
      _(values).must_include 'approved'
      _(values).must_include 'delivered'
    end

    it 'validate! returns value when in VALUES' do
      _(PetstoreClient::Models::TestStatusEnumValidate.validate!('approved')).must_equal 'approved'
    end

    it 'validate! raises ArgumentError for value not in VALUES' do
      assert_raises(ArgumentError) do
        PetstoreClient::Models::TestStatusEnumValidate.validate!('shipped')
      end
    end
  end

  # ── discard extras on deserialize (#14) ──

  describe 'extras discarded on deserialize' do
    it 'silently ignores unknown JSON keys not in OPENAPI_TYPES' do
      json_str = '{"id":1,"name":"Dogs","unexpected":"extra","another":42}'
      category = PetstoreClient::ObjectSerializer.deserialize(json_str, 'Category')
      _(category).must_be_kind_of PetstoreClient::Models::Category
      _(category.id).must_equal 1
      _(category.name).must_equal 'Dogs'
      _(category.respond_to?(:unexpected)).must_equal false
    end
  end

  # ── Gap V — NaN/Infinity rejection (RFC 8259 §6) ──

  describe 'NaN/Infinity rejection' do
    it 'serialize raises on NaN' do
      # ObjectSerializer wraps JSON::GeneratorError as SerializationError.
      assert_raises(PetstoreClient::SerializationError) do
        PetstoreClient::ObjectSerializer.serialize({ 'val' => Float::NAN })
      end
    end

    it 'serialize raises on +Infinity' do
      assert_raises(PetstoreClient::SerializationError) do
        PetstoreClient::ObjectSerializer.serialize({ 'val' => Float::INFINITY })
      end
    end

    it 'serialize raises on -Infinity' do
      assert_raises(PetstoreClient::SerializationError) do
        PetstoreClient::ObjectSerializer.serialize({ 'val' => -Float::INFINITY })
      end
    end

    it 'deserialize raises on NaN literal' do
      assert_raises(PetstoreClient::SerializationError) do
        PetstoreClient::ObjectSerializer.deserialize('{"val": NaN}', 'Object')
      end
    end

    it 'deserialize raises on Infinity literal' do
      assert_raises(PetstoreClient::SerializationError) do
        PetstoreClient::ObjectSerializer.deserialize('{"val": Infinity}', 'Object')
      end
    end
  end

  # ── format: byte (Buffer/bytes surface, 2.1) ──

  describe 'format: byte base64 transcoding' do
    it 'decodes base64 wire value into binary-encoded String on deserialize' do
      raw = "hello\x00\xFFworld".dup.force_encoding(Encoding::BINARY)
      encoded = Base64.strict_encode64(raw)
      json = %({"blob":"#{encoded}","note":"x"})
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'TestFormatModel')
      _(result.blob).must_equal(raw)
      _(result.blob.encoding).must_equal(Encoding::BINARY)
      _(result.note).must_equal('x')
    end

    it 'encodes binary String back to base64 on serialize' do
      raw = "\x00\x01\x02\xFF".dup.force_encoding(Encoding::BINARY)
      model = PetstoreClient::Models::TestFormatModel.new(blob: raw, note: 'y')
      json = PetstoreClient::ObjectSerializer.serialize(model)
      data = JSON.parse(json)
      _(data['blob']).must_equal(Base64.strict_encode64(raw))
      _(data['note']).must_equal('y')
    end

    it 'round-trip preserves bytes exactly' do
      raw = (0..255).map(&:chr).join.dup.force_encoding(Encoding::BINARY)
      model = PetstoreClient::Models::TestFormatModel.new(blob: raw)
      json = PetstoreClient::ObjectSerializer.serialize(model)
      restored = PetstoreClient::ObjectSerializer.deserialize(json, 'TestFormatModel')
      _(restored.blob).must_equal(raw)
    end

    it 'raises SerializationError on invalid base64 input' do
      json = '{"blob":"not valid base64!!!"}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'TestFormatModel')
      }).must_raise(PetstoreClient::SerializationError)
    end
  end

  # ── format: uuid (typed UUID surface, 2.2) ──

  describe 'format: uuid validation' do
    it 'accepts canonical RFC 4122 UUID on deserialize' do
      json = '{"identifier":"550e8400-e29b-41d4-a716-446655440000"}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'TestFormatModel')
      _(result.identifier).must_equal('550e8400-e29b-41d4-a716-446655440000')
    end

    it 'raises SerializationError on malformed UUID' do
      json = '{"identifier":"not-a-uuid"}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'TestFormatModel')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'raises SerializationError on serialize with malformed UUID' do
      model = PetstoreClient::Models::TestFormatModel.new(identifier: 'bogus')
      _(proc {
        PetstoreClient::ObjectSerializer.serialize(model)
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'serializes a valid UUID unchanged' do
      uuid = '00112233-4455-6677-8899-aabbccddeeff'
      model = PetstoreClient::Models::TestFormatModel.new(identifier: uuid)
      json = PetstoreClient::ObjectSerializer.serialize(model)
      _(JSON.parse(json)['identifier']).must_equal(uuid)
    end
  end

  # ── format: time (Tod::TimeOfDay surface, 4.8) ──

  describe 'format: time round-tripping' do
    it 'stringify emits HH:MM:SS for a Tod::TimeOfDay' do
      t = Tod::TimeOfDay.new(13, 45, 30)
      _(PetstoreClient::ObjectSerializer.stringify(t)).must_equal('13:45:30')
    end

    it 'serialize emits HH:MM:SS for a Tod::TimeOfDay inside a hash' do
      t = Tod::TimeOfDay.new(9, 0, 0)
      json = PetstoreClient::ObjectSerializer.serialize({ 'opens_at' => t })
      _(JSON.parse(json)['opens_at']).must_equal('09:00:00')
    end

    it 'convert_to_type parses HH:MM:SS into a Tod::TimeOfDay' do
      result = PetstoreClient::ObjectSerializer.convert_to_type('13:45:30', 'Tod::TimeOfDay')
      _(result).must_be_kind_of(Tod::TimeOfDay)
      _(result.hour).must_equal(13)
      _(result.minute).must_equal(45)
      _(result.second).must_equal(30)
    end

    it 'convert_to_type passes through an existing Tod::TimeOfDay' do
      original = Tod::TimeOfDay.new(7, 30, 15)
      result = PetstoreClient::ObjectSerializer.convert_to_type(original, 'Tod::TimeOfDay')
      _(result).must_equal(original)
    end

    it 'round-trip time yields equivalent value' do
      original = Tod::TimeOfDay.new(7, 30, 15)
      serialized = PetstoreClient::ObjectSerializer.stringify(original)
      parsed = PetstoreClient::ObjectSerializer.convert_to_type(serialized, 'Tod::TimeOfDay')
      _(parsed).must_equal(original)
    end

    it 'convert_to_type raises on malformed time string' do
      assert_raises(ArgumentError) do
        PetstoreClient::ObjectSerializer.convert_to_type('not-a-time', 'Tod::TimeOfDay')
      end
    end
  end

  # ── format: duration (ISO8601::Duration surface, 4.8) ──

  describe 'format: duration round-tripping' do
    it 'stringify emits canonical ISO-8601 for an ISO8601::Duration' do
      d = ISO8601::Duration.new('PT1H30M')
      out = PetstoreClient::ObjectSerializer.stringify(d)
      _(out).must_equal('PT1H30M')
    end

    it 'serialize emits the canonical duration string inside a hash' do
      d = ISO8601::Duration.new('PT5M')
      json = PetstoreClient::ObjectSerializer.serialize({ 'ttl' => d })
      _(JSON.parse(json)['ttl']).must_equal('PT5M')
    end

    it 'serialize emits a duration with days' do
      d = ISO8601::Duration.new('P2DT3H')
      json = PetstoreClient::ObjectSerializer.serialize({ 'window' => d })
      _(JSON.parse(json)['window']).must_equal('P2DT3H')
    end

    it 'convert_to_type parses ISO-8601 into an ISO8601::Duration' do
      result = PetstoreClient::ObjectSerializer.convert_to_type('PT1H30M', 'ISO8601::Duration')
      _(result).must_be_kind_of(ISO8601::Duration)
      _(result.to_seconds).must_equal(5400)
    end

    it 'convert_to_type passes through an existing ISO8601::Duration' do
      original = ISO8601::Duration.new('PT1H')
      result = PetstoreClient::ObjectSerializer.convert_to_type(original, 'ISO8601::Duration')
      _(result).must_equal(original)
    end

    it 'round-trip duration yields equivalent value' do
      original = ISO8601::Duration.new('P1DT2H3M4S')
      serialized = PetstoreClient::ObjectSerializer.stringify(original)
      parsed = PetstoreClient::ObjectSerializer.convert_to_type(serialized, 'ISO8601::Duration')
      _(parsed.to_seconds).must_equal(original.to_seconds)
    end

    it 'convert_to_type raises on malformed duration string' do
      assert_raises(StandardError) do
        PetstoreClient::ObjectSerializer.convert_to_type('not-a-duration', 'ISO8601::Duration')
      end
    end
  end

  # ── Gap K — discriminator auto-injection on subtype serialize ──

  describe 'discriminator auto-injection' do
    it 'dry subtype serialize auto-emits the discriminator' do
      # DryFood defaults food_type to 'dry', so a caller that omits it
      # still gets the discriminator on the wire.
      dry = PetstoreClient::Models::DryFood.new(weight_kg: 2.5)
      data = JSON.parse(PetstoreClient::ObjectSerializer.serialize(dry))
      _(data['foodType']).must_equal('dry')
      _(data['weightKg']).must_equal(2.5)
    end

    it 'wet subtype serialize auto-emits the discriminator' do
      wet = PetstoreClient::Models::WetFood.new(volume_ml: 350)
      data = JSON.parse(PetstoreClient::ObjectSerializer.serialize(wet))
      _(data['foodType']).must_equal('wet')
    end
  end

  describe 'oneOf/anyOf no-match' do
    it 'resolve_one_of returns the first matching variant' do
      candidates = [
        ->(_data) { raise StandardError, 'variant A does not match' },
        ->(data) { "matched:#{data}" }
      ]
      result = PetstoreClient::ObjectSerializer.resolve_one_of('payload', candidates)
      _(result).must_equal('matched:payload')
    end

    it 'resolve_one_of raises when no variant matches' do
      # A payload matching none of the declared variants is a contract
      # violation and must fail loudly rather than be silently returned as nil.
      candidates = [
        ->(_data) { raise StandardError, 'variant A does not match' },
        ->(_data) { raise StandardError, 'variant B does not match' }
      ]
      assert_raises(PetstoreClient::SchemaMismatchError) do
        PetstoreClient::ObjectSerializer.resolve_one_of({ 'unexpected' => true }, candidates)
      end
    end

    it 'resolve_any_of raises when no variant matches' do
      candidates = [->(_data) { raise StandardError, 'no match' }]
      assert_raises(PetstoreClient::SchemaMismatchError) do
        PetstoreClient::ObjectSerializer.resolve_any_of({}, candidates)
      end
    end
  end
end
