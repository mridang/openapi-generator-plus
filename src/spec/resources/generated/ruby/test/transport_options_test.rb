# frozen_string_literal: true

# rubocop:disable Metrics/BlockLength, Lint/MissingCopEnableDirective

require 'minitest/autorun'
require 'petstore_client'

describe PetstoreClient::TransportOptions do
  it 'builder produces correct defaults' do
    opts = PetstoreClient::TransportOptions.builder.build

    _(opts.verify_ssl).must_equal true
    _(opts.ca_cert_path).must_be_nil
    _(opts.proxy).must_be_nil
    _(opts.timeout).must_be_nil
    _(opts.follow_redirects).must_equal true
    _(opts.max_redirects).must_be_nil
    _(opts.user_agent).must_equal 'petstore_client/1.0.0 (ruby)'
    _(opts.default_headers).must_be_empty
    _(opts.inject_request_id).must_equal false
  end

  it 'builder sets all fields' do
    opts = PetstoreClient::TransportOptions.builder
                                           .verify_ssl(false)
                                           .ca_cert_path('/path/to/ca.pem')
                                           .proxy('http://proxy:8080')
                                           .timeout(5000)
                                           .follow_redirects(false)
                                           .max_redirects(3)
                                           .user_agent('TestAgent/1.0')
                                           .default_header('X-Custom', 'value')
                                           .inject_request_id(true)
                                           .build

    _(opts.verify_ssl).must_equal false
    _(opts.ca_cert_path).must_equal '/path/to/ca.pem'
    _(opts.proxy).must_equal 'http://proxy:8080'
    _(opts.timeout).must_equal 5000
    _(opts.follow_redirects).must_equal false
    _(opts.max_redirects).must_equal 3
    _(opts.user_agent).must_equal 'TestAgent/1.0'
    _(opts.default_headers).must_equal({ 'X-Custom' => 'value' })
    _(opts.inject_request_id).must_equal true
  end

  it 'default_headers is a defensive copy' do
    headers = { 'X-Original' => 'original' }

    opts = PetstoreClient::TransportOptions.builder
                                           .default_headers(headers)
                                           .build

    headers['X-Added'] = 'added'

    _(opts.default_headers.size).must_equal 1
    _(opts.default_headers['X-Original']).must_equal 'original'
    _(opts.default_headers.key?('X-Added')).must_equal false
    _(opts.default_headers).must_be :frozen?
  end
end
