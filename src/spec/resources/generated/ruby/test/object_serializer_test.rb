# frozen_string_literal: true

# rubocop:disable Metrics/BlockLength, Lint/MissingCopEnableDirective

require 'test_helper'

describe PetstoreClient::ObjectSerializer do
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
end
