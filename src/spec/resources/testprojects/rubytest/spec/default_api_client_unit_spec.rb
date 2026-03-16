# frozen_string_literal: true

require 'minitest/autorun'
require 'json'
require 'petstore_client'

describe PetstoreClient::DefaultApiClient do
  def stub_connection(stubs)
    Faraday.new('http://localhost') { |f| f.adapter :test, stubs }
  end

  it 'sends GET request and returns response' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/echo') { [200, { 'content-type' => 'application/json' }, '{"method":"GET"}'] }
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('GET', 'http://localhost/echo', {}, nil)
      _(response.status_code).must_equal 200
      body = JSON.parse(response.body)
      _(body['method']).must_equal 'GET'
    end
    stubs.verify_stubbed_calls
  end

  it 'sends POST with JSON body' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/echo') { [200, {}, '{"method":"POST","body":"{key}"}'] }
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      headers = { 'Content-Type' => 'application/json' }
      response = client.send_request('POST', 'http://localhost/echo', headers, '{"key":"value"}')
      _(response.status_code).must_equal 200
      _(response.body).must_include 'POST'
      _(response.body).must_include 'key'
    end
    stubs.verify_stubbed_calls
  end

  it 'returns response headers' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/echo') { [200, { 'x-test-header' => 'test-value' }, 'ok'] }
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('GET', 'http://localhost/echo', {}, nil)
      _(response.headers['x-test-header']).must_equal 'test-value'
    end
    stubs.verify_stubbed_calls
  end

  it 'returns non-2xx status code' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/not-found') { [404, {}, 'not found'] }
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('GET', 'http://localhost/not-found', {}, nil)
      _(response.status_code).must_equal 404
      _(response.body).must_equal 'not found'
    end
    stubs.verify_stubbed_calls
  end

  it 'sends PUT request' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.put('/echo') { [200, {}, '{"method":"PUT"}'] }
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('PUT', 'http://localhost/echo', {}, 'update')
      _(response.status_code).must_equal 200
      _(response.body).must_include 'PUT'
    end
    stubs.verify_stubbed_calls
  end

  it 'sends DELETE request' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.delete('/echo') { [200, {}, '{"method":"DELETE"}'] }
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('DELETE', 'http://localhost/echo', {}, nil)
      _(response.status_code).must_equal 200
      _(response.body).must_include 'DELETE'
    end
    stubs.verify_stubbed_calls
  end
end
