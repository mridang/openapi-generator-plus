# frozen_string_literal: true

require 'spec_helper'

describe PetstoreClient::ObjectSerializer do
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
