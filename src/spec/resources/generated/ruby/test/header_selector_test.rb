# frozen_string_literal: true

require 'test_helper'
require 'petstore/client/header_selector'

describe Petstore::Client::HeaderSelector do
  parallelize_me!

  before do
    @header_selector = Petstore::Client::HeaderSelector.new
  end

  describe '#json_mime?' do
    it 'bhv_headers_019 returns true for application/json' do
      _(@header_selector.json_mime?('application/json')).must_equal(true)
    end

    it 'bhv_headers_020 returns true for application/json with charset' do
      _(@header_selector.json_mime?('application/json; charset=UTF-8')).must_equal(true)
    end

    it 'bhv_headers_021 returns true for uppercase APPLICATION/JSON (case insensitive)' do
      _(@header_selector.json_mime?('APPLICATION/JSON')).must_equal(true)
    end

    it 'bhv_headers_022 returns true for vendor JSON types' do
      _(@header_selector.json_mime?('application/vnd.api+json')).must_equal(true)
      _(@header_selector.json_mime?('application/vnd.company+json')).must_equal(true)
      _(@header_selector.json_mime?('application/hal+json')).must_equal(true)
    end

    it 'bhv_headers_016 returns false for text/html' do
      _(@header_selector.json_mime?('text/html')).must_equal(false)
    end

    it 'bhv_headers_013 returns false for application/xml' do
      _(@header_selector.json_mime?('application/xml')).must_equal(false)
    end

    it 'bhv_headers_012 returns false for application/octet-stream' do
      _(@header_selector.json_mime?('application/octet-stream')).must_equal(false)
    end

    it 'bhv_headers_015 returns false for nil' do
      _(@header_selector.json_mime?(nil)).must_equal(false)
    end

    it 'bhv_headers_014 returns false for empty string' do
      _(@header_selector.json_mime?('')).must_equal(false)
    end
  end

  describe '#select_headers' do
    it 'bhv_headers_024 sets Accept header when accepts provided' do
      headers = @header_selector.select_headers(
        ['application/json'],
        'application/json',
        false
      )
      _(headers['Accept']).must_equal('application/json')
    end

    it 'bhv_headers_006 does not set Accept header when all entries are blank' do
      headers = @header_selector.select_headers(
        ['', '   ', nil],
        'application/json',
        false
      )
      _(headers['Accept']).must_be_nil
    end

    it 'bhv_headers_009 filters out empty and blank entries' do
      headers = @header_selector.select_headers(
        ['', '   ', 'application/json'],
        'application/json',
        false
      )
      _(headers['Accept']).must_equal('application/json')
    end

    it 'bhv_headers_004 does not set Accept header when accepts empty' do
      headers = @header_selector.select_headers(
        [],
        'application/json',
        false
      )
      _(headers['Accept']).must_be_nil
    end

    it 'bhv_headers_025 sets Content-Type header when not multipart' do
      headers = @header_selector.select_headers(
        ['application/json'],
        'application/json',
        false
      )
      _(headers['Content-Type']).must_equal('application/json')
    end

    it 'bhv_headers_007 does not set Content-Type header when multipart' do
      headers = @header_selector.select_headers(
        ['application/json'],
        'application/json',
        true
      )
      _(headers['Content-Type']).must_be_nil
    end

    it 'bhv_headers_001 defaults Content-Type to application/json when empty' do
      headers = @header_selector.select_headers(
        ['application/json'],
        '',
        false
      )
      _(headers['Content-Type']).must_equal('application/json')
    end

    it 'bhv_headers_002 defaults Content-Type to application/json when nil' do
      headers = @header_selector.select_headers(
        ['application/json'],
        nil,
        false
      )
      _(headers['Content-Type']).must_equal('application/json')
    end

    it 'bhv_headers_017 returns single accept as-is' do
      headers = @header_selector.select_headers(
        ['application/json'],
        'application/json',
        false
      )
      _(headers['Accept']).must_equal('application/json')
    end

    it 'bhv_headers_018 returns single non-JSON accept as-is' do
      headers = @header_selector.select_headers(
        ['text/html'],
        'application/json',
        false
      )
      _(headers['Accept']).must_equal('text/html')
    end

    it 'bhv_headers_026 joins media types in declaration order' do
      headers = @header_selector.select_headers(
        %w[image/jpeg image/png application/json],
        'application/json',
        false
      )
      _(headers['Accept']).must_equal('image/jpeg, image/png, application/json')
    end

    it 'bhv_headers_003 does not reorder or prioritize JSON types' do
      headers = @header_selector.select_headers(
        %w[text/html application/vnd.api+json application/json],
        'application/json',
        false
      )
      _(headers['Accept']).must_equal('text/html, application/vnd.api+json, application/json')
    end

    it 'bhv_headers_010 filters out empty entries' do
      headers = @header_selector.select_headers(
        ['', 'application/json', nil],
        'application/json',
        false
      )
      _(headers['Accept']).must_equal('application/json')
    end

    it 'bhv_headers_008 drops whitespace-only entries' do
      headers = @header_selector.select_headers(
        ['   ', 'application/json'],
        'application/json',
        false
      )
      _(headers['Accept']).must_equal('application/json')
    end
  end

  describe '#select_accept_header (private)' do
    it 'bhv_headers_005 returns nil for nil input' do
      _(@header_selector.send(:select_accept_header, nil)).must_be_nil
    end

    it 'bhv_headers_029 returns empty string when all entries are filtered out' do
      _(@header_selector.send(:select_accept_header, ['', nil])).must_equal('')
    end

    it 'bhv_headers_027 returns empty string for empty accept array' do
      _(@header_selector.send(:select_accept_header, [])).must_equal('')
    end

    it 'bhv_headers_023 returns single accept as-is' do
      _(@header_selector.send(:select_accept_header, ['application/json'])).must_equal('application/json')
    end

    it 'bhv_headers_011 joins media types in declaration order with ", "' do
      result = @header_selector.send(:select_accept_header, %w[image/jpeg image/png application/json])
      _(result).must_equal('image/jpeg, image/png, application/json')
    end

    it 'bhv_headers_032 lists media types without quality weights' do
      result = @header_selector.send(:select_accept_header, %w[text/html text/plain])
      _(result).must_equal('text/html, text/plain')
      _(result).wont_include('q=')
    end
  end
end
