# frozen_string_literal: true

require 'minitest/autorun'
require 'petstore_client'

describe Petstore::Client::TransportOptions do
  parallelize_me!

  it 'verify_ssl defaults to true' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.verify_ssl).must_equal true
  end

  it 'ca_cert_path defaults to null' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.ca_cert_path).must_be_nil
  end

  it 'proxy defaults to null' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.proxy).must_be_nil
  end

  it 'timeout defaults to 10000ms' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.timeout).must_equal 10_000
  end

  it 'follow_redirects defaults to true' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.follow_redirects).must_equal true
  end

  it 'max_redirects defaults to null' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.max_redirects).must_be_nil
  end

  it 'user_agent defaults to non-empty string' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.user_agent).wont_be_nil
    _(opts.user_agent).wont_be_empty
  end

  it 'user_agent defaults to the published gem name, the version and the language' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.user_agent).must_equal 'petstore_client/1.0.0 (ruby)'
    _(opts.user_agent).must_match(%r{\A[A-Za-z0-9_.-]+/\d+\.\d+\.\d+\S* \(ruby\)\z})
  end

  it 'default_headers defaults to empty map' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.default_headers).must_be_empty
  end

  it 'inject_request_id defaults to false' do
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.inject_request_id).must_equal false
  end

  it 'max_retries defaults to 2' do
    _(Petstore::Client::TransportOptions.builder.build.max_retries).must_equal 2
  end

  it 'max_retries accepts zero to turn retries off' do
    _(Petstore::Client::TransportOptions.builder.max_retries(0).build.max_retries).must_equal 0
  end

  [-1, 1.5, '2', nil].each do |bad|
    it "rejects max_retries #{bad.inspect} through the builder" do
      err = assert_raises(ArgumentError) { Petstore::Client::TransportOptions.builder.max_retries(bad) } # steep:ignore ArgumentTypeMismatch
      _(err.message).must_include 'Invalid max_retries'
    end
  end

  it 'shows max_retries in inspect' do
    _(Petstore::Client::TransportOptions.builder.max_retries(4).build.inspect).must_include 'max_retries=4'
  end

  it 'builder sets all fields' do
    opts = Petstore::Client::TransportOptions.builder
      .verify_ssl(false)
      .ca_cert_path('/path/to/ca.pem')
      .proxy('http://proxy:8080')
      .timeout(5000)
      .follow_redirects(false)
      .max_redirects(3)
      .user_agent('TestAgent/1.0')
      .default_header('X-Custom', 'value')
      .inject_request_id(true)
      .max_retries(5)
      .build

    _(opts.max_retries).must_equal 5
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

  it 'follow_redirects defaults to true with null max_redirects' do
    opts = Petstore::Client::TransportOptions.builder
      .follow_redirects(true)
      .build

    _(opts.follow_redirects).must_equal true
    _(opts.max_redirects).must_be_nil
  end

  it 'a negative timeout raises ArgumentError' do
    assert_raises(ArgumentError) { Petstore::Client::TransportOptions.builder.timeout(-1) }
    err = assert_raises(ArgumentError) { Petstore::Client::TransportOptions.builder.timeout(-5) }
    _(err.message).must_equal 'Invalid timeout: must be a positive number of milliseconds (got -5)'
    err = assert_raises(ArgumentError) { Petstore::Client::TransportOptions.builder.timeout(0) }
    _(err.message).must_equal 'Invalid timeout: must be a positive number of milliseconds (got 0)'
  end

  # An invalid proxy URL is the caller's mistake, so it raises ArgumentError, never an SDK error.
  it 'invalid proxy URL throws error' do
    error = assert_raises(ArgumentError) do
      Petstore::Client::TransportOptions.builder.proxy('not-a-url').build
    end
    _(error).must_be_instance_of ArgumentError
    _(error).wont_be_kind_of ::Petstore::Client::Errors::OpenAPIError
  end

  it 'unparseable proxy URL raises ArgumentError, not URI::InvalidURIError' do
    error = assert_raises(ArgumentError) do
      Petstore::Client::TransportOptions.builder.proxy('http://[bad').build
    end
    _(error).must_be_instance_of ArgumentError
  end

  it 'a proxy URL with an IPv6 literal host is accepted and kept verbatim' do
    opts = Petstore::Client::TransportOptions.builder.proxy('http://[::1]:3128').build
    _(opts.proxy).must_equal 'http://[::1]:3128'
  end

  it 'a proxy URL with an unterminated IPv6 literal is refused' do
    error = assert_raises(ArgumentError) do
      Petstore::Client::TransportOptions.builder.proxy('http://[::1:3128').build
    end
    _(error.message).must_match(%r{\AInvalid proxy URL \(.*\): http://\[::1:3128\z}m)
  end

  it 'constructing directly with a non-http proxy scheme raises ArgumentError' do
    error = assert_raises(ArgumentError) do
      Petstore::Client::TransportOptions.new(
        verify_ssl: true, ca_cert_path: nil, proxy: 'ftp://x', timeout: nil, follow_redirects: true,
        max_redirects: nil, user_agent: nil, default_headers: {}, inject_request_id: false
      )
    end
    _(error.message).must_equal 'Invalid proxy URL (must use http or https scheme): ftp://x'
  end

  it 'constructing directly with a proxy that has no host raises ArgumentError' do
    error = assert_raises(ArgumentError) do
      Petstore::Client::TransportOptions.new(
        verify_ssl: true, ca_cert_path: nil, proxy: 'http:///path', timeout: nil, follow_redirects: true,
        max_redirects: nil, user_agent: nil, default_headers: {}, inject_request_id: false
      )
    end
    _(error.message).must_equal 'Invalid proxy URL (missing host): http:///path'
  end

  it 'null proxy URL is accepted' do
    opts = Petstore::Client::TransportOptions.builder.proxy(nil).build
    _(opts.proxy).must_be_nil
  end

  it 'builder methods return the same builder instance' do
    builder = Petstore::Client::TransportOptions.builder

    _(builder.verify_ssl(true)).must_be_same_as builder
    _(builder.ca_cert_path(nil)).must_be_same_as builder
    _(builder.proxy(nil)).must_be_same_as builder
    _(builder.timeout(nil)).must_be_same_as builder
    _(builder.follow_redirects(true)).must_be_same_as builder
    _(builder.max_redirects(nil)).must_be_same_as builder
    _(builder.user_agent(nil)).must_be_same_as builder
    _(builder.default_header('X-Key', 'val')).must_be_same_as builder
    _(builder.default_headers({})).must_be_same_as builder
    _(builder.inject_request_id(false)).must_be_same_as builder
  end

  it 'accumulates headers from default_header calls' do
    opts = Petstore::Client::TransportOptions.builder
      .default_header('X-First', 'one')
      .default_header('X-Second', 'two')
      .build

    _(opts.default_headers.size).must_equal 2
    _(opts.default_headers['X-First']).must_equal 'one'
    _(opts.default_headers['X-Second']).must_equal 'two'
  end

  it 'merges headers from default_headers call' do
    opts = Petstore::Client::TransportOptions.builder
      .default_header('X-First', 'one')
      .default_headers({ 'X-Second' => 'two', 'X-Third' => 'three' })
      .build

    _(opts.default_headers.size).must_equal 3
    _(opts.default_headers['X-First']).must_equal 'one'
    _(opts.default_headers['X-Second']).must_equal 'two'
    _(opts.default_headers['X-Third']).must_equal 'three'
  end

  it 'modifying source map does not affect built options' do
    headers = { 'X-Original' => 'original' }

    opts = Petstore::Client::TransportOptions.builder
      .default_headers(headers)
      .build

    headers['X-Added'] = 'added'

    _(opts.default_headers.size).must_equal 1
    _(opts.default_headers['X-Original']).must_equal 'original'
    _(opts.default_headers.key?('X-Added')).must_equal false
    _(opts.default_headers).must_be :frozen?
  end

  it 'builder produces independent instances' do
    builder = Petstore::Client::TransportOptions.builder.verify_ssl(false)
    first = builder.build
    second = builder.build

    _(first.verify_ssl).must_equal second.verify_ssl
    _(first).wont_be_same_as second
  end

  # TimeoutConfigTests

  it 'timeout defaults to 10000ms' do
    # Default TransportOptions applies a 10-second timeout.
    opts = Petstore::Client::TransportOptions.builder.build
    _(opts.timeout).must_equal 10_000
  end

  it 'timeout can be explicitly set to nil for no timeout' do
    opts = Petstore::Client::TransportOptions.builder.timeout(nil).build
    _(opts.timeout).must_be_nil
  end

  it 'setting timeout to 5000 is accessible' do
    opts = Petstore::Client::TransportOptions.builder.timeout(5000).build
    _(opts.timeout).must_equal 5000
  end

  it 'timeout field is named exactly timeout' do
    # Verify via the reader that the field is named :timeout (not :open_timeout or :connection_timeout).
    opts = Petstore::Client::TransportOptions.builder.timeout(1000).build
    _(opts.timeout).wont_be_nil
    _(opts.timeout).must_equal 1000
  end

  # ProxyConfigTests

  it 'setting proxy URL is preserved on read-back' do
    opts = Petstore::Client::TransportOptions.builder
      .proxy('http://proxy.example.com:8080')
      .build
    _(opts.proxy).must_equal 'http://proxy.example.com:8080'
  end

  it 'setting proxy is supported on all platforms' do
    # Proxy configuration must not raise on any platform.
    opts = Petstore::Client::TransportOptions.builder
      .proxy('http://proxy.example.com:8080')
      .build
    _(opts.proxy).must_equal 'http://proxy.example.com:8080'
  end

  {
    'socks5://proxy.example.com:1080' => 'Invalid proxy URL (must use http or https scheme): socks5://proxy.example.com:1080',
    'ftp://proxy.example.com:21' => 'Invalid proxy URL (must use http or https scheme): ftp://proxy.example.com:21',
    'proxy.example.com:8080' => 'Invalid proxy URL (must use http or https scheme): proxy.example.com:8080',
    '' => 'Invalid proxy URL (must use http or https scheme): ',
    'http://' => 'Invalid proxy URL (missing host): http://'
  }.each do |proxy, message|
    it "rejects the proxy #{proxy.inspect} through the builder" do
      error = assert_raises(ArgumentError) { Petstore::Client::TransportOptions.builder.proxy(proxy) }
      _(error.message).must_equal message
    end
  end

  {
    'socks5://user:secret@proxy.example.com:1080' =>
      'Invalid proxy URL (must use http or https scheme): socks5://***@proxy.example.com:1080'
  }.each do |proxy, message|
    it "masks the userinfo of the proxy #{proxy.inspect} in the error" do
      error = assert_raises(ArgumentError) { Petstore::Client::TransportOptions.builder.proxy(proxy) }
      _(error.message).must_equal message
      _(error.message).wont_include 'secret'
    end
  end

  it 'accepts an upper-case proxy scheme and keeps it as given' do
    opts = Petstore::Client::TransportOptions.builder.proxy('HTTPS://proxy.example.com:8443').build
    _(opts.proxy).must_equal 'HTTPS://proxy.example.com:8443'
  end

  it 'accepts proxy credentials in the URL' do
    opts = Petstore::Client::TransportOptions.builder.proxy('http://user:pass@proxy.example.com:8080').build
    _(opts.proxy).must_equal 'http://user:pass@proxy.example.com:8080'
  end

  it 'inspect and to_s hide the proxy password and sensitive header values' do
    opts = Petstore::Client::TransportOptions.builder
                                           .proxy('http://user:hunter2@proxy.example.com:8080')
                                           .default_header('Authorization', 'Bearer abc')
                                           .default_header('Proxy-Authorization', 'Basic xyz')
                                           .default_header('Cookie', 'sid=1')
                                           .default_header('X-Api-Key', 'k1')
                                           .default_header('X-Auth-Token', 't1')
                                           .default_header('X-Client-Secret', 's1')
                                           .default_header('X-Trace', 'visible')
                                           .build
    [opts.inspect, opts.to_s].each do |text|
      %w[hunter2 abc xyz sid=1 k1 t1 s1].each { |secret| _(text).wont_include secret }
      _(text).must_include 'http://user:***@proxy.example.com:8080'
      _(text).must_include 'visible'
    end
  end

  describe 'custom connection' do
    it 'keeps the supplied connection' do
      conn = Faraday.new('http://localhost')
      _(Petstore::Client::TransportOptions.builder.connection(conn).build.connection).must_be_same_as conn
    end

    it 'defaults to no connection' do
      _(Petstore::Client::TransportOptions.builder.build.connection).must_be_nil
    end

    it 'rejects a connection combined with a proxy' do
      error = assert_raises(ArgumentError) do
        Petstore::Client::TransportOptions.builder.connection(Faraday.new('http://localhost')).proxy('http://proxy:3128').build
      end
      _(error.message).must_include 'proxy'
    end

    it 'rejects a connection combined with disabled TLS verification' do
      error = assert_raises(ArgumentError) do
        Petstore::Client::TransportOptions.builder.connection(Faraday.new('http://localhost')).verify_ssl(false).build
      end
      _(error.message).must_include 'verify_ssl'
    end

    it 'rejects a connection combined with a CA bundle' do
      error = assert_raises(ArgumentError) do
        Petstore::Client::TransportOptions.builder.connection(Faraday.new('http://localhost')).ca_cert_path('/tmp/ca.pem').build
      end
      _(error.message).must_include 'ca_cert_path'
    end

    it 'rejects an object that is not a connection' do
      error = assert_raises(ArgumentError) { Petstore::Client::TransportOptions.builder.connection(Object.new).build }
      _(error.message).must_include 'Faraday::Connection'
    end
  end
end
