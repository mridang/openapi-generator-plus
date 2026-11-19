# frozen_string_literal: true

require 'json'
require 'openssl'
require 'tempfile'
require 'test_helper'

describe Petstore::Client::DefaultApiClient do
  parallelize_me!

  describe 'TLS verification disabled' do
    it 'bhv_misc_266 caller default headers replace the SDK user agent and request id on the wire regardless of header name case' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .user_agent('SdkDefault/9.9')
        .inject_request_id(true)
        .default_header('user-agent', 'MyApp')
        .default_header('x-request-id', 'abc')
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['headers']['user-agent']).must_equal('MyApp')
      _(json['headers']['x-request-id']).must_equal('abc')
      _(response.body).wont_include('SdkDefault')
    end

    it 'bhv_misc_271 with no proxy configured the client connects directly even when a proxy environment variable is set' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      dead = %w[HTTP_PROXY HTTPS_PROXY http_proxy https_proxy ALL_PROXY all_proxy]
      bypass = %w[NO_PROXY no_proxy]
      saved = {} # : Hash[String, String?]
      (dead + bypass).each { |name| saved[name] = ENV.fetch(name, nil) }
      dead.each { |name| ENV[name] = 'http://127.0.0.1:1' }
      bypass.each { |name| ENV.delete(name) }

      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.build)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
    ensure
      saved&.each { |name, value| ENV[name] = value }
    end

    it 'bhv_misc_272 an authenticating proxy accepts the credentials carried in the proxy URL end to end' do
      chasm_url = ENV.fetch('CHASM_INTERNAL_HTTP_URL')
      transport = Petstore::Client::TransportOptions.builder
        .proxy("http://user:pass@#{ENV.fetch('PROXY_AUTH_HOST_PORT')}")
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      _(JSON.parse(response.body)['method']).must_equal('GET')
    end

    it 'bhv_misc_269 a self-signed endpoint is reached with verification disabled and rejected with it on' do
      chasm_url = ENV.fetch('CHASM_HTTPS_URL')
      off = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.verify_ssl(false).build)
      _(off.send_request(:GET, "#{chasm_url}/test/echo", {}, nil).status_code).must_equal(200)

      on = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.build)
      _(proc { on.send_request(:GET, "#{chasm_url}/test/echo", {}, nil) }).must_raise Petstore::Client::Errors::NetworkError
    end

    it 'bhv_misc_270 a missing CA certificate file is rejected even with verification disabled' do
      transport = Petstore::Client::TransportOptions.builder.verify_ssl(false).ca_cert_path('/nonexistent/missing-ca.pem').build
      err = assert_raises(StandardError) do
        Petstore::Client::DefaultApiClient.new(transport).send_request(:GET, 'http://localhost/echo', {}, nil)
      end
      _(err.message).must_include 'missing-ca.pem'
    end

    it 'bhv_transport_018 makes HTTPS request with verify_ssl=false' do
      chasm_url = ENV.fetch('CHASM_HTTPS_URL')

      transport = Petstore::Client::TransportOptions.builder
        .verify_ssl(false)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
    end

    it 'bhv_transport_062 with verify_ssl false accepts a certificate that fails validation' do
      chasm_url = ENV.fetch('CHASM_HTTPS_URL')

      transport = Petstore::Client::TransportOptions.builder
        .verify_ssl(false)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
    end
  end

  describe 'custom CA bundle' do
    it 'bhv_transport_017 makes HTTPS request with custom CA cert' do
      chasm_url = ENV.fetch('CHASM_HTTPS_URL')
      ca_cert_path = ENV.fetch('CA_CERT_PATH')

      transport = Petstore::Client::TransportOptions.builder
        .verify_ssl(true)
        .ca_cert_path(ca_cert_path)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
    end
  end

  describe 'TLS verification enabled' do
    it 'bhv_misc_038 with no CA certificate configured, server certificates are verified against the platform trust store' do
      client = Petstore::Client::DefaultApiClient.new
      err = assert_raises(Petstore::Client::Errors::NetworkError) do
        client.send_request(:GET, "#{ENV.fetch('CHASM_HTTPS_URL')}/test/echo", {}, nil)
      end

      _(err.status_code).must_equal(0)
      _(err.cause).must_be_kind_of(Faraday::SSLError)
    end

    it 'bhv_transport_025 rejects a server certificate signed by a CA the system does not trust' do
      client = Petstore::Client::DefaultApiClient.new
      err = assert_raises(Petstore::Client::Errors::NetworkError) do
        client.send_request(:GET, "#{ENV.fetch('CHASM_HTTPS_URL')}/test/echo", {}, nil)
      end

      _(err.status_code).must_equal(0)
      _(err.cause).must_be_kind_of(Faraday::SSLError)
    end

    it 'bhv_transport_093 a server certificate signed by another CA is rejected with a network error' do
      key = OpenSSL::PKey::RSA.new(2048)
      other_ca = OpenSSL::X509::Certificate.new
      other_ca.version = 2
      other_ca.serial = 1
      other_ca.subject = other_ca.issuer = OpenSSL::X509::Name.parse('/CN=unrelated-test-ca')
      other_ca.public_key = key.public_key
      other_ca.not_before = Time.now - 60
      other_ca.not_after = Time.now + 3600
      other_ca.sign(key, OpenSSL::Digest.new('SHA256')) # steep:ignore ArgumentTypeMismatch
      file = Tempfile.new(['other-ca', '.pem'])
      file.write(other_ca.to_pem)
      file.close

      transport = Petstore::Client::TransportOptions.builder.verify_ssl(true).ca_cert_path(file.path).build
      client = Petstore::Client::DefaultApiClient.new(transport)
      err = assert_raises(Petstore::Client::Errors::NetworkError) do
        client.send_request(:GET, "#{ENV.fetch('CHASM_HTTPS_URL')}/test/echo", {}, nil)
      end

      _(err.status_code).must_equal(0)
      _(err.cause).must_be_kind_of(Faraday::SSLError)
    ensure
      file&.unlink
    end

    it 'bhv_transport_023 rejects a server certificate the configured CA did not sign' do
      key = OpenSSL::PKey::RSA.new(2048)
      other_ca = OpenSSL::X509::Certificate.new
      other_ca.version = 2
      other_ca.serial = 1
      other_ca.subject = other_ca.issuer = OpenSSL::X509::Name.parse('/CN=unrelated-test-ca')
      other_ca.public_key = key.public_key
      other_ca.not_before = Time.now - 60
      other_ca.not_after = Time.now + 3600
      other_ca.sign(key, OpenSSL::Digest.new('SHA256')) # steep:ignore ArgumentTypeMismatch
      file = Tempfile.new(['other-ca', '.pem'])
      file.write(other_ca.to_pem)
      file.close

      transport = Petstore::Client::TransportOptions.builder.verify_ssl(true).ca_cert_path(file.path).build
      client = Petstore::Client::DefaultApiClient.new(transport)
      err = assert_raises(Petstore::Client::Errors::NetworkError) do
        client.send_request(:GET, "#{ENV.fetch('CHASM_HTTPS_URL')}/test/echo", {}, nil)
      end

      _(err.status_code).must_equal(0)
      _(err.cause).must_be_kind_of(Faraday::SSLError)
    ensure
      file&.unlink
    end
  end

  describe 'HTTP proxy' do
    it 'bhv_transport_015 makes HTTP request through proxy' do
      chasm_url = ENV.fetch('CHASM_INTERNAL_HTTP_URL')
      proxy_url = ENV.fetch('PROXY_URL')

      transport = Petstore::Client::TransportOptions.builder
        .proxy(proxy_url)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
    end

    it 'bhv_transport_009 ignores the ambient proxy environment when no proxy is configured' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      dead = %w[HTTP_PROXY HTTPS_PROXY http_proxy https_proxy ALL_PROXY all_proxy]
      bypass = %w[NO_PROXY no_proxy]
      saved = {} # : Hash[String, String?]
      (dead + bypass).each { |name| saved[name] = ENV.fetch(name, nil) }
      dead.each { |name| ENV[name] = 'http://127.0.0.1:1' }
      bypass.each { |name| ENV.delete(name) }

      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.build)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
    ensure
      saved&.each { |name, value| ENV[name] = value }
    end
  end

  describe 'proxy with credentials' do
    # The fixture proxy's second port answers 407 unless the request carries
    # Proxy-Authorization, so these two prove the credentials in the proxy URL
    # reach the proxy on the wire.
    it 'bhv_transport_001 authenticating proxy accepts the credentials from the proxy URL' do
      chasm_url = ENV.fetch('CHASM_INTERNAL_HTTP_URL')
      transport = Petstore::Client::TransportOptions.builder
        .proxy("http://user:pass@#{ENV.fetch('PROXY_AUTH_HOST_PORT')}")
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      _(JSON.parse(response.body)['method']).must_equal('GET')
    end

    it 'bhv_transport_059 authenticating proxy refuses a request without credentials' do
      chasm_url = ENV.fetch('CHASM_INTERNAL_HTTP_URL')
      transport = Petstore::Client::TransportOptions.builder
        .proxy("http://#{ENV.fetch('PROXY_AUTH_HOST_PORT')}")
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(407)
    end

    it 'bhv_transport_063 authenticating proxy answers 407 when no credentials are sent' do
      chasm_url = ENV.fetch('CHASM_INTERNAL_HTTP_URL')
      transport = Petstore::Client::TransportOptions.builder
        .proxy("http://#{ENV.fetch('PROXY_AUTH_HOST_PORT')}")
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(407)
    end

    # Gap AK: userinfo embedded in the proxy URL must be base64-encoded
    # and surfaced as Proxy-Authorization so the proxy can authenticate
    # the tunnel — otherwise the proxy 407s. Faraday reads userinfo
    # natively from the URL; we assert TransportOptions preserves the
    # userinfo end-to-end.
    it 'bhv_transport_021 proxy_with_credentials_injects_basic_authorization' do
      require 'base64'
      require 'uri'

      transport = Petstore::Client::TransportOptions.builder
        .proxy('http://alice:s3cret@127.0.0.1:3128')
        .build

      uri = URI.parse(transport.proxy.to_s)
      _(uri.user).must_equal('alice')
      _(uri.password).must_equal('s3cret')
      user = URI.decode_www_form_component(uri.user.to_s)
      pass = URI.decode_www_form_component(uri.password.to_s)
      encoded = Base64.strict_encode64("#{user}:#{pass}")
      _("Basic #{encoded}").must_equal('Basic YWxpY2U6czNjcmV0')
    end

    # Gap AK — canonical cross-SDK scenario: a proxy URL of the exact form
    # `http://user:pass@127.0.0.1:3128` MUST carry its userinfo credentials
    # through to the client's proxy configuration (Proxy-Authorization /
    # userinfo honoured), not drop them. Java was the only SDK that dropped
    # the userinfo (java.net.http.HttpClient strips it); ruby preserves it —
    # TransportOptions keeps the userinfo verbatim and Faraday parses
    # user/password from the proxy URL. Canonical = creds carried.
    it 'bhv_misc_031 a proxy credential is sent exactly when the proxy URL includes user information' do
      require 'base64'

      transport = Petstore::Client::TransportOptions.builder
        .proxy('http://user:pass@127.0.0.1:3128')
        .build

      # The userinfo survives in TransportOptions unmodified.
      _(transport.proxy).must_equal('http://user:pass@127.0.0.1:3128')

      # Faraday parses the userinfo from the proxy URL when the connection
      # is built — the credentials are honoured, not dropped.
      client = Petstore::Client::DefaultApiClient.new(transport)
      conn = client.send(:build_connection)
      _(conn.proxy).wont_be_nil
      _(conn.proxy.user).must_equal('user')
      _(conn.proxy.password).must_equal('pass')

      # And they encode to the canonical Proxy-Authorization Basic value.
      encoded = Base64.strict_encode64('user:pass')
      _("Basic #{encoded}").must_equal('Basic dXNlcjpwYXNz')
    end

    it 'bhv_transport_020 carries user:pass userinfo from the canonical proxy URL' do
      require 'base64'

      transport = Petstore::Client::TransportOptions.builder
        .proxy('http://user:pass@127.0.0.1:3128')
        .build

      # The userinfo survives in TransportOptions unmodified.
      _(transport.proxy).must_equal('http://user:pass@127.0.0.1:3128')

      # Faraday parses the userinfo from the proxy URL when the connection
      # is built — the credentials are honoured, not dropped.
      client = Petstore::Client::DefaultApiClient.new(transport)
      conn = client.send(:build_connection)
      _(conn.proxy).wont_be_nil
      _(conn.proxy.user).must_equal('user')
      _(conn.proxy.password).must_equal('pass')

      # And they encode to the canonical Proxy-Authorization Basic value.
      encoded = Base64.strict_encode64('user:pass')
      _("Basic #{encoded}").must_equal('Basic dXNlcjpwYXNz')
    end
  end

  describe 'HTTP proxy with TLS' do
    it 'bhv_transport_016 makes HTTPS request through proxy with verify_ssl=false' do
      chasm_url = ENV.fetch('CHASM_INTERNAL_HTTPS_URL')
      proxy_url = ENV.fetch('PROXY_URL')

      transport = Petstore::Client::TransportOptions.builder
        .proxy(proxy_url)
        .verify_ssl(false)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
    end
  end

  describe 'request timeout' do
    it 'bhv_transport_086 a request that exceeds its timeout fails with the network timeout error within the budget' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .timeout(1000)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      error = _ { client.send_request(:GET, "#{chasm_url}/test/slow", {}, nil) }.must_raise Petstore::Client::Errors::NetworkTimeoutError
      _(error.status_code).must_equal 0
      _(error.cause).wont_be_nil
    end

    it 'bhv_transport_024 times out on slow endpoint' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .timeout(1000)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      error = _ { client.send_request(:GET, "#{chasm_url}/test/slow", {}, nil) }.must_raise Petstore::Client::Errors::NetworkTimeoutError
      _(error.status_code).must_equal 0
      _(error.cause).wont_be_nil
    end

    it 'bhv_transport_060 cancelling an in-flight call aborts the request without reporting a timeout' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.timeout(30_000).build)
      cancelled = Class.new(StandardError)

      worker = Thread.new { client.send_request(:GET, "#{chasm_url}/test/slow", {}, nil) }
      worker.report_on_exception = false
      sleep 0.3
      worker.raise(cancelled.new('cancelled by the caller'))

      _(assert_raises(cancelled) { worker.join(10) }.message).must_equal 'cancelled by the caller'
      _(worker.alive?).must_equal false
    end

    it 'bhv_redirects_054 a redirect chain past the timeout budget fails and the budget is shared across hops' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .timeout(1200)
        .follow_redirects(true)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      error = _ { client.send_request(:GET, "#{chasm_url}/test/redirect/chain/1", {}, nil) }.must_raise Petstore::Client::Errors::NetworkTimeoutError
      _(error.status_code).must_equal 0
    end

    it 'bhv_redirects_009 spends one budget across every redirect hop' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .timeout(1200)
        .follow_redirects(true)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      error = _ { client.send_request(:GET, "#{chasm_url}/test/redirect/chain/1", {}, nil) }.must_raise Petstore::Client::Errors::NetworkTimeoutError
      _(error.status_code).must_equal 0
    end

    it 'bhv_redirects_002 completes the redirect chain within a sufficient budget' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .timeout(5000)
        .follow_redirects(true)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/redirect/chain/1", {}, nil)
      _(response.status_code).must_equal(200)
    end
  end

  describe 'HTTP method and body' do
    it 'bhv_misc_041 a request with an HTTP method outside the standard set is sent with its body intact' do
      server = TCPServer.new('127.0.0.1', 0)
      seen = []
      thread = Thread.new do
        conn = server.accept
        head = +''
        head << conn.gets until head.end_with?("\r\n\r\n")
        length = head[/content-length: *(\d+)/i, 1].to_i
        seen = [head.lines.first.split.first, conn.read(length)]
        conn.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok")
        conn.close
      end

      client = Petstore::Client::DefaultApiClient.new(nil)
      response = client.send_request(:PURGE, "http://127.0.0.1:#{server.addr[1]}/test", { 'Content-Type' => 'text/plain' }, 'payload')
      thread.join(5)

      _(response.status_code).must_equal(200)
      _(seen).must_equal(%w[PURGE payload])
    ensure
      server&.close
    end
  end

  describe 'User-Agent header' do
    it 'bhv_transport_012 injects custom User-Agent header' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .user_agent('MyApp/1.0')
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['headers']['user-agent']).must_equal('MyApp/1.0')
    end

    it 'bhv_transport_003 keeps a lower-case caller User-Agent and X-Request-ID instead of injecting' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .user_agent('SdkDefault/9.9')
        .inject_request_id(true)
        .default_header('user-agent', 'MyApp')
        .default_header('x-request-id', 'abc')
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['headers']['user-agent']).must_equal('MyApp')
      _(json['headers']['x-request-id']).must_equal('abc')
      _(response.body).wont_include('SdkDefault')
    end
  end

  describe 'X-Request-ID injection' do
    it 'bhv_transport_014 injects X-Request-ID header with UUID format' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .inject_request_id(true)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      request_id = json['headers']['x-request-id']
      _(request_id).wont_be_nil
      _(request_id).must_match(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)
    end

    it 'bhv_transport_008 generates unique X-Request-ID per request' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .inject_request_id(true)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)

      response1 = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)
      request_id1 = JSON.parse(response1.body)['headers']['x-request-id']

      response2 = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)
      request_id2 = JSON.parse(response2.body)['headers']['x-request-id']

      _(request_id1).wont_equal(request_id2)
    end
  end

  describe 'default headers' do
    it 'bhv_transport_011 includes transport-level default headers' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .default_header('X-Custom', 'custom-value')
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['headers']['x-custom']).must_equal('custom-value')
    end

    it 'bhv_misc_050 per-call options override the options object and an unset option falls through to it' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .default_header('Accept', 'text/plain')
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(
        :GET, "#{chasm_url}/test/echo",
        { 'Accept' => 'application/json' }, nil
      )

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['headers']['accept']).must_equal('application/json')
    end

    it 'bhv_options_004 caller headers override transport default headers' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .default_header('Accept', 'text/plain')
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(
        :GET, "#{chasm_url}/test/echo",
        { 'Accept' => 'application/json' }, nil
      )

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['headers']['accept']).must_equal('application/json')
    end
  end

  describe 'redirect handling' do
    it 'bhv_redirects_004 follows redirects when enabled' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .follow_redirects(true)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/redirect/302", {}, nil)

      _(response.status_code).must_equal(200)
    end

    it 'bhv_redirects_007 returns redirect response when disabled' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .follow_redirects(false)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/redirect/302", {}, nil)

      _(response.status_code).must_equal(302)
    end

    it 'bhv_redirects_005 redirect 303 switches to GET and drops body' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      response = client.send_request(
        :POST,
        "#{chasm_url}/test/redirect/303",
        { 'Content-Type' => 'application/json' },
        'hello-body'
      )

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
      _(json['body']).must_equal('')
    end

    # T-new-3: multipart bodies must be replayed across 307 redirects per
    # RFC 7231 §6.4.7 / RFC 7538. Regression test: ensure the follow-up
    # request after a 307 still carries the multipart form parts.
    it 'bhv_retries_046 a request body is resent unchanged when a 307 redirect keeps the same scheme' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      boundary = 'test-boundary'
      multipart_body = "--#{boundary}\r\n" \
                       "Content-Disposition: form-data; name=\"description\"\r\n\r\n" \
                       "hello\r\n" \
                       "--#{boundary}\r\n" \
                       "Content-Disposition: form-data; name=\"file\"; filename=\"file\"\r\n" \
                       "Content-Type: application/octet-stream\r\n\r\n" \
                       "file-content-bytes\r\n" \
                       "--#{boundary}--\r\n"
      headers = { 'Content-Type' => "multipart/form-data; boundary=#{boundary}" }
      # chasm echoes the replayed request after the 307; we verify the
      # method stayed POST and the multipart body was carried through.
      response = client.send_request(:POST, "#{chasm_url}/test/redirect/307-multipart", headers, multipart_body)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('POST')
      _(json['body']).wont_be_nil
      _(json['body']).wont_be_empty
    end

    it 'bhv_retries_012 replays multipart body across 307 redirects (T-new-3)' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      boundary = 'test-boundary'
      multipart_body = "--#{boundary}\r\n" \
                       "Content-Disposition: form-data; name=\"description\"\r\n\r\n" \
                       "hello\r\n" \
                       "--#{boundary}\r\n" \
                       "Content-Disposition: form-data; name=\"file\"; filename=\"file\"\r\n" \
                       "Content-Type: application/octet-stream\r\n\r\n" \
                       "file-content-bytes\r\n" \
                       "--#{boundary}--\r\n"
      headers = { 'Content-Type' => "multipart/form-data; boundary=#{boundary}" }
      # chasm echoes the replayed request after the 307; we verify the
      # method stayed POST and the multipart body was carried through.
      response = client.send_request(:POST, "#{chasm_url}/test/redirect/307-multipart", headers, multipart_body)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('POST')
      _(json['body']).wont_be_nil
      _(json['body']).wont_be_empty
    end

    # WAVE E parity: redirect counting and the "too many redirects" refusal
    # must be scoped to a SINGLE request, never shared on the client. Two
    # requests issued CONCURRENTLY through one shared client must each reach
    # their own independent outcome: a request that follows a redirect chain
    # must not be failed by, or inflate the hop count of, a sibling request,
    # and a sibling that does not redirect must complete cleanly. In this
    # SDK the redirect loop keeps its hop counter (`hops`) as a local
    # variable inside #send_request, so there is no shared state to corrupt
    # — this test confirms that and guards against a regression.
    it 'bhv_redirects_003 scopes redirect counting per request across concurrent calls' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = Petstore::Client::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      # One shared client used by every thread below.
      client = Petstore::Client::DefaultApiClient.new(transport)

      # Mix requests that follow a redirect with requests that do not, all
      # in flight at the same time on the same client. Each thread records
      # its own result so we can assert independence after the join.
      redirecting = Array.new(4) do
        Thread.new do
          client.send_request(:GET, "#{chasm_url}/test/redirect/302", {}, nil)
        end
      end
      direct = Array.new(4) do
        Thread.new do
          client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)
        end
      end

      # A request that follows exactly one redirect must land on 200; a
      # sibling's redirects must not push it over the limit.
      redirecting.each do |t|
        response = t.value
        _(response.status_code).must_equal(200)
      end
      # A request that follows no redirects must complete on its own; it
      # must not inherit a hop count from a concurrent redirecting sibling.
      direct.each do |t|
        response = t.value
        _(response.status_code).must_equal(200)
        json = JSON.parse(response.body)
        _(json['method']).must_equal('GET')
      end
    end

    # WAVE E parity: a "too many redirects" refusal raised by one request
    # must surface ONLY on that request and never leak onto a concurrent
    # sibling. With max_redirects(0) every redirecting request is refused at
    # once, while a non-redirecting request issued on the same client at the
    # same time must still succeed — proving the refusal error is scoped to
    # the calling request, not stored on the client.
    it 'bhv_redirects_047 scopes the too-many-redirects refusal per request across concurrent calls' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      # follow_redirects=true but max_redirects=0: the server still answers
      # /test/redirect/302 with a 3xx, so every redirecting request must be
      # refused with an ApiError, while /test/echo never redirects.
      transport = Petstore::Client::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(0)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)

      # Each redirecting thread must raise; capture the exception per thread.
      refused = Array.new(4) do
        Thread.new do
          client.send_request(:GET, "#{chasm_url}/test/redirect/302", {}, nil)
          nil
        rescue Petstore::Client::Errors::ApiError => e
          e
        end
      end
      # Each non-redirecting thread must succeed despite the concurrent
      # refusals; the refusal error must not surface here.
      ok = Array.new(4) do
        Thread.new do
          client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)
        end
      end

      refused.each do |t|
        error = t.value
        _(error).wont_be_nil
        _(error.message.downcase).must_match(/redirect/)
      end
      ok.each do |t|
        response = t.value
        _(response.status_code).must_equal(200)
        json = JSON.parse(response.body)
        _(json['method']).must_equal('GET')
      end
    end
  end

  describe 'max redirects' do
    it 'bhv_redirects_006 respects max_redirects limit' do
      transport = Petstore::Client::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      client = Petstore::Client::DefaultApiClient.new(transport)
      _(client).wont_be_nil
      _(transport.max_redirects).must_equal(5)
    end
  end

  describe 'multipart body' do
    it 'bhv_serializationwire_027 sends multipart form data' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      client = Petstore::Client::DefaultApiClient.new
      form_data = { 'description' => 'A test file', 'file' => 'file content' }
      response = client.send_request(:POST, "#{chasm_url}/test/echo", {}, form_data)

      _(response).wont_be_nil
    end

    # W-new-2: multipart field-name validation must run on every branch (not
    # just binary). Confirm that even for a plain String value, a CR/LF in
    # the field name is rejected, preventing Content-Disposition smuggling.
    it 'bhv_serializationwire_026 multipart_field_name_with_crlf_rejected_on_string_value' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      client = Petstore::Client::DefaultApiClient.new
      assert_raises(ArgumentError) do
        client.send_request(
          :POST,
          "#{chasm_url}/test/echo",
          {},
          { "name\r\nInjected: yes" => 'string-value' }
        )
      end
    end
  end

  describe 'HTTP compression' do
    # The fixture really answers with the requested Content-Encoding, so the
    # decoded body and the dropped encoding header together show the decoder ran.
    def assert_decodes(encoding)
      client = Petstore::Client::DefaultApiClient.new
      response = client.send_request(
        :GET, "#{ENV.fetch('CHASM_HTTP_URL')}/test/compressed/#{encoding}",
        { 'Accept-Encoding' => encoding }, nil
      )

      _(response.status_code).must_equal(200)
      _(response.headers.key?('content-encoding')).must_equal(false)
      _(response.headers.key?('content-length')).must_equal(false)
      _(JSON.parse(response.body)['title']).must_equal('compressed fixture body')
    end

    it 'bhv_transport_088 a validly gzip-encoded response body is decoded before it is returned' do
      assert_decodes('gzip')
    end

    it 'bhv_misc_039 a well-formed gzip stream whose decoded content is empty or zero is returned as that content' do
      assert_decodes('gzip')
    end

    it 'bhv_transport_005 decompresses gzip response' do
      assert_decodes('gzip')
    end

    it 'bhv_transport_004 decompresses brotli response' do
      skip 'brotli gem not installed (optional group)' unless defined?(Brotli)
      assert_decodes('br')
    end

    it 'bhv_transport_006 decompresses zstd response' do
      skip 'zstd-ruby gem not installed (optional group)' unless defined?(Zstd)
      assert_decodes('zstd')
    end
  end

  describe 'null-body Content-Length' do
    # Regression: POST/PUT/PATCH with body == nil must emit an explicit
    # Content-Length: 0. Some servers / WAFs reject body-bearing verbs
    # with no Content-Length (411 Length Required). The client sends an
    # empty body and Content-Length: 0 explicitly on body-bearing verbs.
    it 'bhv_misc_037 a response body that is not decoded keeps its Content-Length header' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      client = Petstore::Client::DefaultApiClient.new
      response = client.send_request(:POST, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['contentLength']).must_equal(0)
    end

    it 'bhv_transport_019 post_with_null_body_sends_content_length_zero' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      client = Petstore::Client::DefaultApiClient.new
      response = client.send_request(:POST, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['contentLength']).must_equal(0)
    end
  end

  describe 'response shapes through the mock server' do
    it 'bhv_transport_090 response headers repeated on the wire are returned with every value present' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      response = Petstore::Client::DefaultApiClient.new.send_request(:GET, "#{chasm_url}/test/response-headers", {}, nil)

      _(response.headers['x-custom-header']).must_equal('foo')
      link = response.headers['link']
      _(link.index('rel="next"')).must_be :<, link.index('rel="prev"')
      cookies = response.headers['set-cookie']
      _(cookies.index('a=1')).must_be :<, cookies.index('b=2')
    end

    it 'bhv_transport_022 keeps every value of a repeated response header, in order' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      response = Petstore::Client::DefaultApiClient.new.send_request(:GET, "#{chasm_url}/test/response-headers", {}, nil)

      _(response.headers['x-custom-header']).must_equal('foo')
      link = response.headers['link']
      _(link.index('rel="next"')).must_be :<, link.index('rel="prev"')
      cookies = response.headers['set-cookie']
      _(cookies.index('a=1')).must_be :<, cookies.index('b=2')
    end

    it 'bhv_transport_067 returns a 200 without a body as an empty body, not an error' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      response = Petstore::Client::DefaultApiClient.new.send_request(:GET, "#{chasm_url}/test/empty", {}, nil)

      _(response.status_code).must_equal(200)
      _(response.body.to_s).must_be_empty
    end
  end

  describe 'client lifecycle (Gap T6)' do
    # Gap T6: #close releases the underlying connection and is idempotent.
    # A request issued on a closed client must raise the invalid-state
    # RuntimeError (closed-flag guard) rather than silently rebuilding a
    # connection, matching the uniform use-after-close contract across SDKs.
    it 'bhv_lifecycle_018 close releases underlying client' do
      client = Petstore::Client::DefaultApiClient.new
      client.close
      client.close

      error = _ do
        client.send_request(:GET, 'https://example.com', {}, nil)
      end.must_raise RuntimeError
      _(error).must_be_instance_of RuntimeError
      _(error.message).must_include('closed')
    end
  end

  # ── Gap N1 — body-replay guard fires ONLY on 307/308, not every 3xx ──
  #
  # Canonical cross-SDK scenario for an HTTPS -> HTTP (TLS downgrade)
  # redirect that carries a request body:
  #   * 302: per RFC the request demotes to GET and the body is dropped,
  #          so there is nothing to replay over cleartext — the client
  #          MUST proceed and follow the redirect.
  #   * 307/308: the method and body are preserved, so replaying the body
  #          across the downgrade would leak TLS-protected data — the
  #          client MUST refuse with a typed ApiError.
  # Node over-guarded (refused on ALL statuses, throwing where others
  # proceed); ruby already guards 307/308 only. Canonical = guard 307/308.
  # Deterministic Faraday test stubs — no network, no ENV dependency.
  describe 'Gap N1 — HTTPS -> HTTP body-replay guard scope' do
    def stub_connection_n1(stubs)
      Faraday.new('http://localhost') { |f| f.adapter :test, stubs }
    end

    it 'bhv_redirects_001 a 302 https->http with a body is refused (the body is never replayed in plain text and the call fails)' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.post('/r') do
          [302, { 'location' => 'http://localhost/landing' }, '']
        end
        stub.get('/landing') do
          [200, { 'content-type' => 'text/plain' }, 'ok']
        end
      end
      transport = Petstore::Client::TransportOptions.builder.follow_redirects(true).build
      client = Petstore::Client::DefaultApiClient.new(transport)
      client.stub(:build_connection, stub_connection_n1(stubs)) do
        assert_raises(Petstore::Client::Errors::ApiError) do
          client.send_request(:POST, 'https://localhost/r', {}, 'secret=payload')
        end
      end
    end

    it 'bhv_retries_051 a request with a body is refused with status 307 when a redirect downgrades https to http' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.post('/upload') do
          [307, { 'location' => 'http://insecure.example.com/upload' }, '']
        end
      end
      transport = Petstore::Client::TransportOptions.builder.follow_redirects(true).build
      client = Petstore::Client::DefaultApiClient.new(transport)
      client.stub(:build_connection, stub_connection_n1(stubs)) do
        err = assert_raises(Petstore::Client::Errors::ApiError) do
          client.send_request(:POST, 'https://localhost/upload', {}, 'secret=payload')
        end
        _(err.detail_message).must_equal 'Refusing to replay request body across HTTPS->HTTP redirect: https://localhost/upload -> http://insecure.example.com/upload'
      end
    end

    it 'bhv_retries_010 a 307 HTTPS to HTTP redirect with a body raises ApiError (TLS downgrade)' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.post('/upload') do
          [307, { 'location' => 'http://insecure.example.com/upload' }, '']
        end
      end
      transport = Petstore::Client::TransportOptions.builder.follow_redirects(true).build
      client = Petstore::Client::DefaultApiClient.new(transport)
      client.stub(:build_connection, stub_connection_n1(stubs)) do
        err = assert_raises(Petstore::Client::Errors::ApiError) do
          client.send_request(:POST, 'https://localhost/upload', {}, 'secret=payload')
        end
        _(err.detail_message).must_equal 'Refusing to replay request body across HTTPS->HTTP redirect: https://localhost/upload -> http://insecure.example.com/upload'
      end
    end

    it 'bhv_retries_011 a 308 HTTPS to HTTP redirect with a body raises ApiError (TLS downgrade)' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.post('/upload') do
          [308, { 'location' => 'http://insecure.example.com/upload' }, '']
        end
      end
      transport = Petstore::Client::TransportOptions.builder.follow_redirects(true).build
      client = Petstore::Client::DefaultApiClient.new(transport)
      client.stub(:build_connection, stub_connection_n1(stubs)) do
        err = assert_raises(Petstore::Client::Errors::ApiError) do
          client.send_request(:POST, 'https://localhost/upload', {}, 'secret=payload')
        end
        _(err.detail_message).must_equal 'Refusing to replay request body across HTTPS->HTTP redirect: https://localhost/upload -> http://insecure.example.com/upload'
      end
    end
  end

  # RFC 9110 section 15.4: which method and body the follow-up request carries.
  describe 'redirect method rewriting' do
    def redirect_connection(status, seen)
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        %i[get head post put patch delete].each do |verb|
          stub.public_send(verb, '/start') { [status, { 'location' => '/final' }, ''] }
          stub.public_send(verb, '/final') do |env|
            seen << [env.method.to_s.upcase, env.body.to_s]
            [200, { 'content-type' => 'text/plain' }, 'ok']
          end
        end
      end
      Faraday.new('http://localhost') { |f| f.adapter :test, stubs }
    end

    {
      301 => { 'POST' => 'GET', 'PUT' => 'PUT', 'PATCH' => 'PATCH', 'DELETE' => 'DELETE' },
      302 => { 'POST' => 'GET', 'PUT' => 'PUT', 'PATCH' => 'PATCH', 'DELETE' => 'DELETE' },
      303 => { 'POST' => 'GET', 'PUT' => 'GET', 'PATCH' => 'GET', 'DELETE' => 'GET', 'HEAD' => 'HEAD' },
      307 => { 'POST' => 'POST', 'PUT' => 'PUT', 'DELETE' => 'DELETE' },
      308 => { 'POST' => 'POST', 'PATCH' => 'PATCH' }
    }.each do |status, expectations|
      expectations.each do |verb, expected|
        it "bhv_redirects_050 a #{status} redirect sends #{verb} on as #{expected}" do
          seen = [] # : Array[Array[String]]
          transport = Petstore::Client::TransportOptions.builder
                                                      .connection(redirect_connection(status, seen))
                                                      .follow_redirects(true).build
          client = Petstore::Client::DefaultApiClient.new(transport)

          resp = client.send_request(verb.downcase.to_sym, 'http://localhost/start', {}, 'payload')

          _(resp.status_code).must_equal(200)
          _(seen).must_equal([[expected, expected == verb ? 'payload' : '']])
        end
      end
    end

    it 'bhv_redirects_055 a redirect to a scheme other than http or https is refused with an error' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.get('http://localhost/start') { [302, { 'location' => 'ftp://localhost/file' }, ''] }
      end
      conn = Faraday.new { |f| f.adapter :test, stubs }
      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.connection(conn).follow_redirects(true).build)
      _(-> { client.send_request(:GET, 'http://localhost/start', {}, nil) }).must_raise Petstore::Client::Errors::ApiError
    end

    it 'bhv_redirects_048 a 301 or 302 turns a POST follow-up into a GET with no body' do
      [301, 302].each do |status|
        seen = [] # : Array[Array[String]]
        transport = Petstore::Client::TransportOptions.builder
                                                    .connection(redirect_connection(status, seen))
                                                    .follow_redirects(true).build
        Petstore::Client::DefaultApiClient.new(transport).send_request(:post, 'http://localhost/start', {}, 'payload')
        _(seen).must_equal([['GET', '']])
      end
    end

    it 'bhv_redirects_049 a 307 or 308 repeats the request with the same method and body, followed by default' do
      [307, 308].each do |status|
        %i[post put patch delete].each do |verb|
          seen = [] # : Array[Array[String]]
          transport = Petstore::Client::TransportOptions.builder.connection(redirect_connection(status, seen)).build
          resp = Petstore::Client::DefaultApiClient.new(transport).send_request(verb, 'http://localhost/start', {}, 'payload')
          _(resp.status_code).must_equal(200)
          _(seen).must_equal([[verb.to_s.upcase, 'payload']])
        end
      end
    end

    it 'bhv_redirects_046 follows a chain within the redirect limit and refuses one past it with the 3xx status' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.get('/a') { [302, { 'location' => '/b' }, ''] }
        stub.get('/b') { [302, { 'location' => '/c' }, ''] }
        stub.get('/c') { [200, { 'content-type' => 'text/plain' }, 'done'] }
        stub.get('/loop') { [302, { 'location' => '/loop' }, ''] }
      end
      conn = Faraday.new('http://localhost') { |f| f.adapter :test, stubs }
      client = Petstore::Client::DefaultApiClient.new(
        Petstore::Client::TransportOptions.builder.connection(conn).follow_redirects(true).max_redirects(2).build
      )
      _(client.send_request(:GET, 'http://localhost/a', {}, nil).status_code).must_equal(200)
      err = _(-> { client.send_request(:GET, 'http://localhost/loop', {}, nil) }).must_raise Petstore::Client::Errors::ApiError
      _(err.message).must_include 'Too many redirects'
      _(err.status_code).must_equal 302
    end

    it 'bhv_redirects_052 a cross-origin redirect removes Authorization, Cookie, Proxy-Authorization and secret headers' do
      seen = [] # : Array[Hash[String, String]]
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.get('http://localhost/start') { [302, { 'location' => 'http://other.example/final' }, ''] }
        stub.get('http://other.example/final') do |env|
          seen << env.request_headers
          [200, {}, 'ok']
        end
      end
      conn = Faraday.new { |f| f.adapter :test, stubs }
      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.connection(conn).follow_redirects(true).build)
      client.send_request(:GET, 'http://localhost/start', { 'Authorization' => 'Bearer x', 'Cookie' => 'a=b', 'Proxy-Authorization' => 'p', 'X-Api-Secret' => 's' }, nil)
      _(seen.first.keys.map(&:downcase) & %w[authorization cookie proxy-authorization x-api-secret]).must_be_empty
    end

    it 'bhv_redirects_053 Authorization is kept on a same-origin redirect and dropped when the scheme changes either way' do
      kept = []
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.get('http://localhost/same') { [302, { 'location' => 'http://localhost/final' }, ''] }
        stub.get('https://localhost/down') { [302, { 'location' => 'http://localhost/final' }, ''] }
        stub.get('http://localhost/up') { [302, { 'location' => 'https://localhost/final' }, ''] }
        stub.get('http://localhost/final') do |env|
          kept << env.request_headers.key?('Authorization')
          [200, {}, 'ok']
        end
        stub.get('https://localhost/final') do |env|
          kept << env.request_headers.key?('Authorization')
          [200, {}, 'ok']
        end
      end
      conn = Faraday.new { |f| f.adapter :test, stubs }
      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.connection(conn).follow_redirects(true).build)
      %w[http://localhost/same https://localhost/down http://localhost/up].each do |url|
        client.send_request(:GET, url, { 'Authorization' => 'Bearer x' }, nil)
      end
      _(kept).must_equal [true, false, false]
    end

    it 'bhv_redirects_029 rewrites the method only as RFC 9110 section 15.4 allows' do
      run = lambda do |status, verb|
        seen = [] # : Array[Array[String]]
        transport = Petstore::Client::TransportOptions.builder
                                                    .connection(redirect_connection(status, seen))
                                                    .follow_redirects(true).build
        Petstore::Client::DefaultApiClient.new(transport).send_request(verb, 'http://localhost/start', {}, 'payload')
        seen
      end

      _(run.call(301, :post)).must_equal([['GET', '']])
      _(run.call(302, :post)).must_equal([['GET', '']])
      _(run.call(307, :post)).must_equal([%w[POST payload]])
      _(run.call(308, :post)).must_equal([%w[POST payload]])
    end

    it 'bhv_redirects_051 a 303 turns every follow-up into a GET except that HEAD stays HEAD' do
      run = lambda do |verb, body|
        seen = [] # : Array[Array[String]]
        transport = Petstore::Client::TransportOptions.builder
                                                    .connection(redirect_connection(303, seen))
                                                    .follow_redirects(true).build
        Petstore::Client::DefaultApiClient.new(transport).send_request(verb, 'http://localhost/start', {}, body)
        seen
      end

      %i[post put patch delete].each do |verb|
        _(run.call(verb, 'payload')).must_equal([['GET', '']])
      end
      _(run.call(:head, nil)).must_equal([['HEAD', '']])
    end

    it 'bhv_redirects_036 turns every method except HEAD into GET on a 303' do
      run = lambda do |verb, body|
        seen = [] # : Array[Array[String]]
        transport = Petstore::Client::TransportOptions.builder
                                                    .connection(redirect_connection(303, seen))
                                                    .follow_redirects(true).build
        Petstore::Client::DefaultApiClient.new(transport).send_request(verb, 'http://localhost/start', {}, body)
        seen
      end

      %i[post put patch delete].each do |verb|
        _(run.call(verb, 'payload')).must_equal([['GET', '']])
      end
      _(run.call(:head, nil)).must_equal([['HEAD', '']])
    end
  end

  describe 'injected Faraday connection' do
    def injected_connection(seen)
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.get('/ping') do |env|
          seen << env.request_headers
          [200, { 'content-type' => 'text/plain' }, 'pong']
        end
      end
      Faraday.new('http://localhost') { |f| f.adapter :test, stubs }
    end

    it 'bhv_lifecycle_042 a caller-supplied connection sends its requests through that transport' do
      seen = [] # : Array[Hash[String, String]]
      transport = Petstore::Client::TransportOptions.builder.connection(injected_connection(seen)).user_agent('sdk-ua/1').build
      client = Petstore::Client::DefaultApiClient.new(transport)
      resp = client.send_request(:GET, 'http://localhost/ping', {}, nil)
      _(resp.status_code).must_equal(200)
      _(resp.body).must_equal('pong')
      _(seen.length).must_equal(1)
      _(seen.first['User-Agent']).must_equal('sdk-ua/1')
    end

    it 'bhv_transport_092 a caller-supplied HTTP connection receives the SDK request' do
      seen = [] # : Array[Hash[String, String]]
      transport = Petstore::Client::TransportOptions.builder.connection(injected_connection(seen)).user_agent('sdk-ua/1').build
      client = Petstore::Client::DefaultApiClient.new(transport)
      resp = client.send_request(:GET, 'http://localhost/ping', {}, nil)
      _(resp.status_code).must_equal(200)
      _(resp.body).must_equal('pong')
      _(seen.length).must_equal(1)
      _(seen.first['User-Agent']).must_equal('sdk-ua/1')
    end

    it 'bhv_transport_040 sends the request through the supplied connection with the SDK user agent applied' do
      seen = [] # : Array[Hash[String, String]]
      transport = Petstore::Client::TransportOptions.builder.connection(injected_connection(seen)).user_agent('sdk-ua/1').build
      client = Petstore::Client::DefaultApiClient.new(transport)
      resp = client.send_request(:GET, 'http://localhost/ping', {}, nil)
      _(resp.status_code).must_equal(200)
      _(resp.body).must_equal('pong')
      _(seen.length).must_equal(1)
      _(seen.first['User-Agent']).must_equal('sdk-ua/1')
    end

    it 'bhv_lifecycle_045 closing the client leaves a caller-supplied connection open and usable' do
      conn = injected_connection([])
      closed = [] # : Array[bool]
      conn.define_singleton_method(:close) { closed << true }
      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.connection(conn).build)
      client.send_request(:GET, 'http://localhost/ping', {}, nil)
      client.close
      _(closed).must_be_empty
    end

    it 'bhv_lifecycle_043 a request after close fails with an error and never reaches the network' do
      seen = []
      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.connection(injected_connection(seen)).build)
      client.close
      _(-> { client.send_request(:GET, 'http://localhost/ping', {}, nil) }).must_raise RuntimeError
      _(seen).must_be_empty
    end

    it 'bhv_lifecycle_044 a request in flight when the client closes still completes' do
      started = Queue.new
      release = Queue.new
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.get('/ping') do
          started << true
          release.pop
          [200, { 'content-type' => 'text/plain' }, 'pong']
        end
      end
      conn = Faraday.new('http://localhost') { |f| f.adapter :test, stubs }
      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.connection(conn).build)
      worker = Thread.new { client.send_request(:GET, 'http://localhost/ping', {}, nil) }
      started.pop
      client.close
      release << true
      _(worker.value.status_code).must_equal 200
    end

    it 'bhv_lifecycle_009 does not close the supplied connection when the client is closed' do
      conn = injected_connection([])
      closed = [] # : Array[bool]
      conn.define_singleton_method(:close) { closed << true }
      client = Petstore::Client::DefaultApiClient.new(Petstore::Client::TransportOptions.builder.connection(conn).build)
      client.send_request(:GET, 'http://localhost/ping', {}, nil)
      client.close
      _(closed).must_be_empty
    end
  end
end
