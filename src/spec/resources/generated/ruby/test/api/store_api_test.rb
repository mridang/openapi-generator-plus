# frozen_string_literal: true

# rubocop:disable Metrics/BlockLength, Lint/MissingCopEnableDirective

# Integration tests for the Store API endpoints.

require 'test_helper'
require 'socket'

describe PetstoreClient::Api::StoreApi do
  parallelize_me!

  before do
    @api = PetstoreClient::Api::StoreApi.new
  end

  describe '#get_inventory' do
    it 'returns inventory' do
      result = @api.get_inventory

      _(result).must_be_kind_of(Hash)
    end
  end

  describe '#place_order' do
    it 'places an order' do
      order = PetstoreClient::Models::Order.new(
        id: 1,
        pet_id: 12_345,
        quantity: 1,
        ship_date: Time.now.utc.iso8601,
        status: 'placed',
        complete: false
      )

      result = @api.place_order(order)

      _(result).wont_be_nil
      _(result.id).wont_be_nil
    end
  end

  describe '#get_order_by_id' do
    it 'returns an order by id' do
      result = @api.get_order_by_id(1)

      _(result).wont_be_nil
      _(result.id).wont_be_nil
    end
  end

  describe '#delete_order' do
    it 'deletes an order' do
      @api.delete_order(1)
    end
  end

  describe 'error handling' do
    def new_store_api_for_mock(status, content_type, body) # rubocop:disable Metrics/AbcSize, Metrics/MethodLength
      server = TCPServer.new('127.0.0.1', 0)
      port = server.addr[1]
      thread = Thread.new do
        loop do
          client = server.accept rescue break # rubocop:disable Style/RescueModifier
          client.gets # read request line
          while (line = client.gets)
            break if line.strip.empty?
          end
          response = "HTTP/1.1 #{status} OK\r\nContent-Type: #{content_type}\r\n" \
                     "Content-Length: #{body.bytesize}\r\nConnection: close\r\n\r\n#{body}"
          client.print(response)
          client.close
        end
      end

      config = PetstoreClient::Configuration.new(base_url: "http://127.0.0.1:#{port}", default_headers: {})
      api = PetstoreClient::Api::StoreApi.new(nil, config)
      [api, server, thread]
    end

    it 'raises error on get_order_by_id 404' do
      api, server, thread = new_store_api_for_mock(404, 'application/json', '{"message":"Order not found"}')
      begin
        _(-> { api.get_order_by_id(99_999) }).must_raise StandardError
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'raises error on place_order 500' do
      api, server, thread = new_store_api_for_mock(500, 'application/json', '{"message":"Internal server error"}')
      begin
        order = PetstoreClient::Models::Order.new(
          id: 1,
          pet_id: 12_345,
          quantity: 1,
          status: 'placed',
          complete: false
        )
        _(-> { api.place_order(order) }).must_raise StandardError
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'raises error on delete_order 404' do
      api, server, thread = new_store_api_for_mock(404, 'application/json', '{"message":"Order not found"}')
      begin
        _(-> { api.delete_order(99_999) }).must_raise StandardError
      ensure
        server.close
        thread.join(2)
      end
    end
  end
end
