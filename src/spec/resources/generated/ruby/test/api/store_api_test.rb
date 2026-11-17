# frozen_string_literal: true

# Integration tests for the Store API endpoints.

require 'test_helper'
# The stdlib signature of TCPServer#initialize, (?String host, Integer port),
# puts a required parameter after an optional one, which steep reads as a
# single positional, so each TCPServer.new(host, port) below carries a
# steep:ignore for that one diagnostic.
require 'socket'

describe Petstore::Client::Api::StoreApi do
  parallelize_me!

  before do
    @api = Petstore::Client::Api::StoreApi.new(nil, TEST_CONFIGURATION)
  end

  # Starts a one-shot TCP server that records the real outgoing request line
  # headers (lower-cased names) and body, then answers 200 with the given JSON
  # +response_body+. The returned Queue receives one
  # { request_line:, headers:, body: } Hash. The +status_line+ is the status
  # code and reason it answers with.
  def capture_wire_request(response_body, status_line: '200 OK')
    server = TCPServer.new('127.0.0.1', 0) # steep:ignore UnexpectedPositionalArgument
    port = server.addr[1]
    captured = Queue.new
    thread = Thread.new do
      client = begin
        server.accept
      rescue StandardError
        next
      end
      request_line = client.gets.to_s.strip
      headers = {}
      while (line = client.gets)
        break if line.strip.empty?

        name, value = line.split(':', 2)
        headers[name.to_s.strip.downcase] = value.to_s.strip
      end
      length = headers.fetch('content-length', '0').to_i
      captured << { request_line: request_line, headers: headers,
                    body: length.positive? ? client.read(length).to_s : '' }
      client.print("HTTP/1.1 #{status_line}\r\nContent-Type: application/json\r\n" \
                   "Content-Length: #{response_body.bytesize}\r\nConnection: close\r\n\r\n#{response_body}")
      client.close
    end
    config = Petstore::Client::Configuration.new(base_url: "http://127.0.0.1:#{port}", default_headers: {})
    [Petstore::Client::Api::StoreApi.new(nil, config), server, thread, captured]
  end

  describe '#search_store content-encoded query' do
    it 'bhv_operations_097 encodes the q value through the SDK serializer as compact JSON' do
      api, server, thread, captured = capture_wire_request('[]')
      begin
        query = Petstore::Client::Models::PhotoMetadata.new(caption: 'a/b<é')
        api.search_store(Petstore::Client::Api::Options::SearchStoreOptions.new(q: query))
        request_line = captured.pop[:request_line]
        _(request_line).must_include 'q=%7B%22caption%22%3A%22a%2Fb%3C%C3%A9%22%7D'
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'raises SerializationError for an unserializable q and sends nothing' do
      api, server, thread, captured = capture_wire_request('[]')
      begin
        query = Petstore::Client::Models::PhotoMetadata.new(caption: 'c', location: { 'lat' => Float::NAN })
        _ { api.search_store(Petstore::Client::Api::Options::SearchStoreOptions.new(q: query)) }
          .must_raise(Petstore::Client::Errors::SerializationError)
        _(captured.empty?).must_equal(true)
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe '#search_store content-encoded header' do
    it 'bhv_operations_121 encodes the X-Filter value through the SDK serializer as compact JSON' do
      api, server, thread, captured = capture_wire_request('[]')
      begin
        api.search_store(Petstore::Client::Api::Options::SearchStoreOptions.new(x_filter: { 'k' => 'v' }))
        _(captured.pop[:headers]['x-filter']).must_equal('{"k":"v"}')
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe '#search_store uniqueItems query' do
    it 'expands a Set of labels into one pair per element' do
      api, server, thread, captured = capture_wire_request('[]')
      begin
        api.search_store(Petstore::Client::Api::Options::SearchStoreOptions.new(labels: Set['red', 'blue']))
        request_line = captured.pop[:request_line]
        query = request_line.split[1].to_s.split('?', 2).last.to_s
        _(query.split('&').sort).must_equal(%w[labels=blue labels=red])
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe '#get_order_by_tracking_id' do
    it 'bhv_operations_124 puts the lowercase hyphenated uuid in the path' do
      api, server, thread, captured = capture_wire_request('{"id":1}')
      begin
        api.get_order_by_tracking_id('E621E1F8-C36C-495A-93FC-0C247A3E6E5F')
        request_line = captured.pop[:request_line]
        _(request_line).must_equal('GET /store/order/by-tracking/e621e1f8-c36c-495a-93fc-0c247a3e6e5f HTTP/1.1')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_111 rejects a malformed uuid before sending anything' do
      api, server, thread, captured = capture_wire_request('{"id":1}')
      begin
        err = _ { api.get_order_by_tracking_id('not-a-uuid') }.must_raise(Petstore::Client::Errors::SerializationError)
        _(err.message).must_equal('Invalid UUID for format: uuid: "not-a-uuid"')
        _(captured.empty?).must_equal(true)
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe '#get_inventory' do
    it 'bhv_operations_102 get_inventory' do
      result = @api.get_inventory

      _(result).must_be_kind_of(Hash)
    end
  end

  describe 'per-call options on an operation with no parameter and no security' do
    it 'has an Options object that carries only the per-call settings' do
      options = Petstore::Client::Api::Options::GetInventoryOptions.new(timeout: 2500, headers: { 'X-Tenant' => 'a' }, max_retries: 0)

      _(options.timeout).must_equal 2500
      _(options.headers).must_equal({ 'X-Tenant' => 'a' })
      _(options.max_retries).must_equal 0
      _(options).wont_respond_to :auth
    end

    it 'bhv_operations_096 sends the headers of the call' do
      api, server, thread, captured = capture_wire_request('{}')
      begin
        api.get_inventory(Petstore::Client::Api::Options::GetInventoryOptions.new(headers: { 'X-Tenant' => 'a' }))
        _(captured.pop[:headers]['x-tenant']).must_equal('a')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'rejects a per-call timeout that is not positive' do
      _(-> { @api.get_inventory(Petstore::Client::Api::Options::GetInventoryOptions.new(timeout: 0)) }).must_raise ArgumentError
    end

    it 'rejects a per-call retry count that is negative on the variant that returns the result' do
      options = Petstore::Client::Api::Options::GetInventoryOptions.new(max_retries: -1)

      _(-> { @api.get_inventory_with_http_info(options) }).must_raise ArgumentError
    end

    it 'still takes no argument at all' do
      _(@api.get_inventory).must_be_kind_of(Hash)
    end
  end

  describe '#place_order' do
    it 'bhv_operations_117 place_order' do
      order = Petstore::Client::Models::Order.new(
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

  describe '#get_inventory_with_http_info' do
    it 'bhv_operations_103 get_inventory_with_http_info' do
      result = @api.get_inventory_with_http_info

      _(result).wont_be_nil
      _(result.status_code).must_be :>=, 200
      _(result.status_code).must_be :<, 300
      _(result.data).wont_be_nil
    end
  end

  describe '#place_order_with_http_info' do
    it 'bhv_operations_119 place_order_with_http_info' do
      order = Petstore::Client::Models::Order.new(
        id: 1,
        pet_id: 12_345,
        quantity: 1,
        ship_date: Time.now.utc.iso8601,
        status: 'placed',
        complete: false
      )

      result = @api.place_order_with_http_info(order)

      _(result).wont_be_nil
      _(result.status_code).must_be :>=, 200
      _(result.status_code).must_be :<, 300
      _(result.data).wont_be_nil
    end
  end

  describe '#get_order_by_id' do
    it 'bhv_operations_104 get_order_by_id' do
      result = @api.get_order_by_id(1)

      _(result).wont_be_nil
      _(result.id).wont_be_nil
    end
  end

  describe '#get_order_by_id_with_http_info' do
    it 'bhv_operations_105 get_order_by_id_with_http_info' do
      result = @api.get_order_by_id_with_http_info(1)

      _(result).wont_be_nil
      _(result.status_code).must_be :>=, 200
      _(result.status_code).must_be :<, 300
      _(result.data).wont_be_nil
    end
  end

  describe '#delete_order' do
    it 'bhv_operations_099 delete_order' do
      @api.delete_order(1)
    end
  end

  describe '#get_grouped_categories' do
    def new_store_api_for_mock(status, request_content_type, body)
      server = TCPServer.new('127.0.0.1', 0) # steep:ignore UnexpectedPositionalArgument
      port = server.addr[1]
      thread = Thread.new do
        loop do
          client = begin
            server.accept
          rescue StandardError
            break
          end
          client.gets # read request line
          while (line = client.gets)
            break if line.strip.empty?
          end
          response = "HTTP/1.1 #{status} OK\r\nContent-Type: #{request_content_type}\r\n" \
                     "Content-Length: #{body.bytesize}\r\nConnection: close\r\n\r\n#{body}"
          client.print(response)
          client.close
        end
      end

      config = Petstore::Client::Configuration.new(base_url: "http://127.0.0.1:#{port}", default_headers: {})
      api = Petstore::Client::Api::StoreApi.new(nil, config)
      [api, server, thread]
    end

    # Nested generic container: Array<Hash<String, Category>>. Every leaf must
    # deserialize into a typed Category instance, not be left as a raw Hash.
    it 'bhv_operations_101 decodes nested container leaves into typed Category instances' do
      body = '[{"a":{"id":1,"name":"Dogs"}},{"b":{"id":2,"name":"Cats"}}]'
      api, server, thread = new_store_api_for_mock(200, 'application/json', body)
      begin
        result = api.get_grouped_categories

        _(result).must_be_kind_of(Array)
        _(result[0]['a']).must_be_kind_of(Petstore::Client::Models::Category)
        _(result[0]['a'].id).must_equal(1)
        _(result[0]['a'].name).must_equal('Dogs')
        _(result[1]['b']).must_be_kind_of(Petstore::Client::Models::Category)
        _(result[1]['b'].id).must_equal(2)
        _(result[1]['b'].name).must_equal('Cats')
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe 'error handling' do
    def new_store_api_for_mock(status, request_content_type, body)
      server = TCPServer.new('127.0.0.1', 0) # steep:ignore UnexpectedPositionalArgument
      port = server.addr[1]
      thread = Thread.new do
        loop do
          client = begin
            server.accept
          rescue StandardError
            break
          end
          client.gets # read request line
          while (line = client.gets)
            break if line.strip.empty?
          end
          response = "HTTP/1.1 #{status} OK\r\nContent-Type: #{request_content_type}\r\n" \
                     "Content-Length: #{body.bytesize}\r\nConnection: close\r\n\r\n#{body}"
          client.print(response)
          client.close
        end
      end

      config = Petstore::Client::Configuration.new(base_url: "http://127.0.0.1:#{port}", default_headers: {})
      api = Petstore::Client::Api::StoreApi.new(nil, config)
      [api, server, thread]
    end

    it 'bhv_operations_106 get_order_not_found' do
      api, server, thread = new_store_api_for_mock(404, 'application/json', '{"message":"Order not found"}')
      begin
        _(-> { api.get_order_by_id(99_999) }).must_raise Petstore::Client::Errors::NotFoundError
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_118 place_order_server_error' do
      api, server, thread = new_store_api_for_mock(500, 'application/json', '{"message":"Internal server error"}')
      begin
        order = Petstore::Client::Models::Order.new(
          id: 1,
          pet_id: 12_345,
          quantity: 1,
          status: 'placed',
          complete: false
        )
        _(-> { api.place_order(order) }).must_raise Petstore::Client::Errors::InternalServerError
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_100 delete_order_not_found' do
      api, server, thread = new_store_api_for_mock(404, 'application/json', '{"message":"Order not found"}')
      begin
        _(-> { api.delete_order(99_999) }).must_raise Petstore::Client::Errors::NotFoundError
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe 'YARD doc comments' do
    # The contiguous '#' block directly above 'def get_stock_item(': YARD and RDoc
    # attach nothing that sits above a blank line.
    def doc_block(name)
      root = File.expand_path('../..', File.dirname(__FILE__))
      lines = File.readlines(File.join(root, 'lib', 'petstore/client', 'api', 'store_api.rb'))
      index = lines.index { |line| line.lstrip.start_with?("def #{name}(") }
      lines.first(index.to_i).reverse.take_while { |line| line.strip.start_with?('#') }.reverse.map(&:strip)
    end

    it 'bhv_operations_112 keeps the summary and the options param attached to get_stock_item' do
      block = doc_block('get_stock_item')
      _(block.first).must_equal '# Returns a stock item exercising int-enum, non-lowercase enum and nested-container fields.'
      _(block.grep(/\A# @param options \[GetStockItemOptions\]/).size).must_equal 1
      _(block).must_include '# @raise [ApiError] if fails to make API call'
    end
  end

  # A named-enum ($ref) parameter goes on the wire as its value, never as the
  # language's representation of the constant.
  describe '#get_by_swatch' do
    it 'bhv_operations_122 sends the enum value in the path, the query and the header' do
      api, server, thread, captured = capture_wire_request('{"id":1,"name":"x"}')
      begin
        swatch = Petstore::Client::Models::Swatch
        # The options are typed as the enum class, while the values passed are
        # its string constants, which is what goes on the wire.
        options = Petstore::Client::Api::Options::GetBySwatchOptions.new(
          query_swatch: swatch::GREEN, preferred_swatch: swatch::BLUE # steep:ignore ArgumentTypeMismatch
        )
        api.get_by_swatch(swatch::RED, options)
        request = captured.pop

        _(request[:request_line]).must_equal 'GET /store/by-swatch/red?querySwatch=green HTTP/1.1'
        _(request[:headers]['preferred-swatch']).must_equal 'blue'
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_107 omits the optional query and header when they are not given' do
      api, server, thread, captured = capture_wire_request('{"id":1,"name":"x"}')
      begin
        api.get_by_swatch(Petstore::Client::Models::Swatch::BLUE)
        request = captured.pop

        _(request[:request_line]).must_equal 'GET /store/by-swatch/blue HTTP/1.1'
        _(request[:headers]).wont_include 'preferred-swatch'
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe '#create_account' do
    it 'bhv_operations_098 sends the writeOnly password and decodes the readOnly id' do
      api, server, thread, captured = capture_wire_request('{"id":9,"username":"ann"}')
      begin
        account = Petstore::Client::Models::Account.new(username: 'ann', password: 'hunter2')
        result = api.create_account(account)
        request = captured.pop

        _(request[:request_line]).must_match(%r{\APOST /store/account })
        _(request[:headers]['content-type']).must_include('application/json')
        _(JSON.parse(request[:body])).must_equal('username' => 'ann', 'password' => 'hunter2')
        _(result).must_be_kind_of(Petstore::Client::Models::Account)
        _(result.id).must_equal(9)
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'refuses a nil account before sending' do
      api, server, thread, captured = capture_wire_request('{}')
      begin
        err = _ { api.create_account(nil) }.must_raise(ArgumentError)
        _(err.message).must_equal("Missing the required parameter 'account' when calling StoreApi.create_account")
        _(captured.empty?).must_equal(true)
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe 'model responses' do
    it 'bhv_operations_116 decodes the self-referential tree from get_tree' do
      api, server, thread, = capture_wire_request('{"value":"root","child":{"value":"leaf"}}')
      begin
        tree = api.get_tree

        _(tree).must_be_kind_of(Petstore::Client::Models::TreeNode)
        _(tree.child.value).must_equal('leaf')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_108 decodes the mutually recursive models from get_department' do
      api, server, thread, = capture_wire_request('{"name":"Eng","lead":{"name":"Ann","department":{"name":"Eng"}}}')
      begin
        department = api.get_department

        _(department).must_be_kind_of(Petstore::Client::Models::Department)
        _(department.lead.department).must_be_kind_of(Petstore::Client::Models::Department)
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'decodes a 200 reserve_stock body as the existing reservation' do
      api, server, thread, = capture_wire_request('{"reservationId":31,"quantity":4}')
      begin
        result = api.reserve_stock

        _(result).must_be_kind_of(Petstore::Client::Models::Reservation)
        _(result.reservation_id).must_equal(31)
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_120 decodes a 201 reserve_stock body as the new receipt' do
      api, server, thread, = capture_wire_request(
        '{"receiptCode":"R-1","expiresAt":"2030-01-01T00:00:00Z"}', status_line: '201 Created'
      )
      begin
        result = api.reserve_stock

        _(result).must_be_kind_of(Petstore::Client::Models::ReservationReceipt)
        _(result.receipt_code).must_equal('R-1')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_123 applies the schema defaults to the model get_defaults returns' do
      api, server, thread, = capture_wire_request('{}')
      begin
        defaults = api.get_defaults

        _(defaults.retries).must_equal(3)
        _(defaults.ratio).must_equal(1.5)
        _(defaults.ceiling).must_equal(9_000_000_000)
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe 'nested container responses' do
    it 'bhv_operations_109 decodes Array<Array<Integer>> from get_matrix' do
      api, server, thread, = capture_wire_request('[[1,2],[3]]')
      begin
        _(api.get_matrix).must_equal [[1, 2], [3]]
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_114 decodes Array<Hash<String, Swatch>> from get_swatch_groups' do
      api, server, thread, = capture_wire_request('[{"a":"red"},{"b":"blue"}]')
      begin
        _(api.get_swatch_groups).must_equal [{ 'a' => 'red' }, { 'b' => 'blue' }]
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_115 decodes Array<Hash<String, Time>> from get_timestamp_groups into Time values' do
      api, server, thread, = capture_wire_request('[{"t":"2024-01-01T00:00:00Z"}]')
      begin
        result = api.get_timestamp_groups

        _(result.first['t']).must_be_kind_of Time
        _(result.first['t'].utc.iso8601).must_equal '2024-01-01T00:00:00Z'
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_113 decodes a top-level enum from get_swatch' do
      api, server, thread, = capture_wire_request('"red"')
      begin
        _(api.get_swatch).must_equal 'red'
      ensure
        server.close
        thread.join(2)
      end
    end
  end
end
