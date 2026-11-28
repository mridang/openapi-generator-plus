# frozen_string_literal: true

# Integration tests for the Store API endpoints.

require 'test_helper'
# The stdlib signature of TCPServer#initialize, (?String host, Integer port),
# puts a required parameter after an optional one, which steep reads as a
# single positional, so each TCPServer.new(host, port) below carries a
# steep:ignore for that one diagnostic.
require 'socket'

# An in-memory client that records the method, URL and body of every request. A GET
# or PATCH is answered with a canned memo, anything else with an empty reply of
# the status its operation declares.
class MemoRecordingStoreClient
  attr_reader :methods_sent, :urls, :bodies

  def initialize
    @methods_sent = []
    @urls = []
    @bodies = []
  end

  def send_request(method, url, _headers, body, **_kwargs)
    verb = method.to_s
    @methods_sent << verb
    @urls << url
    @bodies << body
    if %w[GET PATCH].include?(verb)
      return Petstore::Client::ApiHttpResponse.new(
        status_code: 200,
        body: '{"memoId":"m1"}',
        headers: { 'content-type' => 'application/json' }
      )
    end
    Petstore::Client::ApiHttpResponse.new(status_code: verb == 'HEAD' ? 200 : 204, body: '', headers: {})
  end
end

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

  # Starts a one-shot TCP server that answers +count+ requests in turn and
  # records the lower-cased headers of each, in order. The returned Queue
  # receives one Hash of headers per request.
  def capture_wire_headers(count, default_headers: {})
    server = TCPServer.new('127.0.0.1', 0) # steep:ignore UnexpectedPositionalArgument
    port = server.addr[1]
    captured = Queue.new
    thread = Thread.new do
      count.times do
        client = begin
          server.accept
        rescue StandardError
          next
        end
        client.gets # request line
        headers = {}
        while (line = client.gets)
          break if line.strip.empty?

          name, value = line.split(':', 2)
          headers[name.to_s.strip.downcase] = value.to_s.strip
        end
        captured << headers
        client.print("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" \
                     "Content-Length: 2\r\nConnection: close\r\n\r\n{}")
        client.close
      end
    end
    config = Petstore::Client::Configuration.new(base_url: "http://127.0.0.1:#{port}", default_headers: default_headers)
    [Petstore::Client::Api::StoreApi.new(nil, config), server, thread, captured]
  end

  describe '#search_store content-encoded query' do
    it 'bhv_operations_139 keeps the fractional seconds of a time parameter when non-zero' do
      api, server, thread, captured = capture_wire_request('[]')
      begin
        at = Petstore::Client::ObjectSerializer.convert_to_type('10:30:00.123', 'Tod::TimeOfDay')
        api.search_store(Petstore::Client::Api::Options::SearchStoreOptions.new(at: at))
        query = captured.pop[:request_line].split('?', 2).last.to_s.split.first
        _(URI.decode_www_form(query).to_h['at']).must_equal '10:30:00.123'
      ensure
        server.close
        thread.join(2)
      end
    end

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

    it 'bhv_operations_128 a content query parameter that cannot be serialized raises a serialization error and sends no request' do
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

  describe '#search_store query order' do
    it 'bhv_operations_155 sends the query parameters in the order the specification declares them' do
      api, server, thread, captured = capture_wire_request('[]')
      begin
        at = Petstore::Client::ObjectSerializer.convert_to_type('10:30:00', 'Tod::TimeOfDay')
        api.search_store(Petstore::Client::Api::Options::SearchStoreOptions.new(labels: Set['red', 'blue'], at: at))
        query = captured.pop[:request_line].split[1].to_s.split('?', 2).last.to_s
        _(query.split('&').map { |pair| pair.split('=', 2).first }).must_equal(%w[labels labels at])
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
    it 'bhv_langruby_011 expands a Set of labels into one pair per element' do
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

  describe '#reserve_stock' do
    it 'bhv_unions_036 a 200 body that fits only the 201 shape is refused with a serialization error' do
      api, server, thread, _captured = capture_wire_request('{"receiptCode":"rcpt","expiresAt":"2030-01-01T00:00:00Z"}')
      begin
        _ { api.reserve_stock }.must_raise(Petstore::Client::Errors::SerializationError)
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

  describe 'no-parameter operations and null bodies' do
    it 'bhv_misc_009 a call without options sends none of the per-call headers' do
      api, server, thread, captured = capture_wire_headers(1)
      begin
        api.get_inventory
        _(captured.pop).wont_include('x-tenant')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_misc_011 an options object with no parameters defaults the settings it omits' do
      options = Petstore::Client::Api::Options::GetInventoryOptions.new(timeout: 2500)

      _(options.timeout).must_equal 2500
      _(options.headers).must_be_nil
      _(options.max_retries).must_be_nil
    end

    it 'bhv_misc_013 an operation with no parameters returns its result' do
      api, server, thread, = capture_wire_request('{"available":3}')
      begin
        _(api.get_inventory['available']).must_equal 3
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_misc_014 a literal null success body raises an API error' do
      api, server, thread, = capture_wire_request('null')
      begin
        _(-> { api.get_inventory }).must_raise Petstore::Client::Errors::ApiError
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_misc_006 a bare null body through the with-http-info variant has no data and keeps the raw body' do
      api, server, thread, = capture_wire_request('null')
      begin
        result = api.get_order_by_id_with_http_info(1)
        _(result.data).must_be_nil
        _(result.raw_body).must_equal 'null'
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe 'per-call options on an operation with no parameter and no security' do
    it 'bhv_langruby_012 has an Options object that carries only the per-call settings' do
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

    it 'bhv_options_056 rejects a per-call timeout that is not positive' do
      _(-> { @api.get_inventory(Petstore::Client::Api::Options::GetInventoryOptions.new(timeout: 0)) }).must_raise ArgumentError
    end

    it 'bhv_options_053 per-call headers apply to that one call only' do
      api, server, thread, captured = capture_wire_headers(2)
      begin
        api.get_inventory(Petstore::Client::Api::Options::GetInventoryOptions.new(headers: { 'X-Tenant' => 'a' }))
        api.get_inventory
        _(captured.pop['x-tenant']).must_equal('a')
        _(captured.pop).wont_include('x-tenant')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_options_055 a call without per-call options keeps the client default headers' do
      api, server, thread, captured = capture_wire_headers(1, default_headers: { 'X-Client' => 'c' })
      begin
        api.get_inventory
        _(captured.pop['x-client']).must_equal('c')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_options_054 an operation takes its per-call options after its other arguments' do
      api, server, thread, captured = capture_wire_request('{"id":1}')
      begin
        api.place_order(nil, Petstore::Client::Api::Options::PlaceOrderOptions.new(headers: { 'X-Tenant' => 'a' }))
        _(captured.pop[:headers]['x-tenant']).must_equal('a')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_options_057 a per-call timeout below one millisecond is refused before anything is sent' do
      _(-> { @api.get_inventory(Petstore::Client::Api::Options::GetInventoryOptions.new(timeout: 0.5)) }).must_raise ArgumentError
    end

    it 'bhv_langruby_013 still takes no argument at all' do
      _(@api.get_inventory).must_be_kind_of(Hash)
    end
  end

  describe '#place_order' do
    it 'bhv_operations_126 a place_order call without an order sends no body and no content type' do
      api, server, thread, captured = capture_wire_request('{"id":1}')
      begin
        api.place_order
        sent = captured.pop
        _(sent[:body]).must_be_empty
        _(sent[:headers]).wont_include 'content-type'
      ensure
        server.close
        thread.join(2)
      end
    end

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

    it 'bhv_operations_110 picks BadRequestError for the 400 route of get_order_by_id' do
      api, server, thread = new_store_api_for_mock(400, 'application/json', '{"message":"Bad request"}')
      begin
        _(-> { api.get_order_by_id(1) }).must_raise Petstore::Client::Errors::BadRequestError
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

    it 'bhv_langruby_014 refuses a nil account before sending' do
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

    it 'bhv_operations_129 decodes a 200 reserve_stock body as the existing reservation' do
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

    it 'bhv_misc_048 a success status that no declared response row names decodes as the union' do
      api, server, thread, = capture_wire_request('{"reservationId":7,"quantity":2}', status_line: '202 Accepted')
      begin
        result = api.reserve_stock

        _(result).wont_be_nil
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_misc_114 a success response is decoded by the decoder for the status actually received' do
      api, server, thread, = capture_wire_request(
        '{"receiptCode":"R-9","expiresAt":"2030-01-01T00:00:00Z"}', status_line: '201 Created'
      )
      begin
        result = api.reserve_stock

        _(result).must_be_kind_of(Petstore::Client::Models::ReservationReceipt)
        _(result.receipt_code).must_equal('R-9')
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_misc_052 when the same per-call option is given twice the last one takes effect' do
      first = { timeout: 1000 }
      options = Petstore::Client::Api::Options::GetInventoryOptions.new(**first, timeout: 2500)

      _(options.timeout).must_equal 2500
    end

    it 'bhv_misc_054 per-call headers are a read-only copy that later changes to the callers map do not reach' do
      mine = { 'X-Tenant' => 'a' }
      options = Petstore::Client::Api::Options::GetInventoryOptions.new(headers: mine)
      mine['X-Tenant'] = 'changed'
      mine['X-Other'] = 'added'

      _(options.headers).must_equal({ 'X-Tenant' => 'a' })
      _(options.headers).must_be :frozen?
    end

    it 'bhv_misc_079 each success status decodes as the type declared for the status it arrived with' do
      api, server, thread, = capture_wire_request(
        '{"receiptCode":"R-1","expiresAt":"2030-01-01T00:00:00Z"}', status_line: '201 Created'
      )
      begin
        result = api.reserve_stock

        _(result).must_be_kind_of(Petstore::Client::Models::ReservationReceipt)
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'bhv_operations_154 a 201 body that fits the 200 schema but not its own status schema is refused' do
      api, server, thread, = capture_wire_request('{"reservationId":31,"quantity":4}', status_line: '201 Created')
      begin
        _ { api.reserve_stock }.must_raise(Petstore::Client::Errors::SerializationError)
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

  it 'bhv_streaming_028 an operation with a streaming media type gets a stream method, and an SSE or JSON Lines only operation gets no buffered method' do
    names = Petstore::Client::Api::StoreApi.public_instance_methods(false).map(&:to_s)
    %w[export_orders watch_orders watch_notices].each_case do |operation|
      _(names).must_include "#{operation}_stream"
      _(names).wont_include operation
      _(names).wont_include "#{operation}_with_http_info"
    end
    _(Petstore::Client::Api::StoreApi.instance_method(:export_orders_stream).parameters.map(&:last)).must_include :stream_options
  end

  describe 'memo operations' do
    def new_memo_recording_store_api
      client = MemoRecordingStoreClient.new
      config = Petstore::Client::Configuration.new(base_url: 'http://127.0.0.1:1', default_headers: {})
      [Petstore::Client::Api::StoreApi.new(client, config), client]
    end

    it 'bhv_servers_031 an operation under a path item that declares its own servers is sent to the server chosen from them' do
      api, client = new_memo_recording_store_api

      memo = api.get_memo('m1', server: Petstore::Client::Api::GetMemoServerMemos.new)

      _(memo.memo_id).must_equal 'm1'
      _(client.urls.length).must_equal 1
      _(client.urls.first).must_match(%r{\Ahttps://memos\.example\.com/v1/store/memos/m1})
    end

    it 'bhv_operations_166 a PATCH, HEAD, OPTIONS or TRACE operation is sent with its own HTTP method' do
      api, client = new_memo_recording_store_api

      api.patch_memo('m1', Petstore::Client::Models::Memo.new(memo_id: 'm1'))
      api.head_memo('m1')
      api.options_memo('m1')
      api.trace_memo('m1')

      _(client.methods_sent).must_equal %w[PATCH HEAD OPTIONS TRACE]
    end
  end

  describe 'constraint validation' do
    def new_validating_store_api
      client = MemoRecordingStoreClient.new
      config = Petstore::Client::Configuration.builder
        .base_url('http://127.0.0.1:1')
        .validate_constraints(true)
        .build
      [Petstore::Client::Api::StoreApi.new(client, config), client]
    end

    it 'bhv_models_706 sends a body that breaks its constraints unchanged when validation is left at its default' do
      client = MemoRecordingStoreClient.new
      config = Petstore::Client::Configuration.builder.base_url('http://127.0.0.1:1').build
      api = Petstore::Client::Api::StoreApi.new(client, config)

      api.submit_allowance(Petstore::Client::Models::Allowance.new(label: 'a', count: 0))

      _(config.validate_constraints).must_equal false
      _(client.methods_sent).must_equal ['POST']
      _(JSON.parse(client.bodies.first.to_s)).must_equal({ 'label' => 'a', 'count' => 0 })
    end

    {
      'minLength' => [{ label: 'a' }, 'label must be at least 2 characters long'],
      'maxLength' => [{ label: 'abcdef' }, 'label must be at most 5 characters long'],
      'pattern' => [{ label: 'AB' }, 'label must match the pattern ^[a-z]+$'],
      'minimum' => [{ count: 0 }, 'count must be greater than or equal to 1'],
      'maximum' => [{ count: 11 }, 'count must be less than or equal to 10'],
      'exclusiveMinimum' => [{ ratio: 0.0 }, 'ratio must be greater than 0'],
      'exclusiveMaximum' => [{ ratio: 1.0 }, 'ratio must be less than 1'],
      'multipleOf' => [{ step: 7 }, 'step must be a multiple of 5'],
      'minItems' => [{ codes: [] }, 'codes must have at least 1 items'],
      'maxItems' => [{ codes: %w[a b c d] }, 'codes must have at most 3 items'],
      'minProperties' => [{ limits: {} }, 'limits must have at least 1 properties'],
      'maxProperties' => [{ limits: { 'a' => 1, 'b' => 2, 'c' => 3 } }, 'limits must have at most 2 properties']
    }.each do |keyword, (attributes, message)|
      it "bhv_models_707 refuses a body that breaks #{keyword} before it is sent" do
        api, client = new_validating_store_api

        error = _ { api.submit_allowance(Petstore::Client::Models::Allowance.new(**attributes)) }.must_raise ArgumentError

        _(error.class).must_equal ArgumentError
        _(error.message).must_equal "Invalid request body: #{message}"
        _(client.methods_sent).must_be_empty
      end
    end

    {
      'tier.level must be greater than or equal to 0' =>
        { tier: Petstore::Client::Models::AllowanceTier.new(level: -1) },
      'tier.name must be at most 3 characters long' =>
        { tier: Petstore::Client::Models::AllowanceTier.new(name: 'abcd') },
      'parts[1].level must be greater than or equal to 0' =>
        { parts: [Petstore::Client::Models::AllowanceTier.new(level: 0), Petstore::Client::Models::AllowanceTier.new(level: -1)] }
    }.each do |message, attributes|
      it "bhv_models_708 names the path of a violation inside a held model: #{message}" do
        api, client = new_validating_store_api

        error = _ { api.submit_allowance(Petstore::Client::Models::Allowance.new(**attributes)) }.must_raise ArgumentError

        _(error.class).must_equal ArgumentError
        _(error.message).must_equal "Invalid request body: #{message}"
        _(client.methods_sent).must_be_empty
      end
    end

    it 'bhv_models_709 sends a body within every constraint and does not check null or absent values' do
      api, client = new_validating_store_api
      valid = Petstore::Client::Models::Allowance.new(
        label: 'abc', count: 10, ratio: 0.5, step: 15, codes: %w[a b c], limits: { 'a' => 1, 'b' => 2 },
        tier: Petstore::Client::Models::AllowanceTier.new(level: 0, name: 'abc'),
        parts: [Petstore::Client::Models::AllowanceTier.new(level: 1), Petstore::Client::Models::AllowanceTier.new(name: nil)]
      )

      api.submit_allowance(valid)
      api.submit_allowance(Petstore::Client::Models::Allowance.new(label: nil, tier: Petstore::Client::Models::AllowanceTier.new))

      _(client.methods_sent).must_equal %w[POST POST]
    end
  end
end
