=begin
#Swagger Petstore - OpenAPI 3.0

#Unit tests for HeaderSelector.

#These tests verify RFC 9110 compliant content negotiation with quality weights.

=end

require 'spec_helper'
require 'opigen_client/header_selector'

RSpec.describe OpigenClient::HeaderSelector do
  let(:header_selector) { described_class.new }

  describe '#is_json_mime' do
    it 'returns true for application/json' do
      expect(header_selector.is_json_mime('application/json')).to be true
    end

    it 'returns true for application/json with charset' do
      expect(header_selector.is_json_mime('application/json; charset=UTF-8')).to be true
    end

    it 'returns true for uppercase APPLICATION/JSON (case insensitive)' do
      expect(header_selector.is_json_mime('APPLICATION/JSON')).to be true
    end

    it 'returns true for vendor JSON types' do
      expect(header_selector.is_json_mime('application/vnd.api+json')).to be true
      expect(header_selector.is_json_mime('application/vnd.company+json')).to be true
      expect(header_selector.is_json_mime('application/hal+json')).to be true
    end

    it 'returns false for text/html' do
      expect(header_selector.is_json_mime('text/html')).to be false
    end

    it 'returns false for application/xml' do
      expect(header_selector.is_json_mime('application/xml')).to be false
    end

    it 'returns false for nil' do
      expect(header_selector.is_json_mime(nil)).to be false
    end

    it 'returns false for empty string' do
      expect(header_selector.is_json_mime('')).to be false
    end
  end

  describe '#select_headers' do
    it 'sets Accept header when accepts provided' do
      headers = header_selector.select_headers(
        ['application/json'],
        'application/json',
        false
      )
      expect(headers['Accept']).to eq('application/json')
    end

    it 'does not set Accept header when accepts empty' do
      headers = header_selector.select_headers(
        [],
        'application/json',
        false
      )
      expect(headers['Accept']).to be_nil
    end

    it 'sets Content-Type header when not multipart' do
      headers = header_selector.select_headers(
        ['application/json'],
        'application/json',
        false
      )
      expect(headers['Content-Type']).to eq('application/json')
    end

    it 'does not set Content-Type header when multipart' do
      headers = header_selector.select_headers(
        ['application/json'],
        'application/json',
        true
      )
      expect(headers['Content-Type']).to be_nil
    end

    it 'defaults Content-Type to application/json when empty' do
      headers = header_selector.select_headers(
        ['application/json'],
        '',
        false
      )
      expect(headers['Content-Type']).to eq('application/json')
    end

    it 'defaults Content-Type to application/json when nil' do
      headers = header_selector.select_headers(
        ['application/json'],
        nil,
        false
      )
      expect(headers['Content-Type']).to eq('application/json')
    end

    it 'returns single accept as-is' do
      headers = header_selector.select_headers(
        ['application/json'],
        'application/json',
        false
      )
      expect(headers['Accept']).to eq('application/json')
    end

    it 'returns single non-JSON accept as-is' do
      headers = header_selector.select_headers(
        ['text/html'],
        'application/json',
        false
      )
      expect(headers['Accept']).to eq('text/html')
    end

    it 'returns comma-separated list when no JSON types present' do
      headers = header_selector.select_headers(
        ['text/html', 'text/plain'],
        'application/json',
        false
      )
      expect(headers['Accept']).to eq('text/html,text/plain')
    end

    it 'prioritizes application/json with quality weight' do
      headers = header_selector.select_headers(
        ['text/html', 'application/json'],
        'application/json',
        false
      )
      accept = headers['Accept']
      # application/json should come first with highest weight
      expect(accept).to start_with('application/json')
      expect(accept).to include('text/html')
    end

    it 'handles multiple JSON types with priority' do
      headers = header_selector.select_headers(
        ['text/html', 'application/vnd.api+json', 'application/json'],
        'application/json',
        false
      )
      accept = headers['Accept']
      # application/json should come first
      expect(accept).to start_with('application/json')
      # application/vnd.api+json should come before text/html
      json_index = accept.index('application/json')
      vendor_json_index = accept.index('application/vnd.api+json')
      html_index = accept.index('text/html')
      expect(json_index).to be < vendor_json_index
      expect(vendor_json_index).to be < html_index
    end

    it 'filters out empty entries' do
      headers = header_selector.select_headers(
        ['', 'application/json', nil],
        'application/json',
        false
      )
      expect(headers['Accept']).to eq('application/json')
    end

    it 'preserves existing quality weights in order' do
      headers = header_selector.select_headers(
        ['text/html;q=0.9', 'application/json', 'text/plain;q=0.8'],
        'application/json',
        false
      )
      accept = headers['Accept']
      # application/json should still come first (JSON priority)
      expect(accept).to start_with('application/json')
    end
  end

  describe '#select_accept_header (private)' do
    it 'returns nil for nil input' do
      expect(header_selector.send(:select_accept_header, nil)).to be_nil
    end

    it 'returns nil for empty array' do
      expect(header_selector.send(:select_accept_header, [])).to be_nil
    end

    it 'returns single accept as-is' do
      expect(header_selector.send(:select_accept_header, ['application/json'])).to eq('application/json')
    end

    it 'returns single non-JSON accept as-is' do
      expect(header_selector.send(:select_accept_header, ['text/html'])).to eq('text/html')
    end

    it 'returns comma-separated list when no JSON types present' do
      result = header_selector.send(:select_accept_header, ['text/html', 'text/plain'])
      expect(result).to eq('text/html,text/plain')
    end
  end

  describe '#get_next_weight' do
    it 'returns standard weight sequence for <= 28 headers' do
      # Starting from 1000, should get: 1000, 900, 800, 700, ...
      expect(header_selector.get_next_weight(1000, false)).to eq(900)
      expect(header_selector.get_next_weight(900, false)).to eq(800)
      expect(header_selector.get_next_weight(800, false)).to eq(700)
      expect(header_selector.get_next_weight(700, false)).to eq(600)
      expect(header_selector.get_next_weight(600, false)).to eq(500)
      expect(header_selector.get_next_weight(500, false)).to eq(400)
      expect(header_selector.get_next_weight(400, false)).to eq(300)
      expect(header_selector.get_next_weight(300, false)).to eq(200)
      expect(header_selector.get_next_weight(200, false)).to eq(100)
      # After 100, goes to 90, 80, ...
      expect(header_selector.get_next_weight(100, false)).to eq(90)
      expect(header_selector.get_next_weight(90, false)).to eq(80)
    end

    it 'returns 1-by-1 decrement for > 28 headers' do
      expect(header_selector.get_next_weight(1000, true)).to eq(999)
      expect(header_selector.get_next_weight(999, true)).to eq(998)
      expect(header_selector.get_next_weight(998, true)).to eq(997)
    end

    it 'returns 1 when weight is 1 or less' do
      expect(header_selector.get_next_weight(1, false)).to eq(1)
      expect(header_selector.get_next_weight(0, false)).to eq(1)
      expect(header_selector.get_next_weight(-1, false)).to eq(1)
    end

    it 'produces exactly 27 steps from 1000 to 1' do
      # The formula should produce exactly 27 steps from 1000 to 1
      weight = 1000
      count = 0
      while weight > 1
        weight = header_selector.get_next_weight(weight, false)
        count += 1
      end
      # 1000 -> 900 -> 800 -> ... -> 100 -> 90 -> ... -> 10 -> 9 -> ... -> 1
      # That's 9 (1000 to 100) + 9 (100 to 10) + 9 (10 to 1) = 27 steps
      expect(count).to eq(27)
    end
  end

  describe 'quality weight formatting' do
    it 'does not add quality weight for weight 1000' do
      headers = header_selector.select_headers(
        ['application/json', 'text/html'],
        'application/json',
        false
      )
      accept = headers['Accept']
      # First header should not have ;q= because it's weight 1000
      expect(accept).to start_with('application/json,').or eq('application/json')
    end

    it 'formats quality weight correctly' do
      headers = header_selector.select_headers(
        ['application/json', 'text/html'],
        'application/json',
        false
      )
      accept = headers['Accept']
      # text/html should have quality weight like ;q=0.9
      expect(accept).to include('text/html;q=0.9').or include('text/html;q=0.')
    end

    it 'removes trailing zeros from quality weight' do
      headers = header_selector.select_headers(
        ['application/json', 'text/html'],
        'application/json',
        false
      )
      accept = headers['Accept']
      # Should be ;q=0.9 not ;q=0.900
      expect(accept).not_to include(';q=0.900')
    end
  end
end
