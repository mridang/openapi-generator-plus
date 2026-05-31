# frozen_string_literal: true

# rubocop:disable Metrics/BlockLength, Lint/MissingCopEnableDirective

require 'minitest/autorun'
require 'json'
require 'petstore_client'

describe PetstoreClient::DefaultApiClient do
  parallelize_me!

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

  it 'returns JSON body for vendor JSON content type' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/vendor-json') do
        [200, { 'content-type' => 'application/vnd.api+json' }, '{"format":"vendor"}']
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('GET', 'http://localhost/vendor-json', {}, nil)
      _(response.status_code).must_equal 200
      body = JSON.parse(response.body)
      _(body['format']).must_equal 'vendor'
    end
    stubs.verify_stubbed_calls
  end

  it 'joins multi-value response headers' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/multi-header') do
        [200, { 'x-custom-value' => 'val1, val2' }, 'ok']
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('GET', 'http://localhost/multi-header', {}, nil)
      _(response.status_code).must_equal 200
      _(response.headers['x-custom-value']).must_include 'val1'
      _(response.headers['x-custom-value']).must_include 'val2'
    end
    stubs.verify_stubbed_calls
  end

  it 'injects custom User-Agent header' do
    captured_ua = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/test') do |env|
        captured_ua = env.request_headers['User-Agent']
        [200, {}, '{}']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.user_agent('MyApp/1.0').build
    client = PetstoreClient::DefaultApiClient.new(transport)
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('GET', 'http://localhost/test', {}, nil)
    end
    _(captured_ua).must_equal 'MyApp/1.0'
    stubs.verify_stubbed_calls
  end

  it 'injects default User-Agent when not explicitly set' do
    captured_ua = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/test') do |env|
        captured_ua = env.request_headers['User-Agent']
        [200, {}, '{}']
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('GET', 'http://localhost/test', {}, nil)
    end
    _(captured_ua).wont_be_nil
    _(captured_ua).wont_be_empty
    stubs.verify_stubbed_calls
  end

  it 'injects X-Request-ID when enabled' do
    captured_id = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/test') do |env|
        captured_id = env.request_headers['X-Request-ID']
        [200, {}, '{}']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.inject_request_id(true).build
    client = PetstoreClient::DefaultApiClient.new(transport)
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('GET', 'http://localhost/test', {}, nil)
    end
    _(captured_id).wont_be_nil
    _(captured_id).must_match(/\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/)
    stubs.verify_stubbed_calls
  end

  it 'does not inject X-Request-ID when disabled' do
    captured_headers = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/test') do |env|
        captured_headers = env.request_headers
        [200, {}, '{}']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.inject_request_id(false).build
    client = PetstoreClient::DefaultApiClient.new(transport)
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('GET', 'http://localhost/test', {}, nil)
    end
    _(captured_headers).wont_be_nil
    _(captured_headers.key?('X-Request-ID')).must_equal false
    stubs.verify_stubbed_calls
  end

  it 'does not override caller-provided X-Request-ID' do
    captured_id = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/test') do |env|
        captured_id = env.request_headers['X-Request-ID']
        [200, {}, '{}']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.inject_request_id(true).build
    client = PetstoreClient::DefaultApiClient.new(transport)
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('GET', 'http://localhost/test', { 'X-Request-ID' => 'caller-id' }, nil)
    end
    _(captured_id).must_equal 'caller-id'
    stubs.verify_stubbed_calls
  end

  it 'generates unique X-Request-ID per request' do
    captured_ids = []
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/test') do |env|
        captured_ids << env.request_headers['X-Request-ID']
        [200, {}, '{}']
      end
      stub.get('/test') do |env|
        captured_ids << env.request_headers['X-Request-ID']
        [200, {}, '{}']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.inject_request_id(true).build
    client = PetstoreClient::DefaultApiClient.new(transport)
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('GET', 'http://localhost/test', {}, nil)
      client.send_request('GET', 'http://localhost/test', {}, nil)
    end
    _(captured_ids.length).must_equal 2
    _(captured_ids[0]).wont_equal captured_ids[1]
  end

  it 'includes transport-level default headers' do
    captured_value = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/test') do |env|
        captured_value = env.request_headers['X-Custom']
        [200, {}, '{}']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.default_header('X-Custom', 'custom-value').build
    client = PetstoreClient::DefaultApiClient.new(transport)
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('GET', 'http://localhost/test', {}, nil)
    end
    _(captured_value).must_equal 'custom-value'
    stubs.verify_stubbed_calls
  end

  it 'caller headers override transport default headers' do
    captured_accept = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/test') do |env|
        captured_accept = env.request_headers['Accept']
        [200, {}, '{}']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.default_header('Accept', 'text/plain').build
    client = PetstoreClient::DefaultApiClient.new(transport)
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('GET', 'http://localhost/test', { 'Accept' => 'application/json' }, nil)
    end
    _(captured_accept).must_equal 'application/json'
    stubs.verify_stubbed_calls
  end

  # ── Multipart filename sanitization (Gap F) ──

  it 'rejects multipart filename containing CRLF (header injection)' do
    require 'stringio'
    client = PetstoreClient::DefaultApiClient.new
    io = StringIO.new('payload')
    io.define_singleton_method(:path) { "a\r\nX-Injected: yes" }
    assert_raises(ArgumentError) do
      client.send_request('POST', 'http://localhost/upload', {}, { 'file' => io })
    end
  end

  it 'rejects multipart filename containing NUL byte' do
    require 'stringio'
    client = PetstoreClient::DefaultApiClient.new
    io = StringIO.new('payload')
    io.define_singleton_method(:path) { "a\0b.txt" }
    assert_raises(ArgumentError) do
      client.send_request('POST', 'http://localhost/upload', {}, { 'file' => io })
    end
  end

  it 'backslash-escapes quotes and backslashes in multipart filename' do
    captured_body = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/upload') do |env|
        captured_body = env.body
        [200, {}, '{}']
      end
    end
    require 'stringio'
    io = StringIO.new('payload')
    io.define_singleton_method(:path) { 'a"b.txt' }
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('POST', 'http://localhost/upload', {}, { 'file' => io })
    end
    _(captured_body.to_s).must_include 'filename="a\\"b.txt"'
    stubs.verify_stubbed_calls
  end

  it 'emits RFC 5987 filename* for non-ASCII multipart filenames' do
    captured_body = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/upload') do |env|
        captured_body = env.body
        [200, {}, '{}']
      end
    end
    require 'stringio'
    io = StringIO.new('payload')
    io.define_singleton_method(:path) { '日本.pdf' }
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('POST', 'http://localhost/upload', {}, { 'file' => io })
    end
    body_str = captured_body.to_s.dup.force_encoding(Encoding::ASCII_8BIT)
    _(body_str).must_include "filename*=UTF-8''%E6%97%A5%E6%9C%AC.pdf"
    stubs.verify_stubbed_calls
  end

  # ── Per-part MIME sniffing (Gap J) ──

  it 'sets image/png Content-Type for .png upload' do
    captured_body = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/upload') do |env|
        captured_body = env.body
        [200, {}, '{}']
      end
    end
    require 'stringio'
    io = StringIO.new("\x89PNG\r\n".b)
    io.define_singleton_method(:path) { 'pic.png' }
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('POST', 'http://localhost/upload', {}, { 'file' => io })
    end
    _(captured_body.to_s).must_include 'Content-Type: image/png'
    stubs.verify_stubbed_calls
  end

  it 'sets application/pdf for .pdf upload' do
    captured_body = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/upload') do |env|
        captured_body = env.body
        [200, {}, '{}']
      end
    end
    require 'stringio'
    io = StringIO.new('PDF-bytes')
    io.define_singleton_method(:path) { 'doc.pdf' }
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('POST', 'http://localhost/upload', {}, { 'file' => io })
    end
    _(captured_body.to_s).must_include 'Content-Type: application/pdf'
    stubs.verify_stubbed_calls
  end

  it 'falls back to application/octet-stream for unknown extension' do
    captured_body = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/upload') do |env|
        captured_body = env.body
        [200, {}, '{}']
      end
    end
    require 'stringio'
    io = StringIO.new('bytes')
    io.define_singleton_method(:path) { 'blob.xyzunknown' }
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request('POST', 'http://localhost/upload', {}, { 'file' => io })
    end
    _(captured_body.to_s).must_include 'Content-Type: application/octet-stream'
    stubs.verify_stubbed_calls
  end

  # ── Response charset decoding (Gap H) ──

  it 'decodes ISO-8859-1 response body to UTF-8' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/latin1') do
        [200, { 'content-type' => 'text/plain; charset=ISO-8859-1' }, "\xE9".b]
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('GET', 'http://localhost/latin1', {}, nil)
      _(response.body.encoding).must_equal Encoding::UTF_8
      _(response.body).must_equal 'é'
    end
    stubs.verify_stubbed_calls
  end

  it 'defaults to UTF-8 when no charset is given' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/no-charset') do
        [200, { 'content-type' => 'text/plain' }, 'héllo']
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('GET', 'http://localhost/no-charset', {}, nil)
      _(response.body).must_equal 'héllo'
    end
    stubs.verify_stubbed_calls
  end

  it 'falls back to UTF-8 for unknown charset without raising' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/bogus') do
        [200, { 'content-type' => 'text/plain; charset=not-a-real-charset' }, 'hello']
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      response = client.send_request('GET', 'http://localhost/bogus', {}, nil)
      _(response.body).must_equal 'hello'
    end
    stubs.verify_stubbed_calls
  end

  # ── Proxy authentication (#29) ──
  #
  # The test fixture Squid config does NOT enable basic auth, so any
  # proxy-with-credentials request will succeed at the proxy level just
  # like an unauthenticated request. We assert that the userinfo portion
  # of the proxy URL is accepted and forwarded to Faraday's proxy config
  # without raising; full end-to-end basic-auth verification is skipped
  # because the fixture Squid lacks auth_param config.
  it 'accepts proxy URL with basic-auth userinfo (skips if Squid lacks auth)' do
    skip 'Squid fixture has no auth_param basic configuration'
  end

  it 'parses proxy URL with userinfo without raising' do
    transport = PetstoreClient::TransportOptions.builder
      .proxy('http://user:pass@proxy.example.com:3128')
      .build
    _(transport.proxy).must_equal 'http://user:pass@proxy.example.com:3128'

    # Verify Faraday accepts the proxy URL during connection build.
    client = PetstoreClient::DefaultApiClient.new(transport)
    conn = client.send(:build_connection)
    captured_proxy = conn.proxy
    _(captured_proxy).wont_be_nil
    _(captured_proxy.user).must_equal 'user'
    _(captured_proxy.password).must_equal 'pass'
  end

  # ── Bucket 3.2: no_redirect refuses 3xx on token POSTs ──

  it 'raises ApiError when 302 is returned and no_redirect: true' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/token') do
        [302, { 'location' => 'https://attacker.example.com/steal' }, '']
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      err = assert_raises(PetstoreClient::ApiError) do
        client.send_request(:POST, 'http://localhost/token', {}, 'grant_type=client_credentials', no_redirect: true)
      end
      _(err.message).must_match(/Refusing to follow/)
    end
  end

  it 'raises ApiError on 307 redirect when no_redirect: true' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/token') do
        [307, { 'location' => 'https://attacker.example.com/steal' }, '']
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      assert_raises(PetstoreClient::ApiError) do
        client.send_request(:POST, 'http://localhost/token', {}, 'client_id=abc&client_secret=xyz', no_redirect: true)
      end
    end
  end

  it 'returns 2xx normally when no_redirect: true and no redirect' do
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/token') do
        [200, { 'content-type' => 'application/json' }, '{"access_token":"ok"}']
      end
    end
    client = PetstoreClient::DefaultApiClient.new
    client.stub(:build_connection, stub_connection(stubs)) do
      resp = client.send_request(:POST, 'http://localhost/token', {}, 'grant_type=client_credentials', no_redirect: true)
      _(resp.status_code).must_equal 200
    end
  end

  # ── Bucket 3.3: refuse body replay on HTTPS -> HTTP downgrade ──

  it 'raises ApiError when 307 redirects HTTPS -> HTTP and there is a body' do
    # 307 preserves method+body, so the original POST body would be
    # replayed over cleartext on the redirect. The downgrade guard
    # MUST refuse rather than leak the body.
    call_count = 0
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.post('/upload') do
        call_count += 1
        [307, { 'location' => 'http://insecure.example.com/upload' }, '']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.follow_redirects(true).build
    client = PetstoreClient::DefaultApiClient.new(transport)
    client.stub(:build_connection, stub_connection(stubs)) do
      err = assert_raises(PetstoreClient::ApiError) do
        client.send_request(:POST, 'https://localhost/upload', {}, 'secret=payload')
      end
      _(err.message).must_match(/TLS downgrade/)
    end
    _(call_count).must_equal 1
  end

  it 'allows HTTPS -> HTTP redirect when method demotes to GET with no body' do
    # 302 of a POST demotes to GET and drops the body, so there is no
    # body to replay across the downgrade — the guard MUST NOT refuse.
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
    client.stub(:build_connection, stub_connection(stubs)) do
      resp = client.send_request(:POST, 'https://localhost/r', {}, 'k=v')
      _(resp.status_code).must_equal 200
    end
  end

  # ── Bucket 3.1: API-key header names included in cross-origin strip set ──

  it 'EXTRA_SENSITIVE_HEADER_NAMES is defined and lowercase' do
    # Codegen populates this from `securitySchemes` entries with
    # type=apiKey, in=header. Whether the spec under test contains any
    # such schemes or not, the constant MUST exist and every entry
    # MUST be lowercased so the cross-origin filter compares
    # case-insensitively.
    names = PetstoreClient::DefaultApiClient::EXTRA_SENSITIVE_HEADER_NAMES
    _(names).must_be_kind_of Array
    names.each do |n|
      _(n).must_equal n.downcase
    end
    _(names).must_include ''
    _(names).must_include ''
  end

  it 'strips configured api-key headers on cross-origin redirect (Bucket 3.1)' do
    # 302 redirects from localhost to a different host. Sensitive
    # headers (the static authorization/cookie/proxy-authorization plus
    # any apiKey,in=header names harvested from the spec) MUST be
    # dropped on the cross-origin follow-up so a malicious 302 cannot
    # exfiltrate the API key.
    captured_followup_headers = nil
    stubs = Faraday::Adapter::Test::Stubs.new do |stub|
      stub.get('/start') do
        [302, { 'location' => 'http://otherhost.example.com/landing' }, '']
      end
      stub.get('http://otherhost.example.com/landing') do |env|
        captured_followup_headers = env.request_headers
        [200, { 'content-type' => 'text/plain' }, 'ok']
      end
    end
    transport = PetstoreClient::TransportOptions.builder.follow_redirects(true).build
    client = PetstoreClient::DefaultApiClient.new(transport)
    headers = {
      'Authorization' => 'Bearer secret',
      'X-Api-Key' => 'k1',
      'X-Internal-Key' => 'k2'
    }
    client.stub(:build_connection, stub_connection(stubs)) do
      client.send_request(:GET, 'http://localhost/start', headers, nil)
    end
    _(captured_followup_headers).wont_be_nil
    lc = captured_followup_headers.transform_keys(&:downcase)
    _(lc.key?('authorization')).must_equal false
    _(lc.key?('')).must_equal false
    _(lc.key?('')).must_equal false
  end
end
