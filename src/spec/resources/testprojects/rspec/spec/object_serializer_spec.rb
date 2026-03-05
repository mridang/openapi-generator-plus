require_relative 'spec_helper'

RSpec.describe OpigenClient::ObjectSerializer do
  describe '.to_path_value' do
    it 'returns empty string for nil' do
      expect(described_class.to_path_value(nil)).to eq('')
    end

    it 'returns the string for a string value' do
      expect(described_class.to_path_value('hello')).to eq('hello')
    end

    it 'converts integer to string' do
      expect(described_class.to_path_value(42)).to eq('42')
    end

    it 'converts true to "true"' do
      expect(described_class.to_path_value(true)).to eq('true')
    end

    it 'converts false to "false"' do
      expect(described_class.to_path_value(false)).to eq('false')
    end
  end

  describe '.to_query_value' do
    it 'returns nil for nil' do
      expect(described_class.to_query_value(nil)).to be_nil
    end

    it 'returns the string for a string value' do
      expect(described_class.to_query_value('hello')).to eq('hello')
    end

    it 'converts integer to string' do
      expect(described_class.to_query_value(42)).to eq('42')
    end

    it 'converts true to "true"' do
      expect(described_class.to_query_value(true)).to eq('true')
    end

    it 'joins array with comma by default' do
      expect(described_class.to_query_value(%w[a b c])).to eq('a,b,c')
    end

    it 'joins array with comma for csv' do
      expect(described_class.to_query_value(%w[a b c], :csv)).to eq('a,b,c')
    end

    it 'joins array with space for ssv' do
      expect(described_class.to_query_value(%w[a b c], :ssv)).to eq('a b c')
    end

    it 'joins array with tab for tsv' do
      expect(described_class.to_query_value(%w[a b c], :tsv)).to eq("a\tb\tc")
    end

    it 'joins array with pipe for pipes' do
      expect(described_class.to_query_value(%w[a b c], :pipes)).to eq('a|b|c')
    end

    it 'returns array as-is for multi' do
      expect(described_class.to_query_value(%w[a b c], :multi)).to eq(%w[a b c])
    end
  end

  describe '.to_header_value' do
    it 'returns empty string for nil' do
      expect(described_class.to_header_value(nil)).to eq('')
    end

    it 'returns the string for a string value' do
      expect(described_class.to_header_value('hello')).to eq('hello')
    end

    it 'converts integer to string' do
      expect(described_class.to_header_value(42)).to eq('42')
    end

    it 'joins array with comma' do
      expect(described_class.to_header_value(%w[a b c])).to eq('a,b,c')
    end
  end

  describe '.to_form_value' do
    it 'returns empty string for nil' do
      expect(described_class.to_form_value(nil)).to eq('')
    end

    it 'returns the string for a string value' do
      expect(described_class.to_form_value('hello')).to eq('hello')
    end

    it 'converts integer to string' do
      expect(described_class.to_form_value(42)).to eq('42')
    end

    it 'converts true to "true"' do
      expect(described_class.to_form_value(true)).to eq('true')
    end

    it 'converts false to "false"' do
      expect(described_class.to_form_value(false)).to eq('false')
    end
  end

  describe '.serialize' do
    it 'serializes a model to valid JSON' do
      category = OpigenClient::Models::Category.new(id: 1, name: 'Dogs')
      json = described_class.serialize(category)
      data = JSON.parse(json)
      expect(data['id']).to eq(1)
      expect(data['name']).to eq('Dogs')
    end

    it 'handles nil' do
      json = described_class.serialize(nil)
      expect(json).to eq('null')
    end
  end

  describe '.deserialize' do
    it 'deserializes JSON to typed model' do
      json_str = '{"id":1,"name":"Dogs"}'
      category = described_class.deserialize(json_str, 'Category')
      expect(category).to be_a(OpigenClient::Models::Category)
      expect(category.id).to eq(1)
      expect(category.name).to eq('Dogs')
    end

    it 'returns nil for empty input' do
      expect(described_class.deserialize('', 'Category')).to be_nil
    end

    it 'returns nil for nil input' do
      expect(described_class.deserialize(nil, 'Category')).to be_nil
    end
  end
end
