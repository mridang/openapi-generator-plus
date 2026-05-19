# frozen_string_literal: true

# rubocop:disable Metrics/BlockLength, Lint/MissingCopEnableDirective

require 'test_helper'

describe PetstoreClient::ValueSerializer do
  describe 'path location' do
    it 'null returns empty string' do
      _(PetstoreClient::ValueSerializer.serialize(nil, :path, 'string')).must_equal('')
    end

    it 'string returns URL-encoded value' do
      _(PetstoreClient::ValueSerializer.serialize('hello', :path, 'string')).must_equal('hello')
    end

    it 'string with spaces is URL-encoded' do
      _(PetstoreClient::ValueSerializer.serialize('hello world', :path, 'string')).must_equal('hello%20world')
    end

    it 'string with slash is URL-encoded' do
      _(PetstoreClient::ValueSerializer.serialize('a/b', :path, 'string')).must_equal('a%2Fb')
    end

    it 'integer returns string' do
      _(PetstoreClient::ValueSerializer.serialize(42, :path, 'integer')).must_equal('42')
    end

    it 'boolean true returns true' do
      _(PetstoreClient::ValueSerializer.serialize(true, :path, 'boolean')).must_equal('true')
    end

    it 'boolean false returns false' do
      _(PetstoreClient::ValueSerializer.serialize(false, :path, 'boolean')).must_equal('false')
    end
  end

  describe 'query location' do
    it 'null returns nil' do
      _(PetstoreClient::ValueSerializer.serialize(nil, :query, 'string')).must_be_nil
    end

    it 'string returns as-is' do
      _(PetstoreClient::ValueSerializer.serialize('hello', :query, 'string')).must_equal('hello')
    end

    it 'integer returns string' do
      _(PetstoreClient::ValueSerializer.serialize(42, :query, 'integer')).must_equal('42')
    end

    it 'boolean true returns true' do
      _(PetstoreClient::ValueSerializer.serialize(true, :query, 'boolean')).must_equal('true')
    end

    it 'boolean false returns false' do
      _(PetstoreClient::ValueSerializer.serialize(false, :query, 'boolean')).must_equal('false')
    end

    it 'array joins with comma by default' do
      _(PetstoreClient::ValueSerializer.serialize(%w[a b c], :query, 'array')).must_equal('a,b,c')
    end

    it 'array joins with comma for csv' do
      result = PetstoreClient::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :csv)
      _(result).must_equal('a,b,c')
    end

    it 'array joins with space for ssv' do
      result = PetstoreClient::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :ssv)
      _(result).must_equal('a b c')
    end

    it 'array joins with tab for tsv' do
      result = PetstoreClient::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :tsv)
      _(result).must_equal("a\tb\tc")
    end

    it 'array joins with pipe for pipes' do
      result = PetstoreClient::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :pipes)
      _(result).must_equal('a|b|c')
    end

    it 'array returns list for multi' do
      result = PetstoreClient::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :multi)
      _(result).must_equal(%w[a b c])
    end

    it 'empty array returns empty string for csv' do
      _(PetstoreClient::ValueSerializer.serialize([], :query, 'array')).must_equal('')
    end

    it 'empty array returns empty list for multi' do
      _(PetstoreClient::ValueSerializer.serialize([], :query, 'array', collection_format: :multi)).must_equal([])
    end

    it 'single-element array returns single value' do
      _(PetstoreClient::ValueSerializer.serialize(['a'], :query, 'array')).must_equal('a')
    end

    it 'array of integers stringifies elements' do
      _(PetstoreClient::ValueSerializer.serialize([1, 2, 3], :query, 'array')).must_equal('1,2,3')
    end

    it 'array of booleans stringifies elements' do
      _(PetstoreClient::ValueSerializer.serialize([true, false], :query, 'array')).must_equal('true,false')
    end
  end

  describe 'header location' do
    it 'null returns empty string' do
      _(PetstoreClient::ValueSerializer.serialize(nil, :header, 'string')).must_equal('')
    end

    it 'string returns as-is' do
      _(PetstoreClient::ValueSerializer.serialize('hello', :header, 'string')).must_equal('hello')
    end

    it 'integer returns string' do
      _(PetstoreClient::ValueSerializer.serialize(42, :header, 'integer')).must_equal('42')
    end

    it 'boolean true returns true' do
      _(PetstoreClient::ValueSerializer.serialize(true, :header, 'boolean')).must_equal('true')
    end

    it 'array joins with comma' do
      _(PetstoreClient::ValueSerializer.serialize(%w[a b c], :header, 'array')).must_equal('a,b,c')
    end

    it 'empty array joins to empty string' do
      _(PetstoreClient::ValueSerializer.serialize([], :header, 'array')).must_equal('')
    end

    it 'array of integers stringifies and joins' do
      _(PetstoreClient::ValueSerializer.serialize([1, 2, 3], :header, 'array')).must_equal('1,2,3')
    end
  end

  describe 'cookie location' do
    it 'string returns as-is' do
      _(PetstoreClient::ValueSerializer.serialize('hello', :cookie, 'string')).must_equal('hello')
    end

    it 'null returns empty string' do
      _(PetstoreClient::ValueSerializer.serialize(nil, :cookie, 'string')).must_equal('')
    end
  end

  describe 'form location' do
    it 'null returns empty string' do
      _(PetstoreClient::ValueSerializer.serialize(nil, :form, 'string')).must_equal('')
    end

    it 'string returns as-is' do
      _(PetstoreClient::ValueSerializer.serialize('hello', :form, 'string')).must_equal('hello')
    end

    it 'integer returns string' do
      _(PetstoreClient::ValueSerializer.serialize(42, :form, 'integer')).must_equal('42')
    end

    it 'boolean true returns true' do
      _(PetstoreClient::ValueSerializer.serialize(true, :form, 'boolean')).must_equal('true')
    end

    it 'boolean false returns false' do
      _(PetstoreClient::ValueSerializer.serialize(false, :form, 'boolean')).must_equal('false')
    end
  end

  describe 'serialize_styled' do
    describe 'matrix style' do
      it 'scalar returns semicolon-prefixed name=value' do
        result = PetstoreClient::ValueSerializer.serialize_styled('color', 'blue', :path, 'string', nil, 'matrix', true)
        _(result).must_equal(';color=blue')
      end

      it 'array with explode false joins with comma' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', %w[blue black], :path, 'array', nil, 'matrix', false
        )
        _(result).must_equal(';color=blue,black')
      end

      it 'array with explode true repeats name' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', %w[blue black], :path, 'array', nil, 'matrix', true
        )
        _(result).must_equal(';color=blue;color=black')
      end

      it 'null returns empty string' do
        result = PetstoreClient::ValueSerializer.serialize_styled('color', nil, :path, 'string', nil, 'matrix', true)
        _(result).must_equal('')
      end
    end

    describe 'label style' do
      it 'scalar returns dot-prefixed value' do
        result = PetstoreClient::ValueSerializer.serialize_styled('color', 'blue', :path, 'string', nil, 'label', true)
        _(result).must_equal('.blue')
      end

      it 'array with explode false joins with comma after dot' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', %w[blue black], :path, 'array', nil, 'label', false
        )
        _(result).must_equal('.blue,black')
      end

      it 'array with explode true joins with dot separator' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', %w[blue black], :path, 'array', nil, 'label', true
        )
        _(result).must_equal('.blue.black')
      end

      it 'null returns empty string' do
        result = PetstoreClient::ValueSerializer.serialize_styled('color', nil, :path, 'string', nil, 'label', true)
        _(result).must_equal('')
      end
    end

    describe 'spaceDelimited style' do
      it 'array joins with space' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'spaceDelimited', false
        )
        _(result).must_equal('blue black')
      end

      it 'scalar returns stringified value' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', 'blue', :query, 'string', nil, 'spaceDelimited', false
        )
        _(result).must_equal('blue')
      end
    end

    describe 'pipeDelimited style' do
      it 'array joins with pipe' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'pipeDelimited', false
        )
        _(result).must_equal('blue|black')
      end

      it 'scalar returns stringified value' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', 'blue', :query, 'string', nil, 'pipeDelimited', false
        )
        _(result).must_equal('blue')
      end
    end

    describe 'form style with explode' do
      it 'array with explode false joins with comma' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'form', false
        )
        _(result).must_equal('blue,black')
      end

      it 'array with explode true returns list' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'form', true
        )
        _(result).must_equal(%w[blue black])
      end

      it 'scalar with explode true returns string not list' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', 'blue', :query, 'string', nil, 'form', true
        )
        _(result).must_equal('blue')
      end

      it 'single-element array with explode true returns list' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'color', ['blue'], :query, 'array', nil, 'form', true
        )
        _(result).must_equal(['blue'])
      end

      it 'null returns nil for query location' do
        result = PetstoreClient::ValueSerializer.serialize_styled('color', nil, :query, 'string', nil, 'form', true)
        _(result).must_be_nil
      end
    end

    describe 'simple style backward compatibility' do
      it 'scalar returns stringified value' do
        result = PetstoreClient::ValueSerializer.serialize_styled('id', '5', :path, 'string', nil, 'simple', false)
        _(result).must_equal('5')
      end

      it 'array joins with comma' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'id', %w[3 4 5], :path, 'array', nil, 'simple', false
        )
        _(result).must_equal('3,4,5')
      end

      it 'null returns empty string' do
        result = PetstoreClient::ValueSerializer.serialize_styled('id', nil, :path, 'string', nil, 'simple', true)
        _(result).must_equal('')
      end

      it 'scalar URL-encodes for path' do
        result = PetstoreClient::ValueSerializer.serialize_styled(
          'id', 'hello world', :path, 'string', nil, 'simple', false
        )
        _(result).must_equal('hello%20world')
      end
    end

    describe 'null style falls back to location default' do
      it 'path with null style behaves like simple' do
        result = PetstoreClient::ValueSerializer.serialize_styled('id', '5', :path, 'string', nil, nil, false)
        _(result).must_equal('5')
      end

      it 'empty style falls back to location default' do
        result = PetstoreClient::ValueSerializer.serialize_styled('id', '5', :path, 'string', nil, '', false)
        _(result).must_equal('5')
      end
    end
  end

  describe 'serialize_deep_object' do
    it 'basic map returns bracketed keys' do
      result = PetstoreClient::ValueSerializer.serialize_deep_object('filter', { 'color' => 'blue', 'size' => 'large' })
      _(result['filter[color]']).must_equal('blue')
      _(result['filter[size]']).must_equal('large')
    end

    it 'null returns empty hash' do
      result = PetstoreClient::ValueSerializer.serialize_deep_object('filter', nil)
      _(result).must_equal({})
    end
  end

  # Cross-language parity tests for path-segment percent-encoding.
  # Every SDK must produce identical encoded strings for these inputs.
  describe 'path encoding parity' do
    it 'ASCII-safe pass-through' do
      _(PetstoreClient::ValueSerializer.serialize('abc123', :path, 'string')).must_equal('abc123')
    end

    it 'space encoded as %20' do
      _(PetstoreClient::ValueSerializer.serialize('a b', :path, 'string')).must_equal('a%20b')
    end

    it 'slash encoded' do
      _(PetstoreClient::ValueSerializer.serialize('a/b', :path, 'string')).must_equal('a%2Fb')
    end

    it 'question mark encoded' do
      _(PetstoreClient::ValueSerializer.serialize('a?b', :path, 'string')).must_equal('a%3Fb')
    end

    it 'hash encoded' do
      _(PetstoreClient::ValueSerializer.serialize('a#b', :path, 'string')).must_equal('a%23b')
    end

    it 'comma preserved (sub-delimiter)' do
      _(PetstoreClient::ValueSerializer.serialize('a,b', :path, 'string')).must_equal('a,b')
    end

    it 'colon preserved (sub-delimiter)' do
      _(PetstoreClient::ValueSerializer.serialize('a:b', :path, 'string')).must_equal('a:b')
    end

    it 'plus preserved (sub-delimiter)' do
      _(PetstoreClient::ValueSerializer.serialize('a+b', :path, 'string')).must_equal('a+b')
    end

    it 'unicode encoded as UTF-8 percent' do
      _(PetstoreClient::ValueSerializer.serialize('日本', :path, 'string')).must_equal('%E6%97%A5%E6%9C%AC')
    end

    it 'empty string preserved' do
      _(PetstoreClient::ValueSerializer.serialize('', :path, 'string')).must_equal('')
    end

    it 'null returns empty string in path location' do
      _(PetstoreClient::ValueSerializer.serialize(nil, :path, 'string')).must_equal('')
    end

    it 'simple style encodes value' do
      result = PetstoreClient::ValueSerializer.serialize_styled('color', 'a b', :path, 'string', nil, 'simple', false)
      _(result).must_equal('a%20b')
    end

    it 'simple style array encodes each item' do
      result = PetstoreClient::ValueSerializer.serialize_styled('color', ['a b', 'c?d'], :path, 'array', nil, 'simple', false)
      _(result).must_equal('a%20b,c%3Fd')
    end

    it 'matrix style encodes value' do
      result = PetstoreClient::ValueSerializer.serialize_styled('color', 'a b', :path, 'string', nil, 'matrix', false)
      _(result).must_equal(';color=a%20b')
    end

    it 'label style encodes value' do
      result = PetstoreClient::ValueSerializer.serialize_styled('color', 'a b', :path, 'string', nil, 'label', false)
      _(result).must_equal('.a%20b')
    end

    it 'query location is not path-encoded' do
      result = PetstoreClient::ValueSerializer.serialize_styled('color', 'a b', :query, 'string', nil, 'form', false)
      _(result).must_equal('a b')
    end

    it 'empty string path param raises ArgumentError' do
      # Gap W — empty-string path values silently produce malformed
      # URLs like `/pet//details`; reject at serialization time so
      # callers see the real error rather than a downstream 404.
      _(-> {
        PetstoreClient::ValueSerializer.serialize_styled('id', '', :path, 'string', nil, 'simple', false)
      }).must_raise ArgumentError
    end
  end
end
