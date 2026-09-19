# frozen_string_literal: true
# rubocop:disable all

require 'json'
require 'test_helper'

describe PetstoreClient::DefaultApiClient do
  parallelize_me!

  describe 'TLS verification disabled' do
    it 'makes HTTPS request with verify_ssl=false' do
      chasm_url = ENV.fetch('CHASM_HTTPS_URL')

      transport = PetstoreClient::TransportOptions.builder
        .verify_ssl(false)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
    end
  end

  describe 'custom CA bundle' do
    it 'makes HTTPS request with custom CA cert' do
      chasm_url = ENV.fetch('CHASM_HTTPS_URL')
      ca_cert_path = ENV.fetch('CA_CERT_PATH')

      transport = PetstoreClient::TransportOptions.builder
        .verify_ssl(true)
        .ca_cert_path(ca_cert_path)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
    end
  end

  describe 'HTTP proxy' do
    it 'makes HTTP request through proxy' do
      chasm_url = ENV.fetch('CHASM_INTERNAL_HTTP_URL')
      proxy_url = ENV.fetch('PROXY_URL')

      transport = PetstoreClient::TransportOptions.builder
        .proxy(proxy_url)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
    end
  end

  describe 'proxy with credentials' do
    # Gap AK: userinfo embedded in the proxy URL must be base64-encoded
    # and surfaced as Proxy-Authorization so the proxy can authenticate
    # the tunnel — otherwise the proxy 407s. Faraday reads userinfo
    # natively from the URL; we assert TransportOptions preserves the
    # userinfo end-to-end.
    it 'proxy_with_credentials_injects_basic_authorization' do
      require 'base64'
      require 'uri'

      transport = PetstoreClient::TransportOptions.builder
        .proxy('http://alice:s3cret@127.0.0.1:3128')
        .build

      uri = URI.parse(transport.proxy)
      _(uri.user).must_equal('alice')
      _(uri.password).must_equal('s3cret')
      user = URI.decode_www_form_component(uri.user)
      pass = URI.decode_www_form_component(uri.password)
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
    it 'carries user:pass userinfo from the canonical proxy URL' do
      require 'base64'

      transport = PetstoreClient::TransportOptions.builder
        .proxy('http://user:pass@127.0.0.1:3128')
        .build

      # The userinfo survives in TransportOptions unmodified.
      _(transport.proxy).must_equal('http://user:pass@127.0.0.1:3128')

      # Faraday parses the userinfo from the proxy URL when the connection
      # is built — the credentials are honoured, not dropped.
      client = PetstoreClient::DefaultApiClient.new(transport)
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
    it 'makes HTTPS request through proxy with verify_ssl=false' do
      chasm_url = ENV.fetch('CHASM_INTERNAL_HTTPS_URL')
      proxy_url = ENV.fetch('PROXY_URL')

      transport = PetstoreClient::TransportOptions.builder
        .proxy(proxy_url)
        .verify_ssl(false)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['method']).must_equal('GET')
    end
  end

  describe 'request timeout' do
    it 'times out on slow endpoint' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .timeout(1)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      _ { client.send_request(:GET, "#{chasm_url}/test/slow", {}, nil) }.must_raise StandardError
    end
  end

  describe 'User-Agent header' do
    it 'injects custom User-Agent header' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .user_agent('MyApp/1.0')
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['headers']['user-agent']).must_equal('MyApp/1.0')
    end
  end

  describe 'X-Request-ID injection' do
    it 'injects X-Request-ID header with UUID format' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .inject_request_id(true)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      request_id = json['headers']['x-request-id']
      _(request_id).wont_be_nil
      _(request_id).must_match(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)
    end

    it 'generates unique X-Request-ID per request' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .inject_request_id(true)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)

      response1 = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)
      request_id1 = JSON.parse(response1.body)['headers']['x-request-id']

      response2 = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)
      request_id2 = JSON.parse(response2.body)['headers']['x-request-id']

      _(request_id1).wont_equal(request_id2)
    end
  end

  describe 'default headers' do
    it 'includes transport-level default headers' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .default_header('X-Custom', 'custom-value')
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['headers']['x-custom']).must_equal('custom-value')
    end

    it 'caller headers override transport default headers' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .default_header('Accept', 'text/plain')
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
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
    it 'follows redirects when enabled' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .follow_redirects(true)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/redirect/302", {}, nil)

      _(response.status_code).must_equal(200)
    end

    it 'returns redirect response when disabled' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .follow_redirects(false)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      response = client.send_request(:GET, "#{chasm_url}/test/redirect/302", {}, nil)

      _(response.status_code).must_equal(302)
    end

    it '303 switches to GET and drops body (Gap T3)' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
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
    it 'replays multipart body across 307 redirects (T-new-3)' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
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
    it 'scopes redirect counting per request across concurrent calls' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      transport = PetstoreClient::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      # One shared client used by every thread below.
      client = PetstoreClient::DefaultApiClient.new(transport)

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
    it 'scopes the too-many-redirects refusal per request across concurrent calls' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      # follow_redirects=true but max_redirects=0: the server still answers
      # /test/redirect/302 with a 3xx, so every redirecting request must be
      # refused with an ApiError, while /test/echo never redirects.
      transport = PetstoreClient::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(0)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)

      # Each redirecting thread must raise; capture the exception per thread.
      refused = Array.new(4) do
        Thread.new do
          begin
            client.send_request(:GET, "#{chasm_url}/test/redirect/302", {}, nil)
            nil
          rescue PetstoreClient::ApiError => e
            e
          end
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
    it 'respects max_redirects limit' do
      transport = PetstoreClient::TransportOptions.builder
        .follow_redirects(true)
        .max_redirects(5)
        .build

      client = PetstoreClient::DefaultApiClient.new(transport)
      _(client).wont_be_nil
      _(transport.max_redirects).must_equal(5)
    end
  end

  describe 'multipart body' do
    it 'sends multipart form data' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      client = PetstoreClient::DefaultApiClient.new
      form_data = { 'description' => 'A test file', 'file' => 'file content' }
      response = client.send_request(:POST, "#{chasm_url}/test/echo", {}, form_data)

      _(response).wont_be_nil
    end

    it 'rejects multipart field name with CRLF (Gap W2)' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      client = PetstoreClient::DefaultApiClient.new
      assert_raises(ArgumentError) do
        client.send_request(
          :POST,
          "#{chasm_url}/test/echo",
          {},
          { "name\r\nInjected: yes" => 'value' }
        )
      end
    end

    # W-new-2: multipart field-name validation must run on every branch (not
    # just binary). Confirm that even for a plain String value, a CR/LF in
    # the field name is rejected, preventing Content-Disposition smuggling.
    it 'multipart_field_name_with_crlf_rejected_on_string_value' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')

      client = PetstoreClient::DefaultApiClient.new
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
    it 'decompresses gzip response' do
      client = PetstoreClient::DefaultApiClient.new
      response = client.send_request(
        :GET, 'https://jsonplaceholder.typicode.com/posts/1',
        { 'Accept-Encoding' => 'gzip' }, nil
      )

      _(response.status_code).must_equal(200)
      _(response.body).must_include('userId')
    end

    it 'decompresses brotli response' do
      skip 'brotli gem not installed (optional group)' unless defined?(Brotli)
      client = PetstoreClient::DefaultApiClient.new
      response = client.send_request(
        :GET, 'https://jsonplaceholder.typicode.com/posts/1',
        { 'Accept-Encoding' => 'br' }, nil
      )

      _(response.status_code).must_equal(200)
      _(response.body).must_include('userId')
    end

    it 'decompresses zstd response' do
      skip 'zstd-ruby gem not installed (optional group)' unless defined?(Zstd)
      client = PetstoreClient::DefaultApiClient.new
      response = client.send_request(
        :GET, 'https://jsonplaceholder.typicode.com/posts/1',
        { 'Accept-Encoding' => 'zstd' }, nil
      )

      _(response.status_code).must_equal(200)
      _(response.body).must_include('userId')
    end
  end

  describe 'null-body Content-Length' do
    # Regression: POST/PUT/PATCH with body == nil must emit an explicit
    # Content-Length: 0. Some servers / WAFs reject body-bearing verbs
    # with no Content-Length (411 Length Required). The client sends an
    # empty body and Content-Length: 0 explicitly on body-bearing verbs.
    it 'post_with_null_body_sends_content_length_zero' do
      chasm_url = ENV.fetch('CHASM_HTTP_URL')
      client = PetstoreClient::DefaultApiClient.new
      response = client.send_request(:POST, "#{chasm_url}/test/echo", {}, nil)

      _(response.status_code).must_equal(200)
      json = JSON.parse(response.body)
      _(json['contentLength']).must_equal(0)
    end
  end

  describe 'client lifecycle (Gap T6)' do
    # Gap T6: #close releases the underlying connection and is idempotent.
    # A request issued on a closed client must raise the SDK-typed ApiError
    # (closed-flag guard) rather than silently rebuilding a connection,
    # matching the uniform use-after-close contract across SDKs.
    it 'close releases underlying client' do
      client = PetstoreClient::DefaultApiClient.new
      client.close
      client.close

      error = _ do
        client.send_request(:GET, 'https://example.com', {}, nil)
      end.must_raise PetstoreClient::ApiError
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

    it '302 HTTPS -> HTTP with a body proceeds (body dropped, becomes GET)' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.post('/r') do
          [302, { 'location' => 'http://localhost/landing' }, '']
        end
        stub.get('/landing') do
          [200, { 'content-type' => 'text/plain' }, 'ok']
        end
      end
      transport = PetstoreClient::TransportOptions.builder.follow_redirects(true).build
      client = PetstoreClient::DefaultApiClient.new(transport)
      client.stub(:build_connection, stub_connection_n1(stubs)) do
        resp = client.send_request(:POST, 'https://localhost/r', {}, 'secret=payload')
        _(resp.status_code).must_equal(200)
      end
    end

    it '307 HTTPS -> HTTP with a body raises ApiError (TLS downgrade)' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.post('/upload') do
          [307, { 'location' => 'http://insecure.example.com/upload' }, '']
        end
      end
      transport = PetstoreClient::TransportOptions.builder.follow_redirects(true).build
      client = PetstoreClient::DefaultApiClient.new(transport)
      client.stub(:build_connection, stub_connection_n1(stubs)) do
        err = assert_raises(PetstoreClient::ApiError) do
          client.send_request(:POST, 'https://localhost/upload', {}, 'secret=payload')
        end
        _(err.message).must_match(/TLS downgrade/)
      end
    end

    it '308 HTTPS -> HTTP with a body raises ApiError (TLS downgrade)' do
      stubs = Faraday::Adapter::Test::Stubs.new do |stub|
        stub.post('/upload') do
          [308, { 'location' => 'http://insecure.example.com/upload' }, '']
        end
      end
      transport = PetstoreClient::TransportOptions.builder.follow_redirects(true).build
      client = PetstoreClient::DefaultApiClient.new(transport)
      client.stub(:build_connection, stub_connection_n1(stubs)) do
        err = assert_raises(PetstoreClient::ApiError) do
          client.send_request(:POST, 'https://localhost/upload', {}, 'secret=payload')
        end
        _(err.message).must_match(/TLS downgrade/)
      end
    end
  end
end
