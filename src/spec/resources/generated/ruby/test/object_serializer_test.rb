# frozen_string_literal: true

require 'test_helper'
require 'base64'
require 'iso8601'
require 'tod'

# Stub enum modules for ObjectSerializer enum-handling tests.
# Defined at file scope to avoid Lint/ConstantDefinitionInBlock and to ensure
# constants like VALUES are attached to the named module rather than leaking
# into the enclosing scope (which happens with `Class.new do ... end`).
module Petstore::Client
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

        attribute :blob, ::Petstore::Client::Types::Any.optional.meta(omittable: true)
        attribute :identifier, ::Petstore::Client::Types::Any.optional.meta(omittable: true)
        attribute :note, ::Petstore::Client::Types::Any.optional.meta(omittable: true)
        attribute :ttl, ::Petstore::Client::Types::Any.optional.meta(omittable: true)
      end
    end
  end
end

describe Petstore::Client::ObjectSerializer do
  parallelize_me!

  describe 'DateTimeOffsetPreservation' do
    it 'bhv_serializationobject_174 UTC datetime serializes containing date-time and offset' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '+00:00')
      result = Petstore::Client::ObjectSerializer.stringify(t)
      _(result).must_include('2024-01-01')
      _(result).must_include('12:30:45')
      assert(result.include?('+00:00') || result.include?('Z') || result.end_with?('Z'),
             "should contain UTC offset: #{result}")
    end

    it 'bhv_serializationobject_116 positive offset is preserved in serialized string' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '+05:30')
      result = Petstore::Client::ObjectSerializer.stringify(t)
      _(result).must_include('+05:30')
    end

    it 'bhv_serializationobject_107 negative offset is preserved in serialized string' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '-08:00')
      result = Petstore::Client::ObjectSerializer.stringify(t)
      _(result).must_include('-08:00')
    end

    it 'bhv_serializationobject_199 subseconds are preserved in serialized datetime' do
      t = Time.parse('2024-01-01T12:30:45.123+00:00')
      result = Petstore::Client::ObjectSerializer.stringify(t)
      _(result).must_include('.123')
    end

    it 'bhv_serializationobject_044 date-only serializes as ISO 8601 date without time component' do
      d = Date.new(2024, 1, 1)
      result = Petstore::Client::ObjectSerializer.stringify(d)
      _(result).must_equal('2024-01-01')
    end

    it 'bhv_serializationobject_149 serialized datetime string contains an offset' do
      t = Time.new(2024, 1, 1, 12, 30, 45, '+00:00')
      result = Petstore::Client::ObjectSerializer.stringify(t)
      assert_match(/[+-]\d{2}:\d{2}$|Z$/, result, "should end with offset: #{result}")
    end

    it 'bhv_serializationobject_150 ends a serialized UTC datetime with a numeric offset, never Z' do
      result = Petstore::Client::ObjectSerializer.stringify(Time.utc(2024, 1, 1, 12, 30, 45))
      _(result).must_match(/[+-]\d{2}:\d{2}\z/)
      _(result.end_with?('Z')).must_equal(false)
    end

    it 'bhv_serializationobject_144 round-trip datetime yields equivalent instant' do
      original = Time.new(2024, 1, 1, 12, 30, 45, '+05:30')
      serialized = Petstore::Client::ObjectSerializer.stringify(original)
      parsed = Time.parse(serialized)
      _(original.to_i).must_equal(parsed.to_i)
    end

    # ── date-time serialization preserves sub-second precision ──
    #
    # PhotoMetadata.takenAt is `format: date-time` (Ruby surface Time). A
    # millisecond-bearing instant must survive the body round-trip: the
    # encoder must EMIT the fractional ".123" (the decoder, Time.parse,
    # already accepts it), so the round-trip is lossless to the millisecond.
    # Milliseconds (3 digits) are the cross-SDK common denominator. This
    # guards against a whole-second-truncating encoder format.
    it 'bhv_serializationobject_046 date-time serialization preserves sub-second precision' do
      original = Time.parse('2020-01-02T03:04:05.123Z')
      model = Petstore::Client::Models::PhotoMetadata.new(taken_at: original)

      serialized = Petstore::Client::ObjectSerializer.serialize(model)
      _(serialized).must_include('.123')

      round_tripped = Petstore::Client::ObjectSerializer.deserialize(serialized, 'PhotoMetadata')
      # Compare to millisecond precision: the wire form carries 3 fractional
      # digits, so equal rational milliseconds proves a lossless round-trip.
      _((round_tripped.taken_at.to_r * 1000).round).must_equal((original.to_r * 1000).round)
    end

    ['2024-01-01T10:00:00', '01/02/2024 10:00', 'tomorrow', '10:00', '1700000000', '2024-01-01'].each do |text|
      it "bhv_serializationobject_011 rejects the non-RFC 3339 date-time #{text.inspect}" do
        _ { Petstore::Client::ObjectSerializer.deserialize(JSON.generate(text), 'Time') }
          .must_raise(Petstore::Client::Errors::SerializationError)
      end
    end

    it 'bhv_serializationobject_003 rejects a date-time without an offset' do
      _ { Petstore::Client::ObjectSerializer.deserialize('"2024-01-01T10:00:00"', 'Time') }
        .must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'rejects a bare epoch number as a date-time' do
      _ { Petstore::Client::ObjectSerializer.deserialize('1700000000', 'Time') }
        .must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_064 accepts an RFC 3339 date-time with Z or a numeric offset and any fraction' do
      utc = Petstore::Client::ObjectSerializer.deserialize('"2024-01-01T10:00:00Z"', 'Time')
      _(utc).must_equal(Time.utc(2024, 1, 1, 10, 0, 0))
      offset = Petstore::Client::ObjectSerializer.deserialize('"2024-01-01T10:00:00.123456789+05:30"', 'Time')
      _(offset.utc_offset).must_equal(19_800)
      _(offset.nsec).must_equal(123_456_789)
    end
  end

  describe 'malformed date-time' do
    it 'bhv_serializationobject_102 raises SerializationError for a malformed date-time string' do
      _ { Petstore::Client::ObjectSerializer.deserialize('"not-a-date"', 'Time') }
        .must_raise(Petstore::Client::Errors::SerializationError)
    end
  end

  describe '.encode_base64_param' do
    it 'bhv_serializationobject_156 encodes format: byte parameter bytes as padded standard base64' do
      _(Petstore::Client::ObjectSerializer.encode_base64_param('hi')).must_equal('aGk=')
      _(Petstore::Client::ObjectSerializer.encode_base64_param("\xFB\xFF".b)).must_equal('+/8=')
      _(Petstore::Client::ObjectSerializer.encode_base64_param(nil)).must_be_nil
    end
  end

  it 'bhv_serializationobject_229 deserializes a map of models into a model instance for each value' do
    result = Petstore::Client::ObjectSerializer.deserialize(
      '{"dogs":{"id":1,"name":"Dogs"},"cats":{"id":2,"name":"Cats"}}', 'Hash<String, Category>'
    )
    _(result.keys.sort).must_equal(%w[cats dogs])
    _(result['dogs']).must_be_instance_of(Petstore::Client::Models::Category)
    _(result['dogs'].id).must_equal(1)
    _(result['cats'].name).must_equal('Cats')
  end

  describe 'null model elements in containers' do
    it 'bhv_serializationobject_121 rejects a null element in an array of models' do
      _ { Petstore::Client::ObjectSerializer.deserialize('[null,{"name":"a","photoUrls":[]}]', 'Array<Pet>') }
        .must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_123 rejects a null value in a map of models' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{"a":null,"b":{"name":"a","photoUrls":[]}}', 'Hash<String, Pet>') }
        .must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_122 rejects a null element of a map of int32 and of a nested array of int32' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{"a":1,"b":null}', 'Hash<String, Integer>') }
        .must_raise(Petstore::Client::Errors::SerializationError)
      _ { Petstore::Client::ObjectSerializer.deserialize('[[1,null]]', 'Array<Array<Integer>>') }
        .must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serialization_065 keeps a null element in an array of strings' do
      _(Petstore::Client::ObjectSerializer.deserialize('[null,"a"]', 'Array<String>')).must_equal([nil, 'a'])
    end
  end

  describe 'format: uri validation' do
    def decode_uri(text)
      json = JSON.generate({ 'name' => 'n', 'photoUrls' => [], 'homepageUrl' => text, 'thumbnailRef' => '/assets/thumb.png' })
      Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
    end

    ['urn:isbn:0451450523', 'mailto:a@b.com', 'ftp://x.org/f', 'https://example.com/items/1'].each do |text|
      it "bhv_serializationobject_021 accepts and re-emits the absolute URI #{text.inspect}" do
        decoded = decode_uri(text)
        _(decoded.homepage_url).must_equal(text)
        _(decoded.thumbnail_ref).must_equal('/assets/thumb.png')
        _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(decoded))['homepageUrl']).must_equal(text)
      end
    end

    it 'bhv_serializationobject_173 keeps a relative reference in a format: uri-reference field' do
      json = JSON.generate({ 'name' => 'n', 'photoUrls' => [], 'homepageUrl' => 'https://example.com/', 'thumbnailRef' => 'thumb.png' })
      decoded = Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      _(decoded.thumbnail_ref).must_equal('thumb.png')
    end

    ['/relative', 'not a uri', ''].each do |text|
      it "bhv_serializationobject_008 rejects #{text.inspect} as a format: uri value" do
        _ { decode_uri(text) }.must_raise(Petstore::Client::Errors::SerializationError)
      end
    end
  end

  describe 'NonAsciiSerialization' do
    it 'bhv_serializationobject_019 accented character serializes without unicode escape' do
      result = Petstore::Client::ObjectSerializer.serialize('café')
      _(result).must_include('é')
    end

    it 'bhv_serializationobject_031 CJK characters serialize without unicode escape' do
      result = Petstore::Client::ObjectSerializer.serialize('日本')
      _(result).must_include('日本')
    end

    it 'bhv_serializationobject_160 tab character is properly escaped in JSON' do
      result = Petstore::Client::ObjectSerializer.serialize("a\tb")
      _(result).must_include('\t')
    end
  end

  describe 'DeserializationErrorWrapping' do
    # The error contract: every wire-shape failure raises exactly
    # SerializationError (no subclass, no library error), and that is an
    # instance of the SDK root so one rescue catches it.
    {
      'malformed JSON' => ['{', 'Category'],
      'wrong primitive type' => ['{"id":"abc","name":"doggie","photoUrls":[]}', 'Pet'],
      'unknown enum value' => ['{"name":"doggie","photoUrls":[],"status":"banana"}', 'Pet'],
      'missing required field' => ['{"photoUrls":[]}', 'Pet'],
      'malformed duration' => ['{"retryAfter":"soon"}', 'EdgeCases'],
      'malformed date-time' => ['{"expiresAt":"not-a-date"}', 'EdgeCases']
    }.each do |failure, args|
      it "bhv_serializationobject_077 raises exactly SerializationError, a root error, for #{failure}" do
        json = args.fetch(0)
        type = args.fetch(1)
        err = _(proc {
          Petstore::Client::ObjectSerializer.deserialize(json, type)
        }).must_raise(Petstore::Client::Errors::SerializationError)
        _(err).must_be_instance_of(Petstore::Client::Errors::SerializationError)
        _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
      end
    end

    it 'bhv_serializationobject_066 raises SerializationError for text that is not JSON' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('not json', 'Category')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_095 raises SerializationError for JSON with a missing colon' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('{"name" "doggie","photoUrls":[]}', 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_180 raises SerializationError for a boolean where an integer is expected' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('{"name":"doggie","photoUrls":[],"id":true}', 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_170 truncated JSON raises SerializationError not a raw parse error' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('{', 'Category')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_089 incomplete JSON object raises SerializationError' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('{"name":', 'Category')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_162 thrown SerializationError has a cause referencing original error' do
      Petstore::Client::ObjectSerializer.deserialize('{', 'Category')
      flunk 'Expected SerializationError to be raised'
    rescue Petstore::Client::Errors::SerializationError => e
      _(e.cause).wont_be_nil
    end
  end

  describe '.stringify' do
    it 'bhv_serializationobject_110 returns empty string for nil' do
      _(Petstore::Client::ObjectSerializer.stringify(nil)).must_equal('')
    end

    it 'bhv_serializationobject_211 writes an int64 as its exact decimal digits' do
      _(Petstore::Client::ObjectSerializer.stringify(9_007_199_254_740_993)).must_equal('9007199254740993')
    end

    it 'bhv_serializationobject_028 returns "true" for boolean true' do
      _(Petstore::Client::ObjectSerializer.stringify(true)).must_equal('true')
    end

    it 'bhv_serializationobject_027 returns "false" for boolean false' do
      _(Petstore::Client::ObjectSerializer.stringify(false)).must_equal('false')
    end

    it 'bhv_serializationobject_091 returns string representation of integer' do
      _(Petstore::Client::ObjectSerializer.stringify(42)).must_equal('42')
    end

    it 'bhv_serializationobject_190 stringifies a long to its exact decimal digits' do
      _(Petstore::Client::ObjectSerializer.stringify(9_007_199_254_740_993)).must_equal('9007199254740993')
    end

    it 'bhv_serializationobject_157 returns ISO 8601 string for Time' do
      t = Time.new(2024, 1, 15, 10, 30, 0, '+00:00')
      result = Petstore::Client::ObjectSerializer.stringify(t)
      _(result).must_include('2024-01-15')
      _(result).must_include('10:30:00')
    end

    it 'bhv_serializationobject_155 stringifies a boolean to its lowercase word' do
      _(Petstore::Client::ObjectSerializer.stringify(true)).must_equal('true')
      _(Petstore::Client::ObjectSerializer.stringify(false)).must_equal('false')
    end

    it 'bhv_serializationobject_159 stringifies a string to itself, unescaped' do
      _(Petstore::Client::ObjectSerializer.stringify('say "hi" & more')).must_equal('say "hi" & more')
    end

    it 'bhv_serializationobject_114 passes through plain string unchanged' do
      _(Petstore::Client::ObjectSerializer.stringify('hello')).must_equal('hello')
    end

    it 'bhv_serializationobject_078 returns string representation of float' do
      _(Petstore::Client::ObjectSerializer.stringify(3.14)).must_equal('3.14')
    end

    it 'bhv_serializationobject_134 returns ISO 8601 date string for Date' do
      _(Petstore::Client::ObjectSerializer.stringify(Date.new(2024, 1, 15))).must_equal('2024-01-15')
    end
  end

  describe '.to_path_value' do
    it 'bhv_serializationobject_130 returns empty string for nil' do
      _(Petstore::Client::ObjectSerializer.to_path_value(nil)).must_equal('')
    end

    it 'bhv_serializationobject_141 returns the string for a string value' do
      _(Petstore::Client::ObjectSerializer.to_path_value('hello')).must_equal('hello')
    end

    it 'bhv_serializationobject_166 converts a boolean to its lowercase word' do
      _(Petstore::Client::ObjectSerializer.to_path_value(true)).must_equal('true')
      _(Petstore::Client::ObjectSerializer.to_path_value(false)).must_equal('false')
    end

    it 'bhv_serializationobject_036 converts integer to string' do
      _(Petstore::Client::ObjectSerializer.to_path_value(42)).must_equal('42')
    end

    it 'bhv_serializationobject_043 converts true to "true"' do
      _(Petstore::Client::ObjectSerializer.to_path_value(true)).must_equal('true')
    end

    it 'bhv_serializationobject_033 converts false to "false"' do
      _(Petstore::Client::ObjectSerializer.to_path_value(false)).must_equal('false')
    end
  end

  describe '.to_query_value' do
    it 'bhv_serializationobject_137 returns nil for nil' do
      _(Petstore::Client::ObjectSerializer.to_query_value(nil)).must_be_nil
    end

    it 'bhv_serializationobject_139 returns the string for a string value' do
      _(Petstore::Client::ObjectSerializer.to_query_value('hello')).must_equal('hello')
    end

    it 'bhv_serializationobject_037 converts integer to string' do
      _(Petstore::Client::ObjectSerializer.to_query_value(42)).must_equal('42')
    end

    it 'bhv_serializationobject_041 converts true to "true"' do
      _(Petstore::Client::ObjectSerializer.to_query_value(true)).must_equal('true')
    end

    it 'bhv_serializationobject_034 converts false to "false"' do
      _(Petstore::Client::ObjectSerializer.to_query_value(false)).must_equal('false')
    end

    it 'bhv_serializationobject_167 returns a string value unescaped' do
      _(Petstore::Client::ObjectSerializer.to_query_value('a b&c')).must_equal('a b&c')
    end

    it 'bhv_serializationobject_168 joins a string slice with commas for csv' do
      _(Petstore::Client::ObjectSerializer.to_query_value(%w[a b], :csv)).must_equal('a,b')
    end

    it 'bhv_serializationobject_169 joins a string slice with spaces for ssv' do
      _(Petstore::Client::ObjectSerializer.to_query_value(%w[a b], :ssv)).must_equal('a b')
    end

    it 'bhv_serializationobject_097 joins array with comma by default' do
      _(Petstore::Client::ObjectSerializer.to_query_value(%w[a b c])).must_equal('a,b,c')
    end

    it 'bhv_serializationobject_098 joins array with comma for csv' do
      _(Petstore::Client::ObjectSerializer.to_query_value(%w[a b c], :csv)).must_equal('a,b,c')
    end

    it 'bhv_serializationobject_100 joins array with space for ssv' do
      _(Petstore::Client::ObjectSerializer.to_query_value(%w[a b c], :ssv)).must_equal('a b c')
    end

    it 'bhv_serializationobject_101 joins array with tab for tsv' do
      _(Petstore::Client::ObjectSerializer.to_query_value(%w[a b c], :tsv)).must_equal("a\tb\tc")
    end

    it 'bhv_serializationobject_099 joins array with pipe for pipes' do
      _(Petstore::Client::ObjectSerializer.to_query_value(%w[a b c], :pipes)).must_equal('a|b|c')
    end

    it 'bhv_serializationobject_135 returns array as-is for multi' do
      _(Petstore::Client::ObjectSerializer.to_query_value(%w[a b c], :multi)).must_equal(%w[a b c])
    end
  end

  describe '.to_header_value' do
    it 'bhv_serializationobject_131 returns empty string for nil' do
      _(Petstore::Client::ObjectSerializer.to_header_value(nil)).must_equal('')
    end

    it 'bhv_serializationobject_142 returns the string for a string value' do
      _(Petstore::Client::ObjectSerializer.to_header_value('hello')).must_equal('hello')
    end

    it 'bhv_serializationobject_038 converts integer to string' do
      _(Petstore::Client::ObjectSerializer.to_header_value(42)).must_equal('42')
    end

    it 'bhv_serializationobject_210 joins a list of strings with commas' do
      _(Petstore::Client::ObjectSerializer.to_header_value(['alpha', 'beta gamma'])).must_equal('alpha,beta gamma')
    end

    it 'bhv_serializationobject_096 joins array with comma' do
      _(Petstore::Client::ObjectSerializer.to_header_value(%w[a b c])).must_equal('a,b,c')
    end
  end

  describe '.to_form_value' do
    it 'bhv_serializationobject_132 returns empty string for nil' do
      _(Petstore::Client::ObjectSerializer.to_form_value(nil)).must_equal('')
    end

    it 'bhv_serializationobject_140 returns the string for a string value' do
      _(Petstore::Client::ObjectSerializer.to_form_value('hello')).must_equal('hello')
    end

    it 'bhv_serializationobject_039 converts integer to string' do
      _(Petstore::Client::ObjectSerializer.to_form_value(42)).must_equal('42')
    end

    it 'bhv_serializationobject_042 converts true to "true"' do
      _(Petstore::Client::ObjectSerializer.to_form_value(true)).must_equal('true')
    end

    it 'bhv_serializationobject_035 converts false to "false"' do
      _(Petstore::Client::ObjectSerializer.to_form_value(false)).must_equal('false')
    end
  end

  describe '.to_cookie_value' do
    it 'bhv_serializationobject_133 returns empty string for nil' do
      _(Petstore::Client::ObjectSerializer.to_cookie_value(nil)).must_equal('')
    end

    it 'bhv_serializationobject_143 returns the string for a string value' do
      _(Petstore::Client::ObjectSerializer.to_cookie_value('hello')).must_equal('hello')
    end

    it 'bhv_serializationobject_040 converts integer to string' do
      _(Petstore::Client::ObjectSerializer.to_cookie_value(42)).must_equal('42')
    end
  end

  describe '.serialize' do
    it 'bhv_serializationobject_152 serializes a model to valid JSON' do
      category = Petstore::Client::Models::Category.new(id: 1, name: 'Dogs')
      json = Petstore::Client::ObjectSerializer.serialize(category)
      data = JSON.parse(json)
      _(data['id']).must_equal(1)
      _(data['name']).must_equal('Dogs')
    end

    it 'bhv_serializationobject_087 handles nil' do
      json = Petstore::Client::ObjectSerializer.serialize(nil)
      _(json).must_equal('null')
    end

    it 'bhv_serializationobject_188 an empty object serializes to an empty JSON object' do
      category = Petstore::Client::Models::Category.new(id: nil, name: nil)
      _(Petstore::Client::ObjectSerializer.serialize(category)).must_equal('{}')
    end

    it 'bhv_serializationobject_088 includes fields explicitly set to default values' do
      category = Petstore::Client::Models::Category.new(id: 0, name: '')
      json = Petstore::Client::ObjectSerializer.serialize(category)
      data = JSON.parse(json)
      _(data).must_include('id')
      _(data['id']).must_equal(0)
      _(data).must_include('name')
      _(data['name']).must_equal('')
    end

    it 'bhv_serializationobject_151 serializes a DateTime in the canonical date-time form' do
      json = Petstore::Client::ObjectSerializer.serialize(DateTime.new(2024, 1, 1, 12, 30, 45))
      _(JSON.parse(json)).must_equal('2024-01-01T12:30:45.000+00:00')
    end

    it 'bhv_serializationobject_045 serializes a Time in the canonical date-time form' do
      json = Petstore::Client::ObjectSerializer.serialize(Time.new(2024, 1, 1, 12, 30, 45, '+00:00'))
      _(JSON.parse(json)).must_equal('2024-01-01T12:30:45.000+00:00')
    end

    it 'bhv_serializationobject_209 returns a copy with the given fields replaced and leaves the original unchanged' do
      original = Petstore::Client::Models::Category.new(id: 1, name: 'Alpha')
      changed = original.with(name: 'Beta')
      _(changed.name).must_equal('Beta')
      _(changed.id).must_equal(1)
      _(original.name).must_equal('Alpha')
    end

    it 'bhv_serializationobject_165 serializes a Date in calendar form' do
      json = Petstore::Client::ObjectSerializer.serialize(Date.new(2024, 1, 1))
      _(JSON.parse(json)).must_equal('2024-01-01')
    end
  end

  describe '.deserialize' do
    it 'bhv_serializationobject_070 deserializes JSON to typed model' do
      json_str = '{"id":1,"name":"Dogs"}'
      category = Petstore::Client::ObjectSerializer.deserialize(json_str, 'Category')
      _(category).must_be_kind_of(Petstore::Client::Models::Category)
      _(category.id).must_equal(1)
      _(category.name).must_equal('Dogs')
    end

    # P2: a leading UTF-8 byte-order mark (BOM) must not break
    # deserialization. JSON.parse tolerates the BOM, so a Category prefixed
    # with U+FEFF round-trips to the same model. GREEN everywhere.
    it 'bhv_serializationobject_060 deserializes BOM-prefixed JSON to typed model' do
      json_str = '﻿{"id":1,"name":"Dogs"}'
      category = Petstore::Client::ObjectSerializer.deserialize(json_str, 'Category')
      _(category).must_be_kind_of(Petstore::Client::Models::Category)
      _(category.id).must_equal(1)
      _(category.name).must_equal('Dogs')
    end

    it 'bhv_serialization_028 raises SerializationError for a string body where an object model is expected' do
      err = _(proc {
        Petstore::Client::ObjectSerializer.deserialize('"oops"', 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
      _(err.message).must_include('Expected a JSON object for Petstore::Client::Models::Pet, got String')
    end

    it 'raises SerializationError for an array body where an object model is expected' do
      err = _(proc {
        Petstore::Client::ObjectSerializer.deserialize('[1,2]', 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
      _(err.message).must_include('Expected a JSON object for Petstore::Client::Models::Pet, got Array')
    end

    it 'bhv_serializationobject_118 raises SerializationError for a scalar where a nested model is expected' do
      err = _(proc {
        Petstore::Client::ObjectSerializer.deserialize('{"name":"x","photoUrls":[],"category":5}', 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
      _(err.message).must_include('Expected a JSON object for Petstore::Client::Models::Category, got Integer')
    end

    it 'raises SerializationError for an array where a nested model is expected' do
      err = _(proc {
        Petstore::Client::ObjectSerializer.deserialize('{"name":"x","photoUrls":[],"category":[1,2]}', 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
      _(err.message).must_include('Expected a JSON object for Petstore::Client::Models::Category, got Array')
    end

    it 'bhv_serializationobject_136 returns nil for empty input' do
      _(Petstore::Client::ObjectSerializer.deserialize('', 'Category')).must_be_nil
    end

    it 'bhv_serializationobject_138 returns nil for nil input' do
      _(Petstore::Client::ObjectSerializer.deserialize(nil, 'Category')).must_be_nil
    end

    # Order.status carries an OpenAPI schema `default: placed`. When the wire
    # JSON omits `status` entirely, deserialization must populate it with the
    # schema default ('placed') rather than leaving it nil. dry-struct's
    # `.default('placed')` on the attribute supplies this on the absent key.
    it 'bhv_serializationobject_053 deserialize applies schema default for absent field' do
      order = Petstore::Client::ObjectSerializer.deserialize('{"id":10,"petId":198772}', 'Order')
      _(order).must_be_kind_of(Petstore::Client::Models::Order)
      _(order.status).must_equal('placed')
    end

    # Defaults model: schema `default` applies ONLY when the property is
    # ABSENT from the payload; an explicit JSON `null` is a provided value
    # and is PRESERVED (the field stays nil), not replaced by the default.
    # This matches the JSON-Schema-correct behaviour of go/rust/python.
    # deserialize_model skips absent keys (so dry-struct's `.default(...)`
    # fires) but passes an explicit null through, which `.optional` keeps.
    it 'bhv_serializationobject_025 applies defaults only for absent fields, preserving explicit null' do
      from_empty = Petstore::Client::ObjectSerializer.deserialize('{}', 'Defaults')
      _(from_empty).must_be_kind_of(Petstore::Client::Models::Defaults)
      _(from_empty.retries).must_equal(3)
      _(from_empty.mode).must_equal('medium')
      _(from_empty.label).must_equal('untitled')
      _(from_empty.greeting).must_equal("it's \"quoted\" \\ back\tslash\nnext $USD \#{x} a*/b")

      from_null = Petstore::Client::ObjectSerializer.deserialize('{"label":null,"retries":7}', 'Defaults')
      _(from_null).must_be_kind_of(Petstore::Client::Models::Defaults)
      _(from_null.label).must_be_nil
      _(from_null.retries).must_equal(7)
    end

    it 'bhv_serializationobject_056 keeps an explicit null over the schema default' do
      defaults = Petstore::Client::ObjectSerializer.deserialize('{"label":null}', 'Defaults')
      _(defaults.label).must_be_nil
      _(defaults.retries).must_equal(3)
    end

    # Numeric defaults keep their exact typed value, and the component
    # default of a $ref'd enum applies on absence.
    it 'bhv_serializationobject_111 applies exact numeric and referenced-enum defaults for absent fields' do
      defaults = Petstore::Client::ObjectSerializer.deserialize('{}', 'Defaults')
      _(defaults.ratio).must_equal(1.5)
      _(defaults.scale).must_equal(0.25)
      _(defaults.weight).must_equal(2)
      _(defaults.ceiling).must_equal(9_000_000_000)
      _(defaults.swatch).must_equal('green')
    end

    # Department and Employee reference each other: both directions decode to
    # typed models and write back unchanged.
    it 'bhv_serializationobject_189 decodes and writes mutually recursive Department and Employee models' do
      json = '{"name":"Eng","lead":{"name":"Ann","department":{"name":"Eng"}}}'
      department = Petstore::Client::ObjectSerializer.deserialize(json, 'Department')
      _(department.lead).must_be_kind_of(Petstore::Client::Models::Employee)
      _(department.lead.department).must_be_kind_of(Petstore::Client::Models::Department)
      _(department.lead.department.name).must_equal('Eng')
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(department))).must_equal(JSON.parse(json))
    end

    # Inline property enums are nested constants classes named
    # <Model>::<Prop>Enum with the same shape as a top-level enum class.
    it 'exposes inline property enums as nested constants classes' do
      mode = Petstore::Client::Models::Defaults::ModeEnum
      _(mode::VALUES).must_equal(%w[low medium high])
      _(mode.validate!('high')).must_equal('high')
      _ { mode.validate!('nope') }.must_raise(ArgumentError)

      phase = Petstore::Client::Models::EdgeCases::PhaseEnum
      _(phase::VALUES).must_equal(%w[In-Stock on-hold OUT_OF_STOCK])
      _ { phase.validate!('bogus') }.must_raise(ArgumentError)
      edge = Petstore::Client::ObjectSerializer.deserialize('{"phase":"on-hold"}', 'EdgeCases')
      _(edge.phase).must_equal('on-hold')
      _ { Petstore::Client::ObjectSerializer.deserialize('{"phase":"bogus"}', 'EdgeCases') }.must_raise(StandardError)

      status = Petstore::Client::Models::Order::StatusEnum
      _(status::PLACED).must_equal('placed')
      _(status::VALUES).must_equal(%w[placed approved delivered])
      _ { status.validate!('bogus') }.must_raise(ArgumentError)
      order = Petstore::Client::ObjectSerializer.deserialize('{"status":"approved"}', 'Order')
      _(order.status).must_equal('approved')
    end

    it 'bhv_serializationobject_022 round-trips every EdgeCases.phase wire value' do
      %w[In-Stock on-hold OUT_OF_STOCK].each do |value|
        edge = Petstore::Client::ObjectSerializer.deserialize(JSON.generate({ 'phase' => value }), 'EdgeCases')
        _(edge.phase).must_equal(value)
        _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(edge))['phase']).must_equal(value)
      end
    end

    # A keyword that Ruby accepts as a method name stays plain (`and`,
    # `return`); only `class`, which would shadow Object#class, and the
    # literals keep the underscore. The wire keys are unchanged either way.
    it 'bhv_serializationobject_117 keeps plain keyword property names and the wire keys of EdgeCases' do
      json = '{"and":true,"or":false,"not":true,"class":"c","return":"r","null":"n","true":true}'
      edge = Petstore::Client::ObjectSerializer.deserialize(json, 'EdgeCases')
      _(edge.and).must_equal(true)
      _(edge.or).must_equal(false)
      _(edge.not).must_equal(true)
      _(edge.return).must_equal('r')
      _(edge._class).must_equal('c')
      _(edge.null).must_equal('n')
      _(edge._true).must_equal(true)
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(edge))).must_equal(JSON.parse(json))
    end

    # Spec prose in a model's externalDocs reaches the comment as written:
    # no HTML entity and no literal escape.
    it 'bhv_serializationobject_227 documents the Account externalDocs description verbatim' do
      path = String(Object.const_source_location(Petstore::Client::Models::Account.name)&.first)
      _(File.read(path)).must_include(%q(# @see https://example.com/docs/account Account <docs> & "notes" it's here))
    end

    # readOnly and writeOnly have no wire effect: a readOnly property is still
    # decoded and a writeOnly one is still written and decoded.
    it 'bhv_serializationobject_014 decodes a readOnly property' do
      account = Petstore::Client::ObjectSerializer.deserialize('{"id":7,"username":"ann"}', 'Account')
      _(account.id).must_equal(7)
    end

    it 'bhv_serializationobject_018 serializes a writeOnly property' do
      account = Petstore::Client::Models::Account.new(username: 'ann', password: 'hunter2')
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(account))).must_equal(
        'username' => 'ann', 'password' => 'hunter2'
      )
    end

    it 'bhv_serializationobject_017 decodes a writeOnly property' do
      account = Petstore::Client::ObjectSerializer.deserialize('{"username":"ann","password":"hunter2"}', 'Account')
      _(account.password).must_equal('hunter2')
    end

    # format: password stays a plain String but never prints in a model's
    # string form.
    it 'bhv_serializationobject_007 masks a format: password property in inspect' do
      account = Petstore::Client::Models::Account.new(username: 'ann', password: 'hunter2')
      _(account.inspect).must_include('password=***')
      _(account.inspect).must_include('username="ann"')
      _(account.inspect).wont_include('hunter2')
      _(account.password).must_equal('hunter2')
    end

    # Self-referential model (TreeNode has a `child` of its own type). A
    # nested payload must decode every level into a typed TreeNode, with the
    # absent innermost `child` left nil rather than wrapped in an empty model.
    it 'bhv_serializationobject_068 deserializes a self-referential TreeNode preserving nesting' do
      json = '{"value":"root","child":{"value":"leaf"}}'
      top = Petstore::Client::ObjectSerializer.deserialize(json, 'TreeNode')
      _(top).must_be_kind_of(Petstore::Client::Models::TreeNode)
      _(top.value).must_equal('root')
      _(top.child).must_be_kind_of(Petstore::Client::Models::TreeNode)
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
    it 'bhv_serializationobject_083 raises when a required field is absent from the JSON' do
      # photoUrls present, name missing.
      json = '{"id":1,"photoUrls":["http://example.com/p.jpg"]}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_063 raises when a second required field is absent from the JSON' do
      # name present, photoUrls missing.
      json = '{"id":1,"name":"doggie"}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_084 raises when a required field is explicitly null in the JSON' do
      json = '{"id":1,"name":null,"photoUrls":["http://example.com/p.jpg"]}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_062 raises when a required container field is explicitly null' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('{"name":"doggie","photoUrls":null}', 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_061 deserializes successfully when all required fields are present' do
      json = '{"id":1,"name":"doggie","photoUrls":["http://example.com/p.jpg"]}'
      pet = Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      _(pet).must_be_kind_of(Petstore::Client::Models::Pet)
      _(pet.name).must_equal('doggie')
      _(pet.photo_urls.to_a).must_equal(['http://example.com/p.jpg'])
    end

    it 'bhv_serializationobject_231 leaves optional fields untouched when omitted' do
      # Only the required fields are supplied; optional `id`/`status` stay nil.
      json = '{"name":"doggie","photoUrls":["http://example.com/p.jpg"]}'
      pet = Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      _(pet.name).must_equal('doggie')
      _(pet.id).must_be_nil
      _(pet.status).must_be_nil
    end
  end

  # ── nil discard on serialize (#13) ──

  describe 'nil discard on serialize' do
    it 'bhv_serializationobject_147 omits hash entries with nil values from serialized JSON' do
      input = { 'present' => 'value', 'absent' => nil }
      json = Petstore::Client::ObjectSerializer.serialize(input)
      data = JSON.parse(json)
      _(data).must_include 'present'
      _(data['present']).must_equal 'value'
      _(data).wont_include 'absent'
    end

    it 'bhv_serializationobject_023 omits nested nil hash values from serialized JSON' do
      input = { 'outer' => { 'a' => 1, 'b' => nil, 'c' => 3 } }
      json = Petstore::Client::ObjectSerializer.serialize(input)
      data = JSON.parse(json)
      _(data['outer']).must_include 'a'
      _(data['outer']).wont_include 'b'
      _(data['outer']).must_include 'c'
    end

    it 'bhv_serializationobject_202 omits a null field on serialize while the non-null fields are kept' do
      item = Petstore::Client::Models::StockItem.new(priority: 1, availability: nil)
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(item))).must_equal('priority' => 1)
    end

    it 'bhv_serializationobject_183 handles null' do
      _(Petstore::Client::ObjectSerializer.deserialize(nil, 'Pet')).must_be_nil
    end

    it 'keeps nil array elements (only hashes filter)' do
      json = Petstore::Client::ObjectSerializer.serialize([1, nil, 3])
      _(JSON.parse(json)).must_equal [1, nil, 3]
    end
  end

  # ── enum unknown value throws on deserialize (#12) ──

  describe 'enum unknown value detection' do
    it 'bhv_serializationobject_172 raises SerializationError when deserializing unknown enum value' do
      # An unknown standalone-enum value is a schema-validation failure, the
      # same class as a missing required field or a strict type mismatch.
      # deserialize wraps all of them uniformly as SerializationError so a
      # caller can catch every bad-payload case with one rescue, rather than
      # this path leaking a raw stdlib ArgumentError.
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('"shipped"', 'TestStatusEnumDeserialize')
      end
    end

    it 'bhv_serializationobject_192 fails loudly at decode for an unknown enum string' do
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('"banana"', 'TestStatusEnumDeserialize')
      end
    end

    it 'bhv_serialization_003 returns value when deserializing valid enum value' do
      result = Petstore::Client::ObjectSerializer.deserialize('"approved"', 'TestStatusEnumDeserialize')
      _(result).must_equal 'approved'
    end

    # Canonical behavior 6 through a real generated model: Pet.status is an
    # inline enum (available/pending/sold) backed by dry-struct's
    # Types::String.enum. An unknown wire value must surface the SDK
    # (de)serialization error rather than silently coercing to a default or
    # an "unknown" sentinel.
    it 'bhv_serializationobject_182 raises SerializationError for an unknown inline enum value on deserialize' do
      json = '{"name":"doggie","photoUrls":["http://x/p.jpg"],"status":"banana"}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_024 names the enum and the unknown value when an inline model enum fails to decode' do
      json = '{"name":"doggie","photoUrls":["http://x/p.jpg"],"status":"banana"}'
      error = _(proc { Petstore::Client::ObjectSerializer.deserialize(json, 'Pet') }).must_raise(
        Petstore::Client::Errors::SerializationError
      )
      _(error.message).must_include 'Unknown enum value for Pet::StatusEnum: "banana"'
    end

    # F316: building a model never validates. An unknown enum string and a value
    # of the wrong type are held as given; only decoding fails on them.
    it 'builds a model holding an unknown inline enum string without raising' do
      pet = Petstore::Client::Models::Pet.new(name: 'Rex', photo_urls: Set['u'], status: 'banana')

      _(pet.status).must_equal 'banana'
    end

    it 'bhv_serializationobject_030 builds a model holding values of the wrong type without raising' do
      pet = Petstore::Client::Models::Pet.new(name: 42, photo_urls: 'not-a-set', id: 'abc', status: 7)

      _(pet.name).must_equal 42
      _(pet.id).must_equal 'abc'
      _(pet.status).must_equal 7
    end

    it 'copies a model into an unknown inline enum string without raising' do
      pet = Petstore::Client::Models::Pet.new(name: 'Rex', photo_urls: Set['u']).with(status: 'banana')

      _(pet.status).must_equal 'banana'
    end

    it 'bhv_serializationobject_069 deserializes an inline model enum with a known value' do
      json = '{"name":"doggie","photoUrls":["http://x/p.jpg"],"status":"available"}'
      pet = Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      _(pet.status).must_equal 'available'
    end
  end

  # ── enum frozen const + VALUES validation (#11) ──

  describe 'enum frozen consts' do
    it 'enum constants are frozen strings' do
      _(Petstore::Client::Models::TestStatusEnumValidate::PLACED).must_be :frozen?
      _(Petstore::Client::Models::TestStatusEnumValidate::APPROVED).must_be :frozen?
    end

    it 'VALUES contains all enum constants and is frozen' do
      values = Petstore::Client::Models::TestStatusEnumValidate::VALUES
      _(values).must_be :frozen?
      _(values).must_include 'placed'
      _(values).must_include 'approved'
      _(values).must_include 'delivered'
    end

    it 'bhv_serializationobject_029 validate! returns value when in VALUES' do
      _(Petstore::Client::Models::TestStatusEnumValidate.validate!('approved')).must_equal 'approved'
    end

    it 'bhv_serialization_004 validate! raises ArgumentError for value not in VALUES' do
      assert_raises(ArgumentError) do
        Petstore::Client::Models::TestStatusEnumValidate.validate!('shipped')
      end
    end
  end

  # ── discard extras on deserialize (#14) ──

  describe 'extras discarded on deserialize' do
    it 'bhv_serializationobject_082 silently ignores unknown JSON keys not in OPENAPI_TYPES' do
      json_str = '{"id":1,"name":"Dogs","unexpected":"extra","another":42}'
      category = Petstore::Client::ObjectSerializer.deserialize(json_str, 'Category')
      _(category).must_be_kind_of Petstore::Client::Models::Category
      _(category.id).must_equal 1
      _(category.name).must_equal 'Dogs'
      _(category.respond_to?(:unexpected)).must_equal false
    end
  end

  # ── Gap V — NaN/Infinity rejection (RFC 8259 §6) ──

  describe 'NaN/Infinity rejection' do
    it 'bhv_serializationobject_153 serialize raises on NaN' do
      # ObjectSerializer wraps JSON::GeneratorError as SerializationError.
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.serialize({ 'val' => Float::NAN })
      end
    end

    it 'serialize raises on +Infinity' do
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.serialize({ 'val' => Float::INFINITY })
      end
    end

    it 'serialize raises on -Infinity' do
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.serialize({ 'val' => -Float::INFINITY })
      end
    end

    it 'bhv_serializationobject_200 a serialize failure starts with the canonical prefix and names the detail' do
      error = _ { Petstore::Client::ObjectSerializer.serialize({ 'val' => Float::NAN }) }
        .must_raise(Petstore::Client::Errors::SerializationError)
      _(error.message).must_match(/\AFailed to serialize object to JSON: /)
      _(error.message).must_include('NaN')
    end

    it 'bhv_serialization_035 deserialize raises on NaN literal' do
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('{"val": NaN}', 'Object')
      end
    end

    it 'bhv_serializationobject_057 deserialize raises on a double literal that overflows to Infinity' do
      error = _ { Petstore::Client::ObjectSerializer.deserialize('{"lat":1e400}', 'PhotoMetadataLocation') }
              .must_raise Petstore::Client::Errors::SerializationError

      _(error.message).must_include('Expected a finite number')
    end

    it 'bhv_serialization_034 deserialize keeps the largest finite double' do
      location = Petstore::Client::ObjectSerializer.deserialize('{"lat":1.7976931348623157e308}', 'PhotoMetadataLocation')

      _(location.lat).must_equal(Float::MAX)
    end

    it 'deserialize raises on Infinity literal' do
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('{"val": Infinity}', 'Object')
      end
    end
  end

  # ── format: byte (Buffer/bytes surface, 2.1) ──

  describe 'format: byte base64 transcoding' do
    it 'bhv_serialization_062 decodes base64 wire value into binary-encoded String on deserialize' do
      raw = "hello\x00\xFFworld".dup.force_encoding(Encoding::BINARY)
      encoded = Base64.strict_encode64(raw)
      json = %({"blob":"#{encoded}","note":"x"})
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'TestFormatModel')
      _(result.blob).must_equal(raw)
      _(result.blob.encoding).must_equal(Encoding::BINARY)
      _(result.note).must_equal('x')
    end

    it 'bhv_serialization_063 encodes binary String back to base64 on serialize' do
      raw = "\x00\x01\x02\xFF".dup.force_encoding(Encoding::BINARY)
      model = Petstore::Client::Models::TestFormatModel.new(blob: raw, note: 'y')
      json = Petstore::Client::ObjectSerializer.serialize(model)
      data = JSON.parse(json)
      _(data['blob']).must_equal(Base64.strict_encode64(raw))
      _(data['note']).must_equal('y')
    end

    it 'bhv_serializationobject_026 round-trip preserves bytes exactly' do
      raw = (0..255).map(&:chr).join.dup.force_encoding(Encoding::BINARY)
      model = Petstore::Client::Models::TestFormatModel.new(blob: raw)
      json = Petstore::Client::ObjectSerializer.serialize(model)
      restored = Petstore::Client::ObjectSerializer.deserialize(json, 'TestFormatModel')
      _(restored.blob).must_equal(raw)
    end

    it 'bhv_serializationobject_185 accepts canonical padded standard base64 for a byte field' do
      result = Petstore::Client::ObjectSerializer.deserialize('{"blob":"dGVzdA==","note":"x"}', 'TestFormatModel')
      _(result.blob).must_equal('test'.b)
    end

    it 'bhv_serializationobject_207 rejects a non-standard base64 value on decode' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('{"blob":"-_8="}', 'TestFormatModel')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_094 raises SerializationError on invalid base64 input' do
      json = '{"blob":"not valid base64!!!"}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'TestFormatModel')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'base64-decodes a top-level byte response (JSON string literal)' do
      # A top-level `type: string, format: byte` response carried as
      # application/json arrives as a JSON string literal. deserialize must
      # JSON-parse it and then base64-decode the inner string to raw bytes.
      raw = 'test-image'.dup.force_encoding(Encoding::BINARY)
      json = Base64.strict_encode64(raw).to_json
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'ByteArray')
      _(result).must_equal(raw)
      _(result.encoding).must_equal(Encoding::BINARY)
    end
  end

  # ── unevaluatedProperties:false (StrictTag, 2.20) ──

  describe 'unevaluatedProperties:false enforcement' do
    it 'bhv_serializationobject_154 rejects an undeclared key in the raw payload' do
      json = '{"id":1,"name":"Dogs","rogue":"x"}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'StrictTag')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_052 writes back every declared key of a strict model it accepted' do
      json = '{"id":1,"name":"Dogs"}'
      strict = Petstore::Client::ObjectSerializer.deserialize(json, 'StrictTag')
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(strict))).must_equal(JSON.parse(json))
    end

    it 'bhv_serializationobject_058 rejects a strict model payload whose only key is undeclared' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{"rogue":"x"}', 'StrictTag') }
        .must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_047 accepts a payload with only declared keys' do
      result = Petstore::Client::ObjectSerializer.deserialize('{"id":1,"name":"Dogs"}', 'StrictTag')
      _(result).wont_be_nil
      _(result.id).must_equal(1)
      _(result.name).must_equal('Dogs')
    end
  end

  # ── format: uuid (typed UUID surface, 2.2) ──

  describe 'format: uuid validation' do
    it 'bhv_serializationobject_175 accepts canonical RFC 4122 UUID on deserialize' do
      json = '{"identifier":"550e8400-e29b-41d4-a716-446655440000"}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'TestFormatModel')
      _(result.identifier).must_equal('550e8400-e29b-41d4-a716-446655440000')
    end

    it 'bhv_serializationobject_103 raises SerializationError on malformed UUID' do
      json = '{"identifier":"not-a-uuid"}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'TestFormatModel')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_080 raises SerializationError on serialize with malformed UUID' do
      model = Petstore::Client::Models::TestFormatModel.new(identifier: 'bogus')
      _(proc {
        Petstore::Client::ObjectSerializer.serialize(model)
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_065 serializes a valid UUID unchanged' do
      uuid = '00112233-4455-6677-8899-aabbccddeeff'
      model = Petstore::Client::Models::TestFormatModel.new(identifier: uuid)
      json = Petstore::Client::ObjectSerializer.serialize(model)
      _(JSON.parse(json)['identifier']).must_equal(uuid)
    end
  end

  describe 'format: uuid canonical form' do
    it 'bhv_serializationobject_148 writes an uppercase uuid in lowercase hyphenated form' do
      order = Petstore::Client::Models::Order.new(tracking_id: 'E621E1F8-C36C-495A-93FC-0C247A3E6E5F')
      _(Petstore::Client::ObjectSerializer.serialize(order)).must_include('"trackingId":"e621e1f8-c36c-495a-93fc-0c247a3e6e5f"')
    end

    it 'bhv_serializationobject_016 decodes an uppercase uuid to its lowercase form' do
      order = Petstore::Client::ObjectSerializer.deserialize(
        '{"trackingId":"E621E1F8-C36C-495A-93FC-0C247A3E6E5F"}', 'Petstore::Client::Models::Order'
      )
      _(order.tracking_id).must_equal('e621e1f8-c36c-495a-93fc-0c247a3e6e5f')
    end

    it 'bhv_serializationobject_048 rejects a malformed uuid on decode' do
      err = assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('{"trackingId":"not-a-uuid"}', 'Petstore::Client::Models::Order')
      end
      _(err.message).must_equal('Invalid UUID for format: uuid: "not-a-uuid"')
    end
  end

  describe 'uniqueItems arrays of scalars' do
    it 'bhv_serializationobject_015 writes the elements in ascending order whatever the insertion order' do
      2.times do
        item = Petstore::Client::Models::Pet.new(name: 'n', photo_urls: Set['c', 'a', 'b'])
        _(Petstore::Client::ObjectSerializer.serialize(item)).must_include('"photoUrls":["a","b","c"]')
      end
    end
  end

  describe 'required and nullable property' do
    it 'bhv_serializationobject_049 decodes an explicit null to nil' do
      model = Petstore::Client::ObjectSerializer.deserialize('{"note":null}', 'Petstore::Client::Models::RequiredNullable')
      _(model.note).must_be_nil
    end

    it 'bhv_serializationobject_050 rejects a payload without the key' do
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('{}', 'Petstore::Client::Models::RequiredNullable')
      end
    end

    it 'bhv_serializationobject_126 rejects a required nullable property whose key is missing' do
      _ { Petstore::Client::ObjectSerializer.deserialize('{}', 'Petstore::Client::Models::RequiredNullable') }
        .must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_218 writes a value of a required nullable property as given' do
      model = Petstore::Client::Models::RequiredNullable.new(note: 'n')
      _(Petstore::Client::ObjectSerializer.serialize(model)).must_equal('{"note":"n"}')
    end

    it 'bhv_serializationobject_125 writes a nil value as an explicit null' do
      model = Petstore::Client::Models::RequiredNullable.new(note: nil)
      _(Petstore::Client::ObjectSerializer.serialize(model)).must_equal('{"note":null}')
    end
  end

  # ── fixture round trips (model bodies the format tables above do not reach) ──

  describe 'EdgeCases model body' do
    it 'bhv_serializationobject_219 decodes and writes a format: time property, keeping the milliseconds' do
      edge = Petstore::Client::ObjectSerializer.deserialize('{"cutoff":"10:30:00.123"}', 'EdgeCases')
      _(edge.cutoff).must_be_kind_of(Tod::TimeOfDay)
      _(edge.cutoff.hour).must_equal(10)
      _(edge.cutoff.minute).must_equal(30)
      _(Petstore::Client::ObjectSerializer.serialize(edge)).must_equal('{"cutoff":"10:30:00.123"}')
    end

    it 'bhv_serialization_038 cuts a finer fraction of a format: time property to the millisecond' do
      edge = Petstore::Client::ObjectSerializer.deserialize('{"cutoff":"10:30:00.123456789"}', 'EdgeCases')
      _(Petstore::Client::ObjectSerializer.serialize(edge)).must_equal('{"cutoff":"10:30:00.123"}')
    end

    it 'bhv_serializationobject_163 writes a format: time property without a fraction when it has none or it is zero' do
      whole = Petstore::Client::ObjectSerializer.deserialize('{"cutoff":"10:30:00"}', 'EdgeCases')
      zero = Petstore::Client::ObjectSerializer.deserialize('{"cutoff":"10:30:00.000"}', 'EdgeCases')
      _(Petstore::Client::ObjectSerializer.serialize(whole)).must_equal('{"cutoff":"10:30:00"}')
      _(Petstore::Client::ObjectSerializer.serialize(zero)).must_equal('{"cutoff":"10:30:00"}')
    end

    it 'bhv_serializationobject_013 decodes and writes a protobuf-JSON duration property' do
      edge = Petstore::Client::ObjectSerializer.deserialize('{"retryAfter":"3600s"}', 'EdgeCases')
      _(edge.retry_after).must_be_kind_of(ISO8601::Duration)
      _(edge.retry_after.to_seconds).must_equal(3600)
      _(Petstore::Client::ObjectSerializer.serialize(edge)).must_equal('{"retryAfter":"3600s"}')
    end

    # D88: the protobuf duration range is +-315 576 000 000 s.
    it 'bhv_serializationobject_223 accepts a duration at the protobuf bound and rejects one past it' do
      ok = Petstore::Client::ObjectSerializer.deserialize('{"retryAfter":"315576000000s"}', 'EdgeCases')
      _(ok.retry_after).must_be_kind_of(ISO8601::Duration)
      ['315576000001s', '-315576000001s', '315576000000.000000001s'].each do |text|
        _(proc {
          Petstore::Client::ObjectSerializer.deserialize(%({"retryAfter":"#{text}"}), 'EdgeCases')
        }).must_raise(Petstore::Client::Errors::SerializationError)
      end
    end

    # The models named Error and Result keep their names inside Models and
    # round trip as the nested lastError and outcome properties.
    it 'bhv_serializationobject_104 round trips the Error and Result models as nested properties' do
      json = '{"lastError":{"code":7,"message":"boom"},"outcome":{"ok":true}}'
      edge = Petstore::Client::ObjectSerializer.deserialize(json, 'EdgeCases')
      _(edge.last_error).must_be_kind_of(Petstore::Client::Models::Error)
      _(edge.last_error.code).must_equal(7)
      _(edge.last_error.message).must_equal('boom')
      _(edge.outcome).must_be_kind_of(Petstore::Client::Models::Result)
      _(edge.outcome.ok).must_equal(true)
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(edge))).must_equal(JSON.parse(json))
    end

    # Separator values sanitise to colliding or empty constant names; the wire
    # value is what round trips.
    [' ', '@@', 'a-b', 'a_b'].each do |value|
      it "bhv_serializationobject_161 round trips the Separator value #{value.inspect}" do
        edge = Petstore::Client::ObjectSerializer.deserialize(JSON.generate({ 'separator' => value }), 'EdgeCases')
        _(edge.separator).must_equal(value)
        _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(edge))['separator']).must_equal(value)
      end
    end
  end

  # S3 / D55: the client does not enforce pattern, minimum or maximum; a value
  # outside the declared bounds decodes as sent and the server owns validation.
  describe 'schema constraints are not enforced' do
    it 'decodes a quantity above its maximum' do
      order = Petstore::Client::ObjectSerializer.deserialize('{"quantity":500}', 'Order')
      _(order.quantity).must_equal(500)
    end

    it 'bhv_serializationobject_032 decodes a nickname and a slug that violate their patterns' do
      pet = Petstore::Client::ObjectSerializer.deserialize('{"name":"Rex","photoUrls":[],"nickname":"X!"}', 'Pet')
      _(pet.nickname).must_equal('X!')
      edge = Petstore::Client::ObjectSerializer.deserialize('{"slug":"not-a-slug"}', 'EdgeCases')
      _(edge.slug).must_equal('not-a-slug')
    end
  end

  describe 'values outside a pattern or a bound round trip' do
    it 'bhv_serializationobject_010 decodes and writes a nickname longer than its maximum length' do
      pet = Petstore::Client::ObjectSerializer.deserialize('{"name":"Rex","photoUrls":[],"nickname":"ZZZZZZZZZZ"}', 'Pet')
      _(pet.nickname).must_equal('ZZZZZZZZZZ')
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(pet))['nickname']).must_equal('ZZZZZZZZZZ')
    end

    it 'bhv_serializationobject_074 decodes and writes a slug that matches its pattern' do
      edge = Petstore::Client::ObjectSerializer.deserialize('{"slug":"12/ab"}', 'EdgeCases')
      _(edge.slug).must_equal('12/ab')
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(edge))['slug']).must_equal('12/ab')
    end
  end

  # Byte arrays compare by content, and a list of them is not a map of them.
  describe 'BinaryVault equality' do
    decode = lambda do |json|
      Petstore::Client::ObjectSerializer.deserialize(json, 'BinaryVault')
    end

    it 'bhv_serializationobject_090 treats two vaults decoded from the same bytes as equal' do
      json = '{"seal":"AQID","shards":["AQID","BAUG"],"labels":{"a":"AQID"}}'
      _(decode.call(json)).must_equal(decode.call(json))
      _(decode.call(json).hash).must_equal(decode.call(json).hash)
    end

    it 'bhv_serializationobject_075 tells vaults with different bytes apart, bare, listed and mapped' do
      base = decode.call('{"seal":"AQID","shards":["AQID"],"labels":{"a":"AQID"}}')
      _(base).wont_equal(decode.call('{"seal":"BAUG","shards":["AQID"],"labels":{"a":"AQID"}}'))
      _(base).wont_equal(decode.call('{"seal":"AQID","shards":["BAUG"],"labels":{"a":"AQID"}}'))
      _(base).wont_equal(decode.call('{"seal":"AQID","shards":["AQID"],"labels":{"a":"BAUG"}}'))
    end

    it 'bhv_serializationobject_206 tells vaults whose map keys differ apart' do
      _(decode.call('{"labels":{"a":"AQID"}}')).wont_equal(decode.call('{"labels":{"b":"AQID"}}'))
    end

    it 'bhv_serializationobject_009 does not confuse a list of byte arrays with a map of them' do
      as_list = decode.call('{"shards":["AQID"]}')
      as_map = decode.call('{"labels":{"0":"AQID"}}')
      _(as_list).wont_equal(as_map)
    end
  end

  # A schema with additionalProperties keeps the extra keys next to the
  # declared properties, and a round trip writes them back.
  describe 'additionalProperties' do
    it 'bhv_serializationobject_020 captures string-valued extras and writes them back at the top level' do
      json = '{"createdAt":"2024-01-01T00:00:00Z","note":"x","owner":"ann"}'
      meta = Petstore::Client::ObjectSerializer.deserialize(json, 'Metadata')
      _(meta.additional_properties).must_equal('note' => 'x', 'owner' => 'ann')
      written = JSON.parse(Petstore::Client::ObjectSerializer.serialize(meta))
      _(written['note']).must_equal('x')
      _(written['owner']).must_equal('ann')
    end

    it 'bhv_serializationobject_205 an extra key survives a full deserialize then serialize round trip' do
      json = '{"createdAt":"2024-01-01T00:00:00Z","region":"eu"}'
      wire = Petstore::Client::ObjectSerializer.serialize(Petstore::Client::ObjectSerializer.deserialize(json, 'Metadata'))
      _(JSON.parse(wire)['region']).must_equal('eu')
      _(JSON.parse(wire)).wont_include('additional_properties')
      again = Petstore::Client::ObjectSerializer.deserialize(wire, 'Metadata')
      _(again.additional_properties).must_equal('region' => 'eu')
    end
  end

  # D46: Availability members that are special inside a string literal keep
  # their exact wire value through decode and encode.
  describe 'Availability members with literal-special characters' do
    ["o'clock", 'a\\b', '$eq', '#{x}'].each do |value|
      it "bhv_serializationobject_073 round trips #{value.inspect}" do
        _(Petstore::Client::Models::Availability::VALUES).must_include(value)
        json = JSON.generate({ 'priority' => 1, 'availability' => value })
        item = Petstore::Client::ObjectSerializer.deserialize(json, 'StockItem')
        _(item.availability).must_equal(value)
        _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(item))['availability']).must_equal(value)
      end
    end
  end

  describe 'Availability members, every one' do
    it 'bhv_serializationobject_208 writes each member back as the exact wire string it was read from' do
      Petstore::Client::Models::Availability::VALUES.each do |value|
        member = Petstore::Client::ObjectSerializer.deserialize(JSON.generate(value), 'Availability')
        _(Petstore::Client::ObjectSerializer.serialize(member)).must_equal(JSON.generate(value))
      end
    end

    it 'bhv_serializationobject_076 round-trips every Availability member, hostile ones included' do
      Petstore::Client::Models::Availability::VALUES.each do |value|
        json = JSON.generate({ 'priority' => 1, 'availability' => value })
        item = Petstore::Client::ObjectSerializer.deserialize(json, 'StockItem')
        _(item.availability).must_equal(value)
        _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(item))['availability']).must_equal(value)
      end
    end
  end

  # A JSON body that repeats an object key resolves to the last value.
  describe 'duplicate object keys' do
    it 'bhv_serializationobject_006 resolves a repeated key to the last value' do
      json = '{"name":"first","name":"second","photoUrls":[]}'
      pet = Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      _(pet.name).must_equal('second')
    end
  end

  # D84: request JSON writes everything but quote, backslash and the controls
  # below U+0020 literally.
  describe 'request JSON string escaping' do
    it 'bhv_serializationobject_124 writes U+2028, U+2029 and DEL literally' do
      json = Petstore::Client::ObjectSerializer.serialize({ 'note' => "a\u2028b\u2029c\u007Fd" })
      _(json).must_equal("{\"note\":\"a\u2028b\u2029c\u007Fd\"}")
    end

    it 'bhv_serialization_061 writes slash, angle brackets, ampersand and non-ASCII literally' do
      json = Petstore::Client::ObjectSerializer.serialize({ 'note' => "/<>&\u00E9" })
      _(json).must_equal("{\"note\":\"/<>&\u00E9\"}")
    end

    it 'bhv_serializationobject_186 escapes the quote, the backslash and controls below U+0020' do
      json = Petstore::Client::ObjectSerializer.serialize({ 'note' => "\"\\\u0001" })
      _(json).must_equal('{"note":"\\"\\\\\\u0001"}')
    end
  end

  # OAS 3.1 prefixItems: a heterogeneous tuple (number, number, string) is
  # carried as a plain array and returns unchanged.
  describe 'prefixItems tuple property' do
    it 'bhv_serializationobject_012 decodes and writes Pet.location as a heterogeneous array' do
      json = '{"name":"Rex","photoUrls":["u"],"location":[1.5,2.5,"depot"]}'
      pet = Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      _(pet.location).must_equal([1.5, 2.5, 'depot'])
      _(JSON.parse(Petstore::Client::ObjectSerializer.serialize(pet))['location']).must_equal([1.5, 2.5, 'depot'])
    end
  end

  # ── format: time (Tod::TimeOfDay surface, 4.8) ──

  describe 'format: time round-tripping' do
    it 'stringify emits HH:MM:SS for a Tod::TimeOfDay' do
      t = Tod::TimeOfDay.new(13, 45, 30)
      _(Petstore::Client::ObjectSerializer.stringify(t)).must_equal('13:45:30')
    end

    it 'bhv_serializationobject_005 serialize emits HH:MM:SS for a Tod::TimeOfDay inside a hash' do
      t = Tod::TimeOfDay.new(9, 0, 0)
      json = Petstore::Client::ObjectSerializer.serialize({ 'opens_at' => t })
      _(JSON.parse(json)['opens_at']).must_equal('09:00:00')
    end

    it 'bhv_serialization_041 convert_to_type parses HH:MM:SS into a Tod::TimeOfDay' do
      result = Petstore::Client::ObjectSerializer.convert_to_type('13:45:30', 'Tod::TimeOfDay')
      _(result).must_be_kind_of(Tod::TimeOfDay)
      _(result.hour).must_equal(13)
      _(result.minute).must_equal(45)
      _(result.second).must_equal(30)
    end

    it 'bhv_serializationobject_164 accepts a fractional time and keeps the milliseconds' do
      result = Petstore::Client::ObjectSerializer.convert_to_type('10:30:00.123', 'Tod::TimeOfDay')
      _(Petstore::Client::ObjectSerializer.stringify(result)).must_equal('10:30:00.123')
    end

    it 'convert_to_type passes through an existing Tod::TimeOfDay' do
      original = Tod::TimeOfDay.new(7, 30, 15)
      result = Petstore::Client::ObjectSerializer.convert_to_type(original, 'Tod::TimeOfDay')
      _(result).must_equal(original)
    end

    it 'bhv_serializationobject_230 round-trip time yields equivalent value' do
      original = Tod::TimeOfDay.new(7, 30, 15)
      serialized = Petstore::Client::ObjectSerializer.stringify(original)
      parsed = Petstore::Client::ObjectSerializer.convert_to_type(serialized, 'Tod::TimeOfDay')
      _(parsed).must_equal(original)
    end

    it 'bhv_serialization_045 convert_to_type raises on malformed time string' do
      assert_raises(ArgumentError) do
        Petstore::Client::ObjectSerializer.convert_to_type('not-a-time', 'Tod::TimeOfDay')
      end
    end
  end

  # ── format: duration (ISO8601::Duration surface, 4.8) ──

  describe 'format: duration round-tripping' do
    # The native surface stays ISO8601::Duration, but the wire form is
    # protobuf-JSON duration ("3600s") -- the form protobuf-derived APIs
    # require. The ISO-8601 form ("PT1H") is rejected.
    it 'bhv_serializationobject_158 stringify emits protobuf-JSON seconds for an ISO8601::Duration' do
      d = ISO8601::Duration.new('PT1H30M')
      out = Petstore::Client::ObjectSerializer.stringify(d)
      _(out).must_equal('5400s')
    end

    it 'bhv_serializationobject_146 serialize emits the protobuf-JSON duration string inside a hash' do
      d = ISO8601::Duration.new('PT5M')
      json = Petstore::Client::ObjectSerializer.serialize({ 'ttl' => d })
      _(JSON.parse(json)['ttl']).must_equal('300s')
    end

    it 'serialize emits a duration with days as total seconds' do
      d = ISO8601::Duration.new('P2DT3H')
      json = Petstore::Client::ObjectSerializer.serialize({ 'window' => d })
      _(JSON.parse(json)['window']).must_equal('183600s')
    end

    it 'bhv_serializationobject_081 serialize emits protobuf-JSON seconds for a duration field on a model body' do
      # Request-body path: a dry-struct model carrying an ISO8601::Duration
      # field must serialize that field as protobuf-JSON ("3600s"), not the
      # native ISO-8601 form ("PT1H") which protobuf-derived APIs reject. `duration` is
      # intentionally absent from OPENAPI_FORMATS, so this exercises the
      # type-dispatch route through duration_to_protobuf_json rather than
      # apply_format_on_serialize.
      model = Petstore::Client::Models::TestFormatModel.new(ttl: ISO8601::Duration.new('PT1H'))
      json = Petstore::Client::ObjectSerializer.serialize(model)
      _(JSON.parse(json)['ttl']).must_equal('3600s')
    end

    it 'bhv_serialization_046 stringify emits fractional seconds trimmed to nanos precision' do
      d = ISO8601::Duration.new('PT3600.000000001S')
      _(Petstore::Client::ObjectSerializer.stringify(d)).must_equal('3600.000000001s')
    end

    it 'bhv_serializationobject_181 stringifies a zero Duration as 0s' do
      _(Petstore::Client::ObjectSerializer.stringify(ISO8601::Duration.new('PT0S'))).must_equal('0s')
    end

    it 'bhv_serializationobject_179 stringify trims trailing zeros to three fractional digits' do
      d = ISO8601::Duration.new('PT1.5S')
      _(Petstore::Client::ObjectSerializer.stringify(d)).must_equal('1.500s')
    end

    it 'bhv_serializationobject_105 stringify preserves the sign for a negative duration' do
      d = ISO8601::Duration.new('-PT1H')
      _(Petstore::Client::ObjectSerializer.stringify(d)).must_equal('-3600s')
    end

    it 'bhv_serializationobject_113 convert_to_type parses a negative protobuf-JSON duration' do
      # The sign must be reconstructed before the 'P' ("-PT3600S"); the gem
      # rejects the "PT-3600S" form, so a negative wire value would otherwise
      # fail to deserialize — breaking the round-trip the serialize side emits.
      result = Petstore::Client::ObjectSerializer.convert_to_type('-3600s', 'ISO8601::Duration')
      _(result).must_be_kind_of(ISO8601::Duration)
      _(result.to_seconds).must_equal(-3600)
    end

    it 'bhv_serializationobject_106 convert_to_type parses a negative fractional protobuf-JSON duration' do
      result = Petstore::Client::ObjectSerializer.convert_to_type('-1.5s', 'ISO8601::Duration')
      _(result.to_seconds).must_be_close_to(-1.5, 1e-9)
    end

    it 'bhv_serializationobject_112 convert_to_type parses protobuf-JSON seconds into an ISO8601::Duration' do
      result = Petstore::Client::ObjectSerializer.convert_to_type('5400s', 'ISO8601::Duration')
      _(result).must_be_kind_of(ISO8601::Duration)
      _(result.to_seconds).must_equal(5400)
    end

    it 'bhv_serializationobject_054 convert_to_type parses fractional protobuf-JSON seconds' do
      result = Petstore::Client::ObjectSerializer.convert_to_type('3600.000000001s', 'ISO8601::Duration')
      _(result.to_seconds).must_be_close_to(3600.000000001, 1e-9)
    end

    it 'bhv_serializationobject_212 convert_to_type parses sub-0.0001s durations without scientific notation' do
      # Regression: building the ISO-8601 literal from a Float interpolated
      # the seconds field as scientific notation for magnitudes below 0.0001
      # (0.00001 -> "1.0e-05"), which the iso8601 gem's decimal-only grammar
      # rejects. Microsecond/nanosecond wire durations must round-trip.
      # Inputs are in canonical protobuf-JSON form (0/3/6/9 fractional digits)
      # so re-serialization round-trips identically; the regression being
      # guarded is that none of these sub-0.0001s magnitudes serialize via
      # Float scientific notation.
      ['0.000010s', '0.000001s', '0.000000001s'].each do |wire|
        result = Petstore::Client::ObjectSerializer.convert_to_type(wire, 'ISO8601::Duration')
        _(result).must_be_kind_of(ISO8601::Duration)
        _(Petstore::Client::ObjectSerializer.stringify(result)).must_equal(wire)
      end
    end

    it 'convert_to_type passes through an existing ISO8601::Duration' do
      original = ISO8601::Duration.new('PT1H')
      result = Petstore::Client::ObjectSerializer.convert_to_type(original, 'ISO8601::Duration')
      _(result).must_equal(original)
    end

    it 'bhv_serializationobject_145 round-trip duration yields equivalent value' do
      original = ISO8601::Duration.new('P1DT2H3M4S')
      serialized = Petstore::Client::ObjectSerializer.stringify(original)
      _(serialized).must_equal('93784s')
      parsed = Petstore::Client::ObjectSerializer.convert_to_type(serialized, 'ISO8601::Duration')
      _(parsed.to_seconds).must_equal(original.to_seconds)
    end

    it 'bhv_serializationobject_184 rejects an ISO-8601 duration string' do
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.convert_to_type('PT1H', 'ISO8601::Duration')
      end
    end

    it 'convert_to_type raises on malformed duration string' do
      assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.convert_to_type('PT1H', 'ISO8601::Duration')
      end
    end
  end

  # ── Gap K — discriminator auto-injection on subtype serialize ──

  describe 'discriminator auto-injection' do
    it 'bhv_serializationobject_072 dry subtype serialize auto-emits the discriminator' do
      # DryFood defaults food_type to 'dry', so a caller that omits it
      # still gets the discriminator on the wire.
      dry = Petstore::Client::Models::DryFood.new(weight_kg: 2.5)
      data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(dry))
      _(data['foodType']).must_equal('dry')
      _(data['weightKg']).must_equal(2.5)
    end

    it 'bhv_serializationobject_178 wet subtype serialize auto-emits the discriminator' do
      wet = Petstore::Client::Models::WetFood.new(volume_ml: 350)
      data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(wet))
      _(data['foodType']).must_equal('wet')
    end

    it 'bhv_serializationobject_055 deserializing the oneOf parent routes to the subtype by discriminator' do
      # A discriminator-tagged payload deserialized against the parent
      # PetFood oneOf must come back as the concrete DryFood subtype, not
      # the raw data — PetFood.build resolves foodType="dry" to DryFood.
      json = '{"foodType":"dry","weightKg":2.5}'
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood')
      _(result).must_be_kind_of(Petstore::Client::Models::DryFood)
      _(result.weight_kg).must_equal(2.5)
    end
  end

  # ── Decimal-as-number — type:number (no format) on the wire ──
  #
  # Canonical cross-SDK scenario: Pet.weightKg is `type: number` with no
  # `format`, so it is a bare JSON number. A non-integer value such as 1.5
  # MUST serialize as an UNQUOTED JSON number (weightKg:1.5), never a
  # quoted string ("1.5"), and must survive a serialize -> deserialize
  # round-trip as a number. Some SDKs that model this as arbitrary-precision
  # Decimal regressed to emitting a quoted string; ruby is already correct
  # because the property is a plain Float, which JSON renders unquoted.
  describe 'Decimal-as-number — type:number serializes unquoted' do
    it 'bhv_serializationobject_171 emits weightKg as an unquoted JSON number, not a string' do
      pet = Petstore::Client::Models::Pet.new(name: 'Rex', photo_urls: ['u'], weight_kg: 1.5)
      json = Petstore::Client::ObjectSerializer.serialize(pet)
      # Unquoted number on the wire: weightKg:1.5 (not "weightKg":"1.5").
      _(json).must_match(/"weightKg":1\.5/)
      refute_match(/"weightKg":"1\.5"/, json)
      data = JSON.parse(json)
      _(data['weightKg']).must_equal(1.5)
      _(data['weightKg']).must_be_kind_of(Numeric)
    end

    it 'bhv_serializationobject_177 round-trips weightKg as a number through serialize -> deserialize' do
      pet = Petstore::Client::Models::Pet.new(name: 'Rex', photo_urls: ['u'], weight_kg: 1.5)
      json = Petstore::Client::ObjectSerializer.serialize(pet)
      result = Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      _(result.weight_kg).must_equal(1.5)
      _(result.weight_kg).must_be_kind_of(Numeric)
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
    it 'bhv_serializationobject_086 raises when required name is JSON null' do
      json = '{"name":null,"photoUrls":["u"]}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'Pet')
      }).must_raise(Petstore::Client::Errors::SerializationError)
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
    it 'bhv_serializationobject_085 raises when the foodType discriminator is absent' do
      json = '{"weightKg":5.0}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'PetFood')
      }).must_raise(Petstore::Client::Errors::SerializationError)
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
    it 'bhv_serializationobject_093 serializes an integer enum value as a JSON number, not a string' do
      json = Petstore::Client::ObjectSerializer.serialize(Petstore::Client::Models::Priority::NUMBER_2)
      # The bare JSON token must be the number 2, never the quoted "2".
      _(json).must_equal('2')
      _(JSON.parse(json)).must_equal(2)
      _(JSON.parse(json)).must_be_kind_of(Integer)
    end

    it 'bhv_serializationobject_092 deserializes a JSON number into the integer enum member' do
      result = Petstore::Client::ObjectSerializer.deserialize('2', 'Priority')
      _(result).must_equal(Petstore::Client::Models::Priority::NUMBER_2)
      _(result).must_be_kind_of(Integer)
    end

    it 'bhv_serializationobject_193 rejects an unknown integer enum value on deserialize' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('99', 'Priority')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_176 round-trips an integer-enum-typed model field as a number' do
      # StockItem.priority references the Priority int enum. The field must
      # survive the model round-trip as a JSON number on the wire and an
      # Integer member in memory.
      item = Petstore::Client::ObjectSerializer.deserialize('{"priority":3}', 'StockItem')
      _(item).must_be_kind_of(Petstore::Client::Models::StockItem)
      _(item.priority).must_equal(Petstore::Client::Models::Priority::NUMBER_3)
      data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(item))
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
    it 'bhv_serializationobject_109 deserializes each declared value to its member, casing preserved' do
      _(Petstore::Client::ObjectSerializer.deserialize('"Available"', 'Availability'))
        .must_equal(Petstore::Client::Models::Availability::AVAILABLE)
      _(Petstore::Client::ObjectSerializer.deserialize('"Sold"', 'Availability'))
        .must_equal(Petstore::Client::Models::Availability::SOLD)
      _(Petstore::Client::ObjectSerializer.deserialize('"on-hold"', 'Availability'))
        .must_equal(Petstore::Client::Models::Availability::ON_HOLD)
    end

    it 'bhv_serialization_002 serializes a member with its original wire casing' do
      json = Petstore::Client::ObjectSerializer.serialize(Petstore::Client::Models::Availability::AVAILABLE)
      _(JSON.parse(json)).must_equal('Available')
    end

    it 'bhv_serializationobject_194 writes a hyphenated enum member with its wire value exactly as declared' do
      _(Petstore::Client::ObjectSerializer.serialize(Petstore::Client::Models::Availability::ON_HOLD)).must_equal('"on-hold"')
    end

    it 'bhv_serializationobject_059 rejects an unknown string enum value on deserialize' do
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize('"available"', 'Availability')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end
  end

  # ── referenced enum on a model field rejects unknown wire value (canonical 10) ──
  #
  # StockItem.availability is a property typed as the referenced Availability
  # enum (not an inline enum like Pet.status). An unknown wire value for that
  # field must surface the SDK serialization error rather than coercing to a
  # default or sentinel — the referenced-enum-as-model-field path.
  describe 'referenced enum model field rejects unknown value' do
    it 'bhv_serializationobject_119 raises when a referenced-enum field carries an unknown value' do
      json = '{"priority":1,"availability":"liquidated"}'
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(json, 'StockItem')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end

    it 'bhv_serializationobject_196 an explicit value on the wire replaces the declared default' do
      item = Petstore::Client::ObjectSerializer.deserialize('{"priority":1,"availability":"Sold"}', 'StockItem')
      _(item.availability).must_equal(Petstore::Client::Models::Availability::SOLD)
      _(item.availability).wont_equal(Petstore::Client::Models::Availability::ON_HOLD)
    end

    it 'accepts a known referenced-enum field value' do
      item = Petstore::Client::ObjectSerializer.deserialize('{"priority":1,"availability":"Sold"}', 'StockItem')
      _(item.availability).must_equal(Petstore::Client::Models::Availability::SOLD)
    end
  end

  # ── a $ref property with a sibling default starts at that default (F866) ──
  #
  # StockItem.availability is a $ref to the Availability enum with
  # `default: on-hold` written next to the $ref. Both a built model and a
  # decoded one that leaves the property out must start at that member.
  describe 'referenced enum field with a sibling default' do
    it 'bhv_serializationobject_001 starts at the default when the model is built without it' do
      item = Petstore::Client::Models::StockItem.new(priority: 1)
      _(item.availability).must_equal(Petstore::Client::Models::Availability::ON_HOLD)
    end

    it 'decodes to the default when the wire leaves the field out' do
      item = Petstore::Client::ObjectSerializer.deserialize('{"priority":1}', 'StockItem')
      _(item.availability).must_equal(Petstore::Client::Models::Availability::ON_HOLD)
    end

    it 'keeps a value that is given' do
      item = Petstore::Client::ObjectSerializer.deserialize('{"priority":1,"availability":"Sold"}', 'StockItem')
      _(item.availability).must_equal(Petstore::Client::Models::Availability::SOLD)
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
    it 'bhv_serializationobject_071 coerces an integral JSON number into a Float' do
      loc = Petstore::Client::ObjectSerializer.deserialize('{"lat":5,"lng":-3}', 'PhotoMetadataLocation')
      _(loc).must_be_kind_of(Petstore::Client::Models::PhotoMetadataLocation)
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
    it 'bhv_serializationobject_108 deserializes array-of-array<int> to typed Integer leaves' do
      json = '{"priority":1,"matrix":[[1,2],[3,4,5]]}'
      item = Petstore::Client::ObjectSerializer.deserialize(json, 'StockItem')
      _(item.matrix).must_equal([[1, 2], [3, 4, 5]])
      _(item.matrix.first.first).must_be_kind_of(Integer)
      _(item.matrix.last.last).must_be_kind_of(Integer)
    end

    it 'round-trips the nested container back to the same shape' do
      json = '{"priority":1,"matrix":[[7,8],[9]]}'
      item = Petstore::Client::ObjectSerializer.deserialize(json, 'StockItem')
      data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(item))
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
    it 'bhv_serializationobject_221 base64-decodes every scan item into raw bytes on deserialize' do
      raw_a = "scan-a\x00".dup.force_encoding(Encoding::BINARY)
      raw_b = "scan-b\xFF".dup.force_encoding(Encoding::BINARY)
      json = %({"scans":["#{Base64.strict_encode64(raw_a)}","#{Base64.strict_encode64(raw_b)}"]})
      passport = Petstore::Client::ObjectSerializer.deserialize(json, 'PetPassport')
      _(passport).must_be_kind_of(Petstore::Client::Models::PetPassport)
      _(passport.scans).must_equal([raw_a, raw_b])
      _(passport.scans.first.encoding).must_equal(Encoding::BINARY)
    end

    it 'bhv_serializationobject_222 re-encodes every scan item to base64 on serialize' do
      raw_a = "\x01\x02".dup.force_encoding(Encoding::BINARY)
      raw_b = "\xFE\xFF".dup.force_encoding(Encoding::BINARY)
      model = Petstore::Client::Models::PetPassport.new(scans: [raw_a, raw_b])
      data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(model))
      _(data['scans']).must_equal([Base64.strict_encode64(raw_a), Base64.strict_encode64(raw_b)])
    end

    it 'bhv_serializationobject_079 round-trips the scans array preserving bytes exactly' do
      raw = (0..255).map(&:chr).join.dup.force_encoding(Encoding::BINARY)
      model = Petstore::Client::Models::PetPassport.new(scans: [raw])
      json = Petstore::Client::ObjectSerializer.serialize(model)
      restored = Petstore::Client::ObjectSerializer.deserialize(json, 'PetPassport')
      _(restored.scans).must_equal([raw])
    end
  end

  # OAS 3.1 contentEncoding: base64 is normalised to a byte field, so the
  # biometric chip is base64 on the wire and raw bytes in the model.
  describe 'contentEncoding base64 property' do
    it 'bhv_serializationobject_115 decodes and writes PetPassport.biometricChip as base64' do
      raw = "chip\x00\xFF".dup.force_encoding(Encoding::BINARY)
      json = %({"biometricChip":"#{Base64.strict_encode64(raw)}"})
      passport = Petstore::Client::ObjectSerializer.deserialize(json, 'PetPassport')
      _(passport.biometric_chip).must_equal(raw)
      written = JSON.parse(Petstore::Client::ObjectSerializer.serialize(passport))
      _(written['biometricChip']).must_equal(Base64.strict_encode64(raw))
    end
  end

  # ── canonical serde behaviours (cross-SDK) ──
  #
  # A bare JSON null body decodes to nil, and the 1000-deep JSON nesting cap
  # rejects an over-deep payload. NaN/Infinity rejection on encode and invalid
  # base64 on a format:byte field are covered above.
  describe 'canonical serde behaviours' do
    it 'bhv_serializationobject_067 deserializes a literal null body to nil' do
      _(Petstore::Client::ObjectSerializer.deserialize('null', 'Category')).must_be_nil
    end

    it 'bhv_serializationobject_051 decodes a payload within the depth cap but rejects one past it' do
      within = ('[' * 600) + (']' * 600)
      _(Petstore::Client::ObjectSerializer.deserialize(within, 'Object')).wont_be_nil

      beyond = ('[' * 1500) + (']' * 1500)
      err = assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize(beyond, 'Object')
      end
      _(err.message).must_equal('JSON nesting depth 1500 exceeds limit 1000')
    end

    it 'bhv_serializationobject_004 prefixes the parser detail of a malformed payload with the canonical deserialize wording' do
      err = assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('{"id":', 'Object')
      end
      _(err.message).must_match(/\AFailed to deserialize JSON: ./)
      _(err.cause).must_be_kind_of(JSON::ParserError)
    end

    it 'prefixes the detail of a failed serialize with the canonical serialize wording' do
      err = assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.serialize(Float::NAN)
      end
      _(err.message).must_match(/\AFailed to serialize object to JSON: ./)
    end
  end

  # An integer property holds a Ruby Integer: a fractional number is refused,
  # and an int64 above 2**53 (where a double stops being exact) survives intact.
  describe 'integer properties' do
    it 'bhv_serializationobject_120 refuses a fractional number for an int32 property' do
      err = assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('{"quantity":1.5}', 'Petstore::Client::Models::Order')
      end
      _(err.message).must_equal('Failed to deserialize JSON: Expected Integer, got Float: 1.5')
    end

    it 'bhv_serialization_029 refuses a quoted number for an int64 property' do
      err = assert_raises(Petstore::Client::Errors::SerializationError) do
        Petstore::Client::ObjectSerializer.deserialize('{"id":"7"}', 'Petstore::Client::Models::Order')
      end
      _(err.message).must_equal('Failed to deserialize JSON: Expected Integer, got String: "7"')
    end

    it 'bhv_serializationobject_191 keeps an int64 above 2**53 exact through decode and encode' do
      order = Petstore::Client::ObjectSerializer.deserialize('{"id":9007199254740993}', 'Petstore::Client::Models::Order')

      _(order.id).must_equal(9_007_199_254_740_993)
      _(Petstore::Client::ObjectSerializer.serialize(order)).must_include('"id":9007199254740993')
    end
  end
end

describe Petstore::Client::ObjectSerializer, 'wire contract for scalars, containers and models' do
  parallelize_me!

  it 'bhv_serialization_001 the text form of an enum member is exactly its declared wire value' do
    _(Petstore::Client::Models::Availability::ON_HOLD.to_s).must_equal('on-hold')
  end

  it 'bhv_serialization_009 a value that cannot be serialized raises the serialization error' do
    cycle = []
    cycle << cycle

    _(proc {
      Petstore::Client::ObjectSerializer.serialize(cycle)
    }).must_raise(Petstore::Client::Errors::SerializationError)
  end

  it 'bhv_serialization_012 keys of maps are written in ascending order so equal data writes identical output' do
    first = Petstore::Client::ObjectSerializer.serialize({ 'b' => 1, 'a' => 2, 'c' => 3 })
    _(first).must_equal('{"a":2,"b":1,"c":3}')
    _(first).must_equal(Petstore::Client::ObjectSerializer.serialize({ 'c' => 3, 'a' => 2, 'b' => 1 }))
  end

  it 'bhv_serialization_011 model keys are written in the declaration order, never sorted' do
    json = Petstore::Client::ObjectSerializer.serialize(
      Petstore::Client::Models::TestFormatModel.new(note: 'n', blob: 'b')
    )

    _(json.index('"blob"')).must_be :<, json.index('"note"')
  end

  it 'bhv_serialization_013 a set or array header value is written as its items' do
    _(Petstore::Client::ObjectSerializer.to_header_value(%w[a b])).must_equal('a,b')
  end

  it 'bhv_serialization_019 an unset optional property is left out of the serialized output' do
    json = Petstore::Client::ObjectSerializer.serialize(Petstore::Client::Models::TestFormatModel.new(note: 'x'))

    _(JSON.parse(json).key?('blob')).must_equal(false)
  end

  it 'bhv_serialization_021 models with equal content compare equal and hash alike' do
    first = Petstore::Client::Models::TestFormatModel.new(note: 'x')
    same = Petstore::Client::Models::TestFormatModel.new(note: 'x')
    other = Petstore::Client::Models::TestFormatModel.new(note: 'y')

    _(first).must_equal(same)
    _(first.hash).must_equal(same.hash)
    _(first).wont_equal(other)
  end

  it 'bhv_serialization_022 a top-level JSON scalar decodes to a value of its own type' do
    _(Petstore::Client::ObjectSerializer.deserialize('42', 'Integer')).must_equal(42)
    _(Petstore::Client::ObjectSerializer.deserialize('"x"', 'String')).must_equal('x')
    _(Petstore::Client::ObjectSerializer.deserialize('true', 'Boolean')).must_equal(true)
  end

  it 'bhv_serialization_023 a payload with content after the document is refused with the serialization error' do
    _(proc {
      Petstore::Client::ObjectSerializer.deserialize('{"a":1} trailing', 'Object')
    }).must_raise(Petstore::Client::Errors::SerializationError)
  end

  it 'bhv_serialization_024 brackets inside JSON strings never count toward the depth limit' do
    nested = '[' * 2000
    json = %({"a":"#{nested}"})

    _(Petstore::Client::ObjectSerializer.deserialize(json, 'Object')).must_equal(a: nested)
  end

  it 'a payload nested past the depth limit is refused with the serialization error' do
    deep = ('[' * 1001) + (']' * 1001)

    _(proc {
      Petstore::Client::ObjectSerializer.deserialize(deep, 'Object')
    }).must_raise(Petstore::Client::Errors::SerializationError)
  end

  it 'bhv_serialization_025 typed leaves nested in arrays and maps decode to their own typed values' do
    items = Petstore::Client::ObjectSerializer.deserialize('[{"note":"x"}]', 'Array<TestFormatModel>')
    by_key = Petstore::Client::ObjectSerializer.deserialize('{"a":"on-hold"}', 'Hash<String, Availability>')

    _(items.first).must_be_kind_of(Petstore::Client::Models::TestFormatModel)
    _(items.first.note).must_equal('x')
    _(by_key).must_equal('a' => 'on-hold')
  end

  it 'bhv_serialization_026 a JSON object decodes to a map of its keys and typed values' do
    _(Petstore::Client::ObjectSerializer.deserialize('{"b":2,"a":1}', 'Hash<String, Integer>')).must_equal('a' => 1, 'b' => 2)
    _(Petstore::Client::ObjectSerializer.deserialize('{}', 'Hash<String, Integer>')).must_equal({})
  end

  it 'bhv_serialization_027 an empty JSON array decodes to an empty collection, distinct from an absent value' do
    _(Petstore::Client::ObjectSerializer.deserialize('[]', 'Array<Integer>')).must_equal([])
    _(Petstore::Client::ObjectSerializer.deserialize('null', 'Array<Integer>')).must_be_nil
  end

  it 'bhv_serialization_030 a date-time field refuses a number, a boolean or an array' do
    %w[5 true [1]].each do |payload|
      _(proc {
        Petstore::Client::ObjectSerializer.deserialize(payload, 'Time')
      }).must_raise(Petstore::Client::Errors::SerializationError)
    end
  end

  it 'bhv_serialization_031 a whole number at the largest 32-bit and 64-bit values decodes exactly' do
    _(Petstore::Client::ObjectSerializer.deserialize('2147483647', 'Integer')).must_equal(2_147_483_647)
    _(Petstore::Client::ObjectSerializer.deserialize('9223372036854775807', 'Integer')).must_equal(9_223_372_036_854_775_807)
  end

  it 'bhv_serialization_032 an integer field refuses a number written with a fraction or an exponent' do
    _(proc { Petstore::Client::ObjectSerializer.deserialize('1.0', 'Integer') }).must_raise(Petstore::Client::Errors::SerializationError)
    _(proc { Petstore::Client::ObjectSerializer.deserialize('1e2', 'Integer') }).must_raise(Petstore::Client::Errors::SerializationError)
  end

  it 'bhv_serialization_033 each leaf of an integer map or nested integer array must be a plain integer' do
    _(proc {
      Petstore::Client::ObjectSerializer.deserialize('{"a":1,"b":1.5}', 'Hash<String, Integer>')
    }).must_raise(Petstore::Client::Errors::SerializationError)
    _(proc {
      Petstore::Client::ObjectSerializer.deserialize('[[1,2],[3,"x"]]', 'Array<Array<Integer>>')
    }).must_raise(Petstore::Client::Errors::SerializationError)
  end

  it 'bhv_serialization_036 a whole-number floating-point value is written with a decimal point' do
    _(Petstore::Client::ObjectSerializer.serialize(1.0)).must_equal('1.0')
  end

  it 'bhv_serialization_039 date-times in different RFC 3339 spellings read to the same instant' do
    zulu = Petstore::Client::ObjectSerializer.deserialize('"2024-01-02T03:04:05.123456Z"', 'Time')
    offset = Petstore::Client::ObjectSerializer.deserialize('"2024-01-02T04:04:05.123456+01:00"', 'Time')

    _(offset).must_equal(zulu)
  end

  it 'bhv_serialization_043 a time of day is written as hours, minutes and seconds' do
    _(Petstore::Client::ObjectSerializer.serialize(Tod::TimeOfDay.new(9, 5, 3))).must_equal('"09:05:03"')
  end

  it 'bhv_serialization_049 a URI-format value must be absolute' do
    _(Petstore::Client::ObjectSerializer.apply_format_on_deserialize('https://example.com/a', 'uri')).must_equal('https://example.com/a')
    _(proc {
      Petstore::Client::ObjectSerializer.apply_format_on_deserialize('relative/path', 'uri')
    }).must_raise(Petstore::Client::Errors::SerializationError)
  end

  it 'bhv_serialization_052 undeclared keys are kept on every element of a list read from the wire' do
    items = Petstore::Client::ObjectSerializer.deserialize('[{"customField":"a"}]', 'Array<Metadata>')

    _(items.first.additional_properties).must_equal('customField' => 'a')
  end

  it 'bhv_serialization_053 undeclared keys held by a model are written back when it is serialized' do
    metadata = Petstore::Client::Models::Metadata.new(additional_properties: { 'customField' => 'a' })
    data = JSON.parse(Petstore::Client::ObjectSerializer.serialize(metadata))

    _(data['customField']).must_equal('a')
  end

  it 'bhv_serialization_054 a free-form value keeps its JSON type' do
    _(Petstore::Client::ObjectSerializer.deserialize('{"a":"x","b":null}', 'Object')).must_equal(a: 'x', b: nil)
  end

  it 'bhv_serialization_042 a date-only value is written as the same calendar date it was read as' do
    date = Petstore::Client::ObjectSerializer.deserialize('"2024-02-29"', 'Date')

    _(Petstore::Client::ObjectSerializer.serialize(date)).must_equal('"2024-02-29"')
  end

  it 'bhv_serialization_064 a collection of unique values is written as a JSON array of its members' do
    _(Petstore::Client::ObjectSerializer.serialize(Set.new(%w[b a]))).must_equal('["a","b"]')
  end
end
