# frozen_string_literal: true

require 'test_helper'

describe Petstore::Client::ValueSerializer do
  parallelize_me!

  describe 'path location' do
    it 'bhv_serializationparam_062 null returns empty string' do
      _(Petstore::Client::ValueSerializer.serialize(nil, :path, 'string')).must_equal('')
    end

    it 'bhv_serializationparam_100 string returns URL-encoded value' do
      _(Petstore::Client::ValueSerializer.serialize('hello', :path, 'string')).must_equal('hello')
    end

    it 'bhv_serializationparam_102 string with spaces is URL-encoded' do
      _(Petstore::Client::ValueSerializer.serialize('hello world', :path, 'string')).must_equal('hello%20world')
    end

    it 'bhv_serializationparam_101 string with slash is URL-encoded' do
      _(Petstore::Client::ValueSerializer.serialize('a/b', :path, 'string')).must_equal('a%2Fb')
    end

    it 'bhv_serializationparam_044 integer returns string' do
      _(Petstore::Client::ValueSerializer.serialize(42, :path, 'integer')).must_equal('42')
    end

    it 'bhv_serializationparam_021 boolean true returns true' do
      _(Petstore::Client::ValueSerializer.serialize(true, :path, 'boolean')).must_equal('true')
    end

    it 'bhv_serializationparam_018 boolean false returns false' do
      _(Petstore::Client::ValueSerializer.serialize(false, :path, 'boolean')).must_equal('false')
    end
  end

  describe 'query location' do
    it 'bhv_serializationparam_067 null returns nil' do
      _(Petstore::Client::ValueSerializer.serialize(nil, :query, 'string')).must_be_nil
    end

    it 'bhv_serializationparam_096 string returns as-is' do
      _(Petstore::Client::ValueSerializer.serialize('hello', :query, 'string')).must_equal('hello')
    end

    it 'bhv_serializationparam_045 integer returns string' do
      _(Petstore::Client::ValueSerializer.serialize(42, :query, 'integer')).must_equal('42')
    end

    it 'bhv_serializationparam_022 boolean true returns true' do
      _(Petstore::Client::ValueSerializer.serialize(true, :query, 'boolean')).must_equal('true')
    end

    it 'bhv_serializationparam_019 boolean false returns false' do
      _(Petstore::Client::ValueSerializer.serialize(false, :query, 'boolean')).must_equal('false')
    end

    it 'bhv_serializationparam_006 array joins with comma by default' do
      _(Petstore::Client::ValueSerializer.serialize(%w[a b c], :query, 'array')).must_equal('a,b,c')
    end

    it 'bhv_serializationparam_007 array joins with comma for csv' do
      result = Petstore::Client::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :csv)
      _(result).must_equal('a,b,c')
    end

    it 'bhv_serializationparam_009 array joins with space for ssv' do
      result = Petstore::Client::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :ssv)
      _(result).must_equal('a b c')
    end

    it 'bhv_serializationparam_010 array joins with tab for tsv' do
      result = Petstore::Client::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :tsv)
      _(result).must_equal("a\tb\tc")
    end

    it 'bhv_serializationparam_008 array joins with pipe for pipes' do
      result = Petstore::Client::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :pipes)
      _(result).must_equal('a|b|c')
    end

    it 'bhv_serializationparam_015 array returns list for multi' do
      result = Petstore::Client::ValueSerializer.serialize(%w[a b c], :query, 'array', collection_format: :multi)
      _(result).must_equal(%w[a b c])
    end

    it 'bhv_serializationparam_032 empty array returns empty string for csv' do
      _(Petstore::Client::ValueSerializer.serialize([], :query, 'array')).must_equal('')
    end

    it 'bhv_serializationparam_031 empty array returns empty list for multi' do
      _(Petstore::Client::ValueSerializer.serialize([], :query, 'array', collection_format: :multi)).must_equal([])
    end

    it 'bhv_serializationparam_090 single-element array returns single value' do
      _(Petstore::Client::ValueSerializer.serialize(['a'], :query, 'array')).must_equal('a')
    end

    it 'bhv_serializationparam_014 array of integers stringifies elements' do
      _(Petstore::Client::ValueSerializer.serialize([1, 2, 3], :query, 'array')).must_equal('1,2,3')
    end

    it 'bhv_serializationparam_012 array of booleans stringifies elements' do
      _(Petstore::Client::ValueSerializer.serialize([true, false], :query, 'array')).must_equal('true,false')
    end

    # A null element keeps its slot: it is neither skipped nor spelled out as
    # the language's null text.
    {
      csv: '1,,3',
      ssv: '1  3',
      tsv: "1\t\t3",
      pipes: '1||3'
    }.each do |format, expected|
      it "bhv_serializationparam_001 array keeps an empty slot for a null element with #{format}" do
        result = Petstore::Client::ValueSerializer.serialize(['1', nil, '3'], :query, 'array', collection_format: format)
        _(result).must_equal(expected)
      end
    end

    it 'bhv_serializationparam_011 array keeps an empty slot for a null element with multi' do
      result = Petstore::Client::ValueSerializer.serialize(['1', nil, '3'], :query, 'array', collection_format: :multi)
      _(result).must_equal(['1', '', '3'])
    end
  end

  describe 'header location' do
    it 'bhv_serializationparam_063 null returns empty string' do
      _(Petstore::Client::ValueSerializer.serialize(nil, :header, 'string')).must_equal('')
    end

    it 'bhv_serializationparam_097 string returns as-is' do
      _(Petstore::Client::ValueSerializer.serialize('hello', :header, 'string')).must_equal('hello')
    end

    it 'bhv_serializationparam_046 integer returns string' do
      _(Petstore::Client::ValueSerializer.serialize(42, :header, 'integer')).must_equal('42')
    end

    it 'bhv_serializationparam_023 boolean true returns true' do
      _(Petstore::Client::ValueSerializer.serialize(true, :header, 'boolean')).must_equal('true')
    end

    it 'bhv_serializationparam_005 array joins with comma' do
      _(Petstore::Client::ValueSerializer.serialize(%w[a b c], :header, 'array')).must_equal('a,b,c')
    end

    it 'bhv_serializationparam_030 empty array joins to empty string' do
      _(Petstore::Client::ValueSerializer.serialize([], :header, 'array')).must_equal('')
    end

    it 'bhv_serializationparam_013 array of integers stringifies and joins' do
      _(Petstore::Client::ValueSerializer.serialize([1, 2, 3], :header, 'array')).must_equal('1,2,3')
    end
  end

  describe 'cookie location' do
    it 'bhv_serializationparam_099 string returns as-is' do
      _(Petstore::Client::ValueSerializer.serialize('hello', :cookie, 'string')).must_equal('hello')
    end

    it 'bhv_serializationparam_065 null returns empty string' do
      _(Petstore::Client::ValueSerializer.serialize(nil, :cookie, 'string')).must_equal('')
    end
  end

  describe 'form location' do
    it 'bhv_serializationparam_064 null returns empty string' do
      _(Petstore::Client::ValueSerializer.serialize(nil, :form, 'string')).must_equal('')
    end

    it 'bhv_serializationparam_098 string returns as-is' do
      _(Petstore::Client::ValueSerializer.serialize('hello', :form, 'string')).must_equal('hello')
    end

    it 'bhv_serializationparam_047 integer returns string' do
      _(Petstore::Client::ValueSerializer.serialize(42, :form, 'integer')).must_equal('42')
    end

    it 'bhv_serializationparam_024 boolean true returns true' do
      _(Petstore::Client::ValueSerializer.serialize(true, :form, 'boolean')).must_equal('true')
    end

    it 'bhv_serializationparam_020 boolean false returns false' do
      _(Petstore::Client::ValueSerializer.serialize(false, :form, 'boolean')).must_equal('false')
    end
  end

  describe 'serialize_styled' do
    describe 'matrix style' do
      it 'bhv_serializationparam_058 scalar returns semicolon-prefixed name=value' do
        result = Petstore::Client::ValueSerializer.serialize_styled('color', 'blue', :path, 'string', nil, 'matrix', true)
        _(result).must_equal(';color=blue')
      end

      it 'bhv_serializationparam_055 array with explode false joins with comma' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :path, 'array', nil, 'matrix', false
        )
        _(result).must_equal(';color=blue,black')
      end

      it 'bhv_serializationparam_056 array with explode true repeats name' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :path, 'array', nil, 'matrix', true
        )
        _(result).must_equal(';color=blue;color=black')
      end

      it 'bhv_serializationparam_057 null returns empty string' do
        result = Petstore::Client::ValueSerializer.serialize_styled('color', nil, :path, 'string', nil, 'matrix', true)
        _(result).must_equal('')
      end
    end

    describe 'label style' do
      it 'bhv_serializationparam_052 scalar returns dot-prefixed value' do
        result = Petstore::Client::ValueSerializer.serialize_styled('color', 'blue', :path, 'string', nil, 'label', true)
        _(result).must_equal('.blue')
      end

      it 'bhv_serializationparam_049 array with explode false joins with comma after dot' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :path, 'array', nil, 'label', false
        )
        _(result).must_equal('.blue,black')
      end

      it 'bhv_serializationparam_050 array with explode true joins with dot separator' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :path, 'array', nil, 'label', true
        )
        _(result).must_equal('.blue.black')
      end

      it 'bhv_serializationparam_051 null returns empty string' do
        result = Petstore::Client::ValueSerializer.serialize_styled('color', nil, :path, 'string', nil, 'label', true)
        _(result).must_equal('')
      end
    end

    describe 'spaceDelimited style' do
      it 'bhv_serializationparam_093 array joins with space' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'spaceDelimited', false
        )
        _(result).must_equal('blue black')
      end

      it 'bhv_serializationparam_094 array with explode true returns list' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'spaceDelimited', true
        )
        _(result).must_equal(%w[blue black])
      end

      it 'bhv_serializationparam_095 scalar returns stringified value' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', 'blue', :query, 'string', nil, 'spaceDelimited', false
        )
        _(result).must_equal('blue')
      end
    end

    describe 'pipeDelimited style' do
      it 'bhv_serializationparam_074 array joins with pipe' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'pipeDelimited', false
        )
        _(result).must_equal('blue|black')
      end

      it 'bhv_serializationparam_075 array with explode true returns list' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'pipeDelimited', true
        )
        _(result).must_equal(%w[blue black])
      end

      it 'bhv_serializationparam_076 scalar returns stringified value' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', 'blue', :query, 'string', nil, 'pipeDelimited', false
        )
        _(result).must_equal('blue')
      end
    end

    describe 'form style with explode' do
      it 'bhv_serializationparam_037 array with explode false joins with comma' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'form', false
        )
        _(result).must_equal('blue,black')
      end

      it 'bhv_serializationparam_038 array with explode true returns list' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', %w[blue black], :query, 'array', nil, 'form', true
        )
        _(result).must_equal(%w[blue black])
      end

      it 'bhv_serializationparam_041 scalar with explode true returns string not list' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', 'blue', :query, 'string', nil, 'form', true
        )
        _(result).must_equal('blue')
      end

      it 'bhv_serializationparam_042 single-element array with explode true returns list' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', ['blue'], :query, 'array', nil, 'form', true
        )
        _(result).must_equal(['blue'])
      end

      it 'bhv_serializationparam_040 null returns nil for query location' do
        result = Petstore::Client::ValueSerializer.serialize_styled('color', nil, :query, 'string', nil, 'form', true)
        _(result).must_be_nil
      end

      it 'a Set with explode false joins its elements like an array' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', Set['red', 'blue'], :query, 'array', nil, 'form', false
        )
        _(result).must_equal('red,blue')
      end

      it 'bhv_serializationparam_083 a Set with explode true returns one value per element like an array' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'color', Set['red', 'blue'], :query, 'array', nil, 'form', true
        )
        _(result).must_equal(%w[red blue])
      end
    end

    describe 'simple style backward compatibility' do
      it 'bhv_serializationparam_086 scalar returns stringified value' do
        result = Petstore::Client::ValueSerializer.serialize_styled('id', '5', :path, 'string', nil, 'simple', false)
        _(result).must_equal('5')
      end

      it 'bhv_serializationparam_084 array joins with comma' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'id', %w[3 4 5], :path, 'array', nil, 'simple', false
        )
        _(result).must_equal('3,4,5')
      end

      it 'bhv_serializationparam_085 null returns empty string' do
        result = Petstore::Client::ValueSerializer.serialize_styled('id', nil, :path, 'string', nil, 'simple', true)
        _(result).must_equal('')
      end

      it 'bhv_serializationparam_087 scalar URL-encodes for path' do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'id', 'hello world', :path, 'string', nil, 'simple', false
        )
        _(result).must_equal('hello%20world')
      end
    end

    describe 'null style falls back to location default' do
      it 'path with null style behaves like simple' do
        result = Petstore::Client::ValueSerializer.serialize_styled('id', '5', :path, 'string', nil, nil, false)
        _(result).must_equal('5')
      end

      it 'bhv_serializationparam_035 empty style falls back to location default' do
        result = Petstore::Client::ValueSerializer.serialize_styled('id', '5', :path, 'string', nil, '', false)
        _(result).must_equal('5')
      end
    end
  end

  describe 'serialize_deep_object' do
    it 'bhv_serializationparam_017 basic map returns bracketed keys' do
      result = Petstore::Client::ValueSerializer.serialize_deep_object('filter', { 'color' => 'blue', 'size' => 'large' })
      _(result['filter[color]']).must_equal('blue')
      _(result['filter[size]']).must_equal('large')
    end

    it 'bhv_serializationparam_061 null returns empty hash' do
      result = Petstore::Client::ValueSerializer.serialize_deep_object('filter', nil)
      _(result).must_equal({})
    end

    it 'a model is expanded under its wire names, unset fields omitted' do
      model = Class.new
      model.const_set(:ATTRIBUTE_MAP, { min_size: 'minSize', label: 'label', unset: 'unset' }.freeze)
      model.define_method(:min_size) { 3 }
      model.define_method(:label) { 'big' }
      model.define_method(:unset) { nil }
      result = Petstore::Client::ValueSerializer.serialize_deep_object('filter', model.new)
      _(result).must_equal({ 'filter[minSize]' => '3', 'filter[label]' => 'big' })
    end
  end

  # Cross-language parity tests for path-segment percent-encoding.
  # Every SDK must produce identical encoded strings for these inputs.
  describe 'path encoding parity' do
    it 'bhv_serializationparam_016 ASCII-safe pass-through' do
      _(Petstore::Client::ValueSerializer.serialize('abc123', :path, 'string')).must_equal('abc123')
    end

    it 'bhv_serializationparam_092 space encoded as %20' do
      _(Petstore::Client::ValueSerializer.serialize('a b', :path, 'string')).must_equal('a%20b')
    end

    it 'bhv_serializationparam_091 slash encoded' do
      _(Petstore::Client::ValueSerializer.serialize('a/b', :path, 'string')).must_equal('a%2Fb')
    end

    it 'bhv_serializationparam_079 question mark encoded' do
      _(Petstore::Client::ValueSerializer.serialize('a?b', :path, 'string')).must_equal('a%3Fb')
    end

    it 'bhv_serializationparam_043 hash encoded' do
      _(Petstore::Client::ValueSerializer.serialize('a#b', :path, 'string')).must_equal('a%23b')
    end

    it 'bhv_serializationparam_026 comma preserved (sub-delimiter)' do
      _(Petstore::Client::ValueSerializer.serialize('a,b', :path, 'string')).must_equal('a,b')
    end

    it 'bhv_serializationparam_025 colon preserved (sub-delimiter)' do
      _(Petstore::Client::ValueSerializer.serialize('a:b', :path, 'string')).must_equal('a:b')
    end

    it 'bhv_serializationparam_077 plus preserved (sub-delimiter)' do
      _(Petstore::Client::ValueSerializer.serialize('a+b', :path, 'string')).must_equal('a+b')
    end

    it 'tilde preserved (RFC 3986 unreserved)' do
      # '~' is an RFC 3986 unreserved character and must never be
      # percent-encoded. URI.encode_www_form_component emits %7E; the
      # canonical encoding (Java, Kotlin, Node, C#, etc.) keeps a literal '~'.
      _(Petstore::Client::ValueSerializer.serialize('a~b', :path, 'string')).must_equal('a~b')
      _(Petstore::Client::ValueSerializer.serialize('~', :path, 'string')).must_equal('~')
    end

    # The OAS path sub-delimiters and the unreserved ~ stay literal, one
    # character per row so a dropped one names itself.
    [';', '=', '~', '@', '!', '$', '&', "'", '(', ')', '*', '+'].each do |char|
      it "bhv_serializationparam_071 #{char.inspect} preserved in a path segment" do
        _(Petstore::Client::ValueSerializer.serialize("a#{char}b", :path, 'string')).must_equal("a#{char}b")
      end
    end

    ['%', '[', ']', '{', '}', '|', '"', '<', '>', '^', '`', '\\'].each do |char|
      it "bhv_serializationparam_002 #{char.inspect} encoded in a path segment" do
        encoded = char.bytes.map { |byte| format('%%%02X', byte) }.join
        _(Petstore::Client::ValueSerializer.serialize("a#{char}b", :path, 'string')).must_equal("a#{encoded}b")
      end
    end

    it 'bhv_serializationparam_004 an already percent-encoded value is encoded again' do
      _(Petstore::Client::ValueSerializer.serialize('a%20b', :path, 'string')).must_equal('a%2520b')
    end

    it 'bhv_serializationparam_103 unicode encoded as UTF-8 percent' do
      _(Petstore::Client::ValueSerializer.serialize('日本', :path, 'string')).must_equal('%E6%97%A5%E6%9C%AC')
    end

    it 'bhv_serializationparam_034 empty string preserved' do
      _(Petstore::Client::ValueSerializer.serialize('', :path, 'string')).must_equal('')
    end

    it 'bhv_serializationparam_066 null returns empty string in path location' do
      _(Petstore::Client::ValueSerializer.serialize(nil, :path, 'string')).must_equal('')
    end

    it 'bhv_serializationparam_089 simple style encodes value' do
      result = Petstore::Client::ValueSerializer.serialize_styled('color', 'a b', :path, 'string', nil, 'simple', false)
      _(result).must_equal('a%20b')
    end

    it 'bhv_serializationparam_088 simple style array encodes each item' do
      result = Petstore::Client::ValueSerializer.serialize_styled('color', ['a b', 'c?d'], :path, 'array', nil, 'simple', false)
      _(result).must_equal('a%20b,c%3Fd')
    end

    it 'bhv_serializationparam_069 path_array_item_with_reserved_char_is_percent_encoded' do
      # Gap W1 regression: every per-item path value in a styled array
      # must be percent-encoded BEFORE being joined with the structural
      # separator. Otherwise '/', '?', '#', space leak into the URL.
      items = %w[a/b c]
      _(Petstore::Client::ValueSerializer.serialize_styled('name', items, :path, 'array', nil, 'simple', false))
        .must_equal('a%2Fb,c')
      _(Petstore::Client::ValueSerializer.serialize_styled('name', items, :path, 'array', nil, 'label', true))
        .must_equal('.a%2Fb.c')
      _(Petstore::Client::ValueSerializer.serialize_styled('name', items, :path, 'array', nil, 'matrix', false))
        .must_equal(';name=a%2Fb,c')
    end

    it 'bhv_serializationparam_059 matrix style encodes value' do
      result = Petstore::Client::ValueSerializer.serialize_styled('color', 'a b', :path, 'string', nil, 'matrix', false)
      _(result).must_equal(';color=a%20b')
    end

    it 'bhv_serializationparam_053 label style encodes value' do
      result = Petstore::Client::ValueSerializer.serialize_styled('color', 'a b', :path, 'string', nil, 'label', false)
      _(result).must_equal('.a%20b')
    end

    it 'bhv_serializationparam_078 query location is not path-encoded' do
      result = Petstore::Client::ValueSerializer.serialize_styled('color', 'a b', :query, 'string', nil, 'form', false)
      _(result).must_equal('a b')
    end

    it 'bhv_serializationparam_033 empty string path param raises ArgumentError' do
      # Gap W — empty-string path values silently produce malformed
      # URLs like `/resource//details`; reject at serialization time so
      # callers see the real error rather than a downstream 404.
      _(lambda {
        Petstore::Client::ValueSerializer.serialize_styled('id', '', :path, 'string', nil, 'simple', false)
      }).must_raise ArgumentError
    end

    ['.', '..'].each do |segment|
      it "bhv_serializationparam_029 rejects the dot segment #{segment.inspect} as a simple path value" do
        err = _(lambda {
          Petstore::Client::ValueSerializer.serialize_styled('id', segment, :path, 'string', nil, 'simple', false)
        }).must_raise ArgumentError
        _(err.message).must_equal "Path parameter 'id' must not be '.' or '..'"
      end
    end

    it 'bhv_serializationparam_072 keeps dots inside a path value and dot values in label and matrix styles' do
      _(Petstore::Client::ValueSerializer.serialize_styled('id', 'a..b', :path, 'string', nil, 'simple', false)).must_equal('a..b')
      _(Petstore::Client::ValueSerializer.serialize_styled('id', '.hidden', :path, 'string', nil, 'simple', false)).must_equal('.hidden')
      _(Petstore::Client::ValueSerializer.serialize_styled('id', '..', :path, 'string', nil, 'label', false)).must_equal('...')
      _(Petstore::Client::ValueSerializer.serialize_styled('id', '..', :path, 'string', nil, 'matrix', false)).must_equal(';id=..')
    end

    it 'bhv_serializationparam_073 path value is encoded exactly once (no double-encoding)' do
      # path-double-encoding: serialize_styled already percent-encodes the path
      # segment, so the api template must NOT wrap it again. A space must become
      # %20 (never %2520) and a slash %2F (never %252F).
      space = Petstore::Client::ValueSerializer.serialize_styled('id', 'a b', :path, 'string', nil, 'simple', false)
      _(space).must_equal('a%20b')
      _(space).wont_include('%2520')
      slash = Petstore::Client::ValueSerializer.serialize_styled('id', 'a/b', :path, 'string', nil, 'simple', false)
      _(slash).must_equal('a%2Fb')
      _(slash).wont_include('%252F')
    end
  end

  describe 'content cookie encoding' do
    it 'keeps only the unreserved set literal and encodes the rest as UTF-8' do
      encoded = Petstore::Client::ValueSerializer.encode_content_cookie('{"k":"a b~*é"}')

      _(encoded).must_equal('%7B%22k%22%3A%22a%20b~%2A%C3%A9%22%7D')
      _(encoded).must_match(/\A[!\x23-\x2B\x2D-\x3A\x3C-\x5B\x5D-\x7E]*\z/)
    end

    it 'bhv_serializationparam_081 encodes the sub-delimiters and the asterisk' do
      _(Petstore::Client::ValueSerializer.encode_content_cookie("!'()*")).must_equal('%21%27%28%29%2A')
    end
  end

  # allowReserved query encoding: a query parameter with allowReserved: true
  # leaves RFC 3986 reserved characters literal on the wire, while everything
  # else (spaces, control, non-ASCII) is still percent-encoded.
  describe 'allowReserved query encoding' do
    it 'bhv_serializationparam_080 reserved characters are left literal, other characters still encoded' do
      _(Petstore::Client::ValueSerializer.encode_query_allowing_reserved('v1.0/beta:rc1')).must_equal('v1.0/beta:rc1')
      # Space is illegal in a URL and must still be percent-encoded even
      # when reserved characters are preserved.
      _(Petstore::Client::ValueSerializer.encode_query_allowing_reserved('a b:c')).must_equal('a%20b:c')
    end

    it 'bhv_serializationparam_068 keeps only the RFC 3986 query-legal reserved set literal and encodes # [ ]' do
      encoded = Petstore::Client::ValueSerializer.encode_query_allowing_reserved("a:b/c?d[e]f@g!h$i&j'k(l)m*n+o,p;q=r~s#t u")
      _(encoded).must_equal("a:b/c?d%5Be%5Df@g!h$i&j'k(l)m*n+o,p;q=r~s%23t%20u")
    end

    it 'bhv_serializationparam_060 maybe_allow_reserved wraps only when allow_reserved is true' do
      _(Petstore::Client::ValueSerializer.maybe_allow_reserved('plain', false)).must_equal('plain')
      wrapped = Petstore::Client::ValueSerializer.maybe_allow_reserved('v1/beta', true)
      _(wrapped).must_be_instance_of(Petstore::Client::ValueSerializer::AllowReservedValue)
      _(wrapped.value).must_equal('v1/beta')
      _(Petstore::Client::ValueSerializer.maybe_allow_reserved(nil, true)).must_be_nil
    end
  end

  # N3/W3 parity: `format: date` path parameters must emit a date-only
  # string (YYYY-MM-DD), not a full ISO datetime. Ruby models
  # `format: date` as `Date`, whose `to_s` already produces YYYY-MM-DD.
  describe 'format:date path parameter emits YYYY-MM-DD' do
    require 'date'

    it 'bhv_serializationparam_028 Date in path returns YYYY-MM-DD' do
      date = Date.new(2024, 1, 15)
      _(Petstore::Client::ValueSerializer.serialize(date, :path, 'string')).must_equal('2024-01-15')
    end

    it 'bhv_serializationparam_054 Date via serialize_styled simple returns YYYY-MM-DD' do
      date = Date.new(2024, 1, 15)
      result = Petstore::Client::ValueSerializer.serialize_styled('since', date, :path, 'string', nil, 'simple', false)
      _(result).must_equal('2024-01-15')
    end

    it 'bhv_serializationparam_027 Date at a day/year boundary still emits date-only (no time/offset leak)' do
      # UTC/midnight edge: a date on the year boundary must serialize as a bare
      # YYYY-MM-DD with no time/offset suffix, matching the stringifyDate UTC
      # behaviour of Go/Node/Swift/Dart.
      date = Date.new(2024, 12, 31)
      _(Petstore::Client::ValueSerializer.serialize(date, :path, 'string')).must_equal('2024-12-31')
    end
  end

  # A DateTime is a Date in Ruby, so a format: date parameter must still drop
  # the time of day.
  describe 'format:date parameter given a date-time' do
    require 'date'

    it 'writes a DateTime as YYYY-MM-DD' do
      value = DateTime.new(2024, 1, 15, 23, 59, 30)
      result = Petstore::Client::ValueSerializer.serialize_styled('born_on', value, :query, 'Date', nil, 'form', true)
      _(result).must_equal('2024-01-15')
    end

    it 'writes a Time as YYYY-MM-DD' do
      value = Time.new(2024, 1, 15, 23, 59, 30, '+00:00')
      result = Petstore::Client::ValueSerializer.serialize_styled('born_on', value, :path, 'Date', nil, 'simple', false)
      _(result).must_equal('2024-01-15')
    end

    it 'writes every element of an array of date-times as YYYY-MM-DD' do
      values = [DateTime.new(2024, 1, 15, 1, 2, 3), Date.new(2024, 2, 1)]
      result = Petstore::Client::ValueSerializer.serialize_styled('days', values, :path, 'Array<Date>', nil, 'simple', false)
      _(result).must_equal('2024-01-15,2024-02-01')
    end

    it 'bhv_serializationparam_048 keeps the time of a date-time parameter that is not a format: date' do
      value = DateTime.new(2024, 1, 15, 1, 2, 3)
      result = Petstore::Client::ValueSerializer.serialize_styled('at', value, :path, 'Time', nil, 'simple', false)
      _(result).must_include('2024-01-15T01:02:03')
    end
  end

  # D47: an object value (a model or a Hash) is expanded by style and explode,
  # never rendered through its to_s.
  describe 'object values' do
    def object_model
      model = Class.new
      model.const_set(:ATTRIBUTE_MAP, { breed: 'breed', min_age: 'min_age', unset: 'unset' }.freeze)
      model.define_method(:breed) { 'lab' }
      model.define_method(:min_age) { 3 }
      model.define_method(:unset) { nil }
      model.new
    end

    it 'bhv_serializationparam_036 expands an exploded form query value to one entry per property' do
      result = Petstore::Client::ValueSerializer.query_entries('criteria', object_model, 'Criteria', nil, 'form', true, false)
      _(result).must_equal({ 'breed' => 'lab', 'min_age' => '3' })
    end

    it 'bhv_serializationparam_039 joins a non-exploded form query value into one comma-separated entry' do
      result = Petstore::Client::ValueSerializer.query_entries('csv_params', object_model, 'Criteria', nil, 'form', false, false)
      _(result).must_equal({ 'csv_params' => 'breed,lab,min_age,3' })
    end

    it 'keeps a scalar under its own name' do
      result = Petstore::Client::ValueSerializer.query_entries('filter', 'x', 'String', nil, 'form', true, false)
      _(result).must_equal({ 'filter' => 'x' })
    end

    it 'bhv_serializationparam_003 wraps an exploded entry when the parameter allows reserved characters' do
      result = Petstore::Client::ValueSerializer.query_entries('criteria', { 'breed' => 'a/b' }, 'Criteria', nil, 'form', true, true)
      _(result['breed']).must_be_kind_of(Petstore::Client::ValueSerializer::AllowReservedValue)
      _(result['breed'].value).must_equal('a/b')
    end

    {
      ['simple', false] => 'breed,lab,min_age,3',
      ['simple', true] => 'breed=lab,min_age=3',
      ['matrix', false] => ';criteria=breed,lab,min_age,3',
      ['matrix', true] => ';breed=lab;min_age=3',
      ['label', false] => '.breed,lab,min_age,3',
      ['label', true] => '.breed=lab.min_age=3'
    }.each do |(style, explode), expected|
      it "renders an object in a path by #{style} explode=#{explode}" do
        result = Petstore::Client::ValueSerializer.serialize_styled(
          'criteria', { 'breed' => 'lab', 'min_age' => 3 }, :path, 'Criteria', nil, style, explode # steep:ignore ArgumentTypeMismatch
        )
        _(result).must_equal(expected)
      end
    end

    it 'flattens an object in a query by spaceDelimited and pipeDelimited style' do
      fields = { 'breed' => 'lab', 'min_age' => 3 }
      space = Petstore::Client::ValueSerializer.serialize_styled('c', fields, :query, 'Criteria', nil, 'spaceDelimited', false)
      pipe = Petstore::Client::ValueSerializer.serialize_styled('c', fields, :query, 'Criteria', nil, 'pipeDelimited', false)
      _(space).must_equal('breed lab min_age 3')
      _(pipe).must_equal('breed|lab|min_age|3')
    end

    it 'bhv_serializationparam_082 renders a model in a header by simple style' do
      result = Petstore::Client::ValueSerializer.serialize_styled(
        'X-Criteria', object_model, :header, 'Criteria', nil, 'simple', false
      )
      _(result).must_equal('breed,lab,min_age,3')
    end
  end

  it 'bhv_serializationparam_070 path encoding of null is empty for every schema type' do
    _(Petstore::Client::ValueSerializer.serialize(nil, :path, 'integer')).must_equal('')
  end
end
