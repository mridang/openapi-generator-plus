# frozen_string_literal: true
# rubocop:disable all

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
        ATTRIBUTE_MAP = { blob: 'blob', identifier: 'identifier', note: 'note', ttl: 'ttl' }.freeze
        JSON_KEY_MAP = ATTRIBUTE_MAP.invert.freeze
        OPENAPI_TYPES = { blob: 'String', identifier: 'String', note: 'String', ttl: 'ISO8601::Duration' }.freeze
        # Mirrors the real model template: `duration` is NOT listed here.
        # ISO8601::Duration values flow through ObjectSerializer's type
        # dispatch (which routes them to duration_to_protobuf_json), not
        # through apply_format_on_serialize.
        OPENAPI_FORMATS = { blob: 'byte', identifier: 'uuid' }.freeze

        transform_keys do |key|
          JSON_KEY_MAP[key.to_s] || key.to_sym
        end

        attribute :blob, Types::Any.optional.meta(omittable: true)
        attribute :identifier, Types::Any.optional.meta(omittable: true)
        attribute :note, Types::Any.optional.meta(omittable: true)
        attribute :ttl, Types::Any.optional.meta(omittable: true)
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

    # P2: a leading UTF-8 byte-order mark (BOM) must not break
    # deserialization. JSON.parse tolerates the BOM, so a Category prefixed
    # with U+FEFF round-trips to the same model. GREEN everywhere.
    it 'deserializes BOM-prefixed JSON to typed model' do
      json_str = "﻿{\"id\":1,\"name\":\"Dogs\"}"
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

    # Order.status carries an OpenAPI schema `default: placed`. When the wire
    # JSON omits `status` entirely, deserialization must populate it with the
    # schema default ('placed') rather than leaving it nil. dry-struct's
    # `.default('placed')` on the attribute supplies this on the absent key.
    it 'deserialize applies schema default for absent field' do
      order = PetstoreClient::ObjectSerializer.deserialize('{"id":10,"petId":198772}', 'Order')
      _(order).must_be_kind_of(PetstoreClient::Models::Order)
      _(order.status).must_equal('placed')
    end

    # Defaults model: schema `default` applies ONLY when the property is
    # ABSENT from the payload; an explicit JSON `null` is a provided value
    # and is PRESERVED (the field stays nil), not replaced by the default.
    # This matches the JSON-Schema-correct behaviour of go/rust/python.
    # deserialize_model skips absent keys (so dry-struct's `.default(...)`
    # fires) but passes an explicit null through, which `.optional` keeps.
    it 'applies defaults only for absent fields, preserving explicit null' do
      from_empty = PetstoreClient::ObjectSerializer.deserialize('{}', 'Defaults')
      _(from_empty).must_be_kind_of(PetstoreClient::Models::Defaults)
      _(from_empty.retries).must_equal(3)
      _(from_empty.mode).must_equal('medium')
      _(from_empty.label).must_equal('untitled')

      from_null = PetstoreClient::ObjectSerializer.deserialize('{"label":null,"retries":7}', 'Defaults')
      _(from_null).must_be_kind_of(PetstoreClient::Models::Defaults)
      _(from_null.label).must_be_nil
      _(from_null.retries).must_equal(7)
    end

    # Self-referential model (TreeNode has a `child` of its own type). A
    # nested payload must decode every level into a typed TreeNode, with the
    # absent innermost `child` left nil rather than wrapped in an empty model.
    it 'deserializes a self-referential TreeNode preserving nesting' do
      json = '{"value":"root","child":{"value":"leaf"}}'
      top = PetstoreClient::ObjectSerializer.deserialize(json, 'TreeNode')
      _(top).must_be_kind_of(PetstoreClient::Models::TreeNode)
      _(top.value).must_equal('root')
      _(top.child).must_be_kind_of(PetstoreClient::Models::TreeNode)
      _(top.child.value).must_equal('leaf')
      _(top.child.child).must_be_nil
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
    it 'raises SerializationError when deserializing unknown enum value' do
      # An unknown standalone-enum value is a schema-validation failure, the
      # same class as a missing required field or a strict type mismatch.
      # deserialize wraps all of them uniformly as SerializationError so a
      # caller can catch every bad-payload case with one rescue, rather than
      # this path leaking a raw stdlib ArgumentError.
      assert_raises(PetstoreClient::SerializationError) do
        PetstoreClient::ObjectSerializer.deserialize('"shipped"', 'TestStatusEnumDeserialize')
      end
    end

    it 'returns value when deserializing valid enum value' do
      result = PetstoreClient::ObjectSerializer.deserialize('"approved"', 'TestStatusEnumDeserialize')
      _(result).must_equal 'approved'
    end

    # Canonical behavior 6 through a real generated model: Pet.status is an
    # inline enum (available/pending/sold) backed by dry-struct's
    # Types::String.enum. An unknown wire value must surface the SDK
    # (de)serialization error rather than silently coercing to a default or
    # an "unknown" sentinel.
    it 'raises SerializationError when an inline model enum has an unknown value' do
      json = '{"name":"doggie","photoUrls":["http://x/p.jpg"],"status":"banana"}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'deserializes an inline model enum with a known value' do
      json = '{"name":"doggie","photoUrls":["http://x/p.jpg"],"status":"available"}'
      pet = PetstoreClient::ObjectSerializer.deserialize(json, 'Pet')
      _(pet.status).must_equal 'available'
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

    it 'base64-decodes a top-level byte response (JSON string literal)' do
      # A top-level `type: string, format: byte` response carried as
      # application/json arrives as a JSON string literal. deserialize must
      # JSON-parse it and then base64-decode the inner string to raw bytes.
      raw = "test-image".dup.force_encoding(Encoding::BINARY)
      json = Base64.strict_encode64(raw).to_json
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'ByteArray')
      _(result).must_equal(raw)
      _(result.encoding).must_equal(Encoding::BINARY)
    end
  end

  # ── unevaluatedProperties:false (StrictTag, 2.20) ──

  describe 'unevaluatedProperties:false enforcement' do
    it 'rejects an undeclared key in the raw payload' do
      json = '{"id":1,"name":"Dogs","rogue":"x"}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'StrictTag')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'accepts a payload with only declared keys' do
      result = PetstoreClient::ObjectSerializer.deserialize('{"id":1,"name":"Dogs"}', 'StrictTag')
      _(result).wont_be_nil
      _(result.id).must_equal(1)
      _(result.name).must_equal('Dogs')
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
    # The native surface stays ISO8601::Duration, but the wire form is
    # protobuf-JSON duration ("3600s") -- the form Zitadel and other
    # protobuf-derived APIs require. The ISO-8601 form ("PT1H") is rejected.
    it 'stringify emits protobuf-JSON seconds for an ISO8601::Duration' do
      d = ISO8601::Duration.new('PT1H30M')
      out = PetstoreClient::ObjectSerializer.stringify(d)
      _(out).must_equal('5400s')
    end

    it 'serialize emits the protobuf-JSON duration string inside a hash' do
      d = ISO8601::Duration.new('PT5M')
      json = PetstoreClient::ObjectSerializer.serialize({ 'ttl' => d })
      _(JSON.parse(json)['ttl']).must_equal('300s')
    end

    it 'serialize emits a duration with days as total seconds' do
      d = ISO8601::Duration.new('P2DT3H')
      json = PetstoreClient::ObjectSerializer.serialize({ 'window' => d })
      _(JSON.parse(json)['window']).must_equal('183600s')
    end

    it 'serialize emits protobuf-JSON seconds for a duration field on a model body' do
      # Request-body path: a dry-struct model carrying an ISO8601::Duration
      # field must serialize that field as protobuf-JSON ("3600s"), not the
      # native ISO-8601 form ("PT1H") which Zitadel rejects. `duration` is
      # intentionally absent from OPENAPI_FORMATS, so this exercises the
      # type-dispatch route through duration_to_protobuf_json rather than
      # apply_format_on_serialize.
      model = PetstoreClient::Models::TestFormatModel.new(ttl: ISO8601::Duration.new('PT1H'))
      json = PetstoreClient::ObjectSerializer.serialize(model)
      _(JSON.parse(json)['ttl']).must_equal('3600s')
    end

    it 'stringify emits fractional seconds trimmed to nanos precision' do
      d = ISO8601::Duration.new('PT3600.000000001S')
      _(PetstoreClient::ObjectSerializer.stringify(d)).must_equal('3600.000000001s')
    end

    it 'stringify trims trailing zeros to three fractional digits' do
      d = ISO8601::Duration.new('PT1.5S')
      _(PetstoreClient::ObjectSerializer.stringify(d)).must_equal('1.500s')
    end

    it 'stringify preserves the sign for a negative duration' do
      d = ISO8601::Duration.new('-PT1H')
      _(PetstoreClient::ObjectSerializer.stringify(d)).must_equal('-3600s')
    end

    it 'convert_to_type parses a negative protobuf-JSON duration' do
      # The sign must be reconstructed before the 'P' ("-PT3600S"); the gem
      # rejects the "PT-3600S" form, so a negative wire value would otherwise
      # fail to deserialize — breaking the round-trip the serialize side emits.
      result = PetstoreClient::ObjectSerializer.convert_to_type('-3600s', 'ISO8601::Duration')
      _(result).must_be_kind_of(ISO8601::Duration)
      _(result.to_seconds).must_equal(-3600)
    end

    it 'convert_to_type parses a negative fractional protobuf-JSON duration' do
      result = PetstoreClient::ObjectSerializer.convert_to_type('-1.5s', 'ISO8601::Duration')
      _(result.to_seconds).must_be_close_to(-1.5, 1e-9)
    end

    it 'convert_to_type parses protobuf-JSON seconds into an ISO8601::Duration' do
      result = PetstoreClient::ObjectSerializer.convert_to_type('5400s', 'ISO8601::Duration')
      _(result).must_be_kind_of(ISO8601::Duration)
      _(result.to_seconds).must_equal(5400)
    end

    it 'convert_to_type parses fractional protobuf-JSON seconds' do
      result = PetstoreClient::ObjectSerializer.convert_to_type('3600.000000001s', 'ISO8601::Duration')
      _(result.to_seconds).must_be_close_to(3600.000000001, 1e-9)
    end

    it 'convert_to_type parses sub-0.0001s durations without scientific notation' do
      # Regression: building the ISO-8601 literal from a Float interpolated
      # the seconds field as scientific notation for magnitudes below 0.0001
      # (0.00001 -> "1.0e-05"), which the iso8601 gem's decimal-only grammar
      # rejects. Microsecond/nanosecond wire durations must round-trip.
      # Inputs are in canonical protobuf-JSON form (0/3/6/9 fractional digits)
      # so re-serialization round-trips identically; the regression being
      # guarded is that none of these sub-0.0001s magnitudes serialize via
      # Float scientific notation.
      ['0.000010s', '0.000001s', '0.000000001s'].each do |wire|
        result = PetstoreClient::ObjectSerializer.convert_to_type(wire, 'ISO8601::Duration')
        _(result).must_be_kind_of(ISO8601::Duration)
        _(PetstoreClient::ObjectSerializer.stringify(result)).must_equal(wire)
      end
    end

    it 'convert_to_type passes through an existing ISO8601::Duration' do
      original = ISO8601::Duration.new('PT1H')
      result = PetstoreClient::ObjectSerializer.convert_to_type(original, 'ISO8601::Duration')
      _(result).must_equal(original)
    end

    it 'round-trip duration yields equivalent value' do
      original = ISO8601::Duration.new('P1DT2H3M4S')
      serialized = PetstoreClient::ObjectSerializer.stringify(original)
      _(serialized).must_equal('93784s')
      parsed = PetstoreClient::ObjectSerializer.convert_to_type(serialized, 'ISO8601::Duration')
      _(parsed.to_seconds).must_equal(original.to_seconds)
    end

    it 'convert_to_type raises on malformed duration string' do
      assert_raises(PetstoreClient::SerializationError) do
        PetstoreClient::ObjectSerializer.convert_to_type('PT1H', 'ISO8601::Duration')
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

    it 'deserializing the oneOf parent routes to the subtype by discriminator' do
      # A discriminator-tagged payload deserialized against the parent
      # PetFood oneOf must come back as the concrete DryFood subtype, not
      # the raw data — PetFood.build resolves foodType="dry" to DryFood.
      json = '{"foodType":"dry","weightKg":2.5}'
      result = PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood')
      _(result).must_be_kind_of(PetstoreClient::Models::DryFood)
      _(result.weight_kg).must_equal(2.5)
    end
  end

  # ── Gap AJ — JSON null on a required, non-nullable field ──
  #
  # Canonical cross-SDK scenario: deserialize `{"name": null,
  # "photoUrls": ["u"]}` into Pet, where `name` is required AND
  # non-nullable. A literal JSON null for that field is a contract
  # violation and MUST surface the SDK's deserialization error rather
  # than producing a partial Pet with name == nil. Buggy before the fix
  # in go/python/kotlin; ruby is already strict via dry-struct's
  # non-omittable, non-nullable attribute. Canonical = throw.
  describe 'Gap AJ — null on required non-nullable field' do
    it 'raises when required name is JSON null' do
      json = '{"name":null,"photoUrls":["u"]}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(PetstoreClient::SerializationError)
    end
  end

  # ── Gap AU-residual — missing discriminator property ──
  #
  # Canonical cross-SDK scenario: deserialize a PetFood payload that omits
  # the `foodType` discriminator property entirely (`{"weightKg": 5.0}`).
  # With no discriminator value there is no subtype to route to, so the
  # SDK MUST throw rather than wrap the raw dict in a union container.
  # Buggy before the fix in python/php; ruby is already strict —
  # PetFood.build raises SerializationError on a nil discriminator value.
  # Canonical = throw.
  describe 'Gap AU-residual — missing discriminator property' do
    it 'raises when the foodType discriminator is absent' do
      json = '{"weightKg":5.0}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'PetFood')
      }).must_raise(PetstoreClient::SerializationError)
    end
  end

  # ── integer-backed enum round-trips as a JSON NUMBER (canonical 1) ──
  #
  # Priority is a standalone `type: integer` enum (1/2/3). It must
  # (de)serialize through the wire as a JSON number, never a quoted string:
  # serialize(2) emits the bare token 2, and deserialize("2", 'Priority')
  # yields the Integer member 2 — not "2". This guards the int-enum template
  # path that several SDKs latently hard-coded as String-backed.
  describe 'integer-backed enum round-trips as a JSON number' do
    it 'serializes an integer enum value as a JSON number, not a string' do
      json = PetstoreClient::ObjectSerializer.serialize(PetstoreClient::Models::Priority::NUMBER_2)
      # The bare JSON token must be the number 2, never the quoted "2".
      _(json).must_equal('2')
      _(JSON.parse(json)).must_equal(2)
      _(JSON.parse(json)).must_be_kind_of(Integer)
    end

    it 'deserializes a JSON number into the integer enum member' do
      result = PetstoreClient::ObjectSerializer.deserialize('2', 'Priority')
      _(result).must_equal(PetstoreClient::Models::Priority::NUMBER_2)
      _(result).must_be_kind_of(Integer)
    end

    it 'rejects an unknown integer enum value on deserialize' do
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize('99', 'Priority')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'round-trips an integer-enum-typed model field as a number' do
      # StockItem.priority references the Priority int enum. The field must
      # survive the model round-trip as a JSON number on the wire and an
      # Integer member in memory.
      item = PetstoreClient::ObjectSerializer.deserialize('{"priority":3}', 'StockItem')
      _(item).must_be_kind_of(PetstoreClient::Models::StockItem)
      _(item.priority).must_equal(PetstoreClient::Models::Priority::NUMBER_3)
      data = JSON.parse(PetstoreClient::ObjectSerializer.serialize(item))
      _(data['priority']).must_equal(3)
      _(data['priority']).must_be_kind_of(Integer)
    end
  end

  # ── non-lowercase string enum preserves wire casing (canonical 2) ──
  #
  # Availability is a standalone string enum whose values are NOT all
  # lowercase ("Available", "Sold", "on-hold"). The wire casing must be
  # preserved verbatim on both (de)serialize, a valid value round-trips to
  # the right member, and an unknown value is rejected.
  describe 'non-lowercase string enum preserves wire casing' do
    it 'deserializes each declared value to its member, casing preserved' do
      _(PetstoreClient::ObjectSerializer.deserialize('"Available"', 'Availability'))
        .must_equal(PetstoreClient::Models::Availability::AVAILABLE)
      _(PetstoreClient::ObjectSerializer.deserialize('"Sold"', 'Availability'))
        .must_equal(PetstoreClient::Models::Availability::SOLD)
      _(PetstoreClient::ObjectSerializer.deserialize('"on-hold"', 'Availability'))
        .must_equal(PetstoreClient::Models::Availability::ON_HOLD)
    end

    it 'serializes a member with its original wire casing' do
      json = PetstoreClient::ObjectSerializer.serialize(PetstoreClient::Models::Availability::AVAILABLE)
      _(JSON.parse(json)).must_equal('Available')
    end

    it 'rejects an unknown string enum value on deserialize' do
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize('"available"', 'Availability')
      }).must_raise(PetstoreClient::SerializationError)
    end
  end

  # ── referenced enum on a model field rejects unknown wire value (canonical 10) ──
  #
  # StockItem.availability is a property typed as the referenced Availability
  # enum (not an inline enum like Pet.status). An unknown wire value for that
  # field must surface the SDK serialization error rather than coercing to a
  # default or sentinel — the referenced-enum-as-model-field path.
  describe 'referenced enum model field rejects unknown value' do
    it 'raises when a referenced-enum field carries an unknown value' do
      json = '{"priority":1,"availability":"liquidated"}'
      _(proc {
        PetstoreClient::ObjectSerializer.deserialize(json, 'StockItem')
      }).must_raise(PetstoreClient::SerializationError)
    end

    it 'accepts a known referenced-enum field value' do
      item = PetstoreClient::ObjectSerializer.deserialize('{"priority":1,"availability":"Sold"}', 'StockItem')
      _(item.availability).must_equal(PetstoreClient::Models::Availability::SOLD)
    end
  end

  # ── double field deserializes from an INTEGRAL JSON value (canonical 4) ──
  #
  # PhotoMetadataLocation.lat/lng are `type: number, format: double` (Ruby
  # surface Float). A wire value carried as an integral JSON token ({"lat":5},
  # not 5.0) must coerce to a Float without crashing — JSON omits the
  # trailing .0 for whole numbers, so the deserializer must accept any
  # Numeric, not strictly a Ruby Float.
  describe 'double field accepts an integral JSON value' do
    it 'coerces an integral JSON number into a Float' do
      loc = PetstoreClient::ObjectSerializer.deserialize('{"lat":5,"lng":-3}', 'PhotoMetadataLocation')
      _(loc).must_be_kind_of(PetstoreClient::Models::PhotoMetadataLocation)
      _(loc.lat).must_equal(5.0)
      _(loc.lat).must_be_kind_of(Float)
      _(loc.lng).must_equal(-3.0)
      _(loc.lng).must_be_kind_of(Float)
    end
  end

  # ── nested container deep-round-trips to typed leaves (canonical 7) ──
  #
  # StockItem.matrix is `Array<Array<Integer>>`. The deserializer must
  # recurse per-element so every leaf is a typed Integer, and re-serialize
  # the nested structure faithfully.
  describe 'nested container deep round-trip' do
    it 'deserializes array-of-array<int> to typed Integer leaves' do
      json = '{"priority":1,"matrix":[[1,2],[3,4,5]]}'
      item = PetstoreClient::ObjectSerializer.deserialize(json, 'StockItem')
      _(item.matrix).must_equal([[1, 2], [3, 4, 5]])
      _(item.matrix.first.first).must_be_kind_of(Integer)
      _(item.matrix.last.last).must_be_kind_of(Integer)
    end

    it 'round-trips the nested container back to the same shape' do
      json = '{"priority":1,"matrix":[[7,8],[9]]}'
      item = PetstoreClient::ObjectSerializer.deserialize(json, 'StockItem')
      data = JSON.parse(PetstoreClient::ObjectSerializer.serialize(item))
      _(data['matrix']).must_equal([[7, 8], [9]])
    end
  end

  # ── format: byte ARRAY items round-trip through base64 (canonical 3, array) ──
  #
  # PetPassport.scans is `Array<String>` with format `byte[]`: every item is
  # base64-decoded to raw bytes on deserialize and re-encoded on serialize.
  # The scalar byte case (TestFormatModel.blob / top-level ByteArray) is
  # covered above; this guards the per-item array transform.
  describe 'format: byte array items round-trip through base64' do
    it 'base64-decodes every scan item into raw bytes on deserialize' do
      raw_a = "scan-a\x00".dup.force_encoding(Encoding::BINARY)
      raw_b = "scan-b\xFF".dup.force_encoding(Encoding::BINARY)
      json = %({"scans":["#{Base64.strict_encode64(raw_a)}","#{Base64.strict_encode64(raw_b)}"]})
      passport = PetstoreClient::ObjectSerializer.deserialize(json, 'PetPassport')
      _(passport).must_be_kind_of(PetstoreClient::Models::PetPassport)
      _(passport.scans).must_equal([raw_a, raw_b])
      _(passport.scans.first.encoding).must_equal(Encoding::BINARY)
    end

    it 're-encodes every scan item to base64 on serialize' do
      raw_a = "\x01\x02".dup.force_encoding(Encoding::BINARY)
      raw_b = "\xFE\xFF".dup.force_encoding(Encoding::BINARY)
      model = PetstoreClient::Models::PetPassport.new(scans: [raw_a, raw_b])
      data = JSON.parse(PetstoreClient::ObjectSerializer.serialize(model))
      _(data['scans']).must_equal([Base64.strict_encode64(raw_a), Base64.strict_encode64(raw_b)])
    end

    it 'round-trips the scans array preserving bytes exactly' do
      raw = (0..255).map(&:chr).join.dup.force_encoding(Encoding::BINARY)
      model = PetstoreClient::Models::PetPassport.new(scans: [raw])
      json = PetstoreClient::ObjectSerializer.serialize(model)
      restored = PetstoreClient::ObjectSerializer.deserialize(json, 'PetPassport')
      _(restored.scans).must_equal([raw])
    end
  end
end
