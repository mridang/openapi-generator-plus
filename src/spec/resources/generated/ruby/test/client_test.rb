# frozen_string_literal: true

require 'test_helper'

# A credential that records the transport the client hands it, as an OAuth2 or
# OpenID Connect authenticator needs it.
class ClientTestHttpAwareCredential < Petstore::Client::Auth::Authenticator
  include Petstore::Client::Auth::HttpAwareAuthenticator

  attr_reader :host, :injected

  def initialize(host)
    super()
    @host = host
  end

  def auth_headers
    {}
  end

  def api_client=(client)
    @injected = client
  end
end

describe Petstore::Client::Petstore do
  parallelize_me!

  it 'constructs with authenticator only' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = Petstore::Client::Petstore.new(authenticator)

    _(client).wont_be_nil
  end

  it 'constructs with authenticator and nil transport options' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = Petstore::Client::Petstore.new(authenticator, nil)

    _(client).wont_be_nil
  end

  it 'constructs with authenticator and transport options' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')
    transport = Petstore::Client::TransportOptions.builder.build

    client = Petstore::Client::Petstore.new(authenticator, transport)

    _(client).wont_be_nil
  end

  it 'with_token takes the token provider as the credential' do
    client = Petstore::Client::Petstore.with_token('/api/v3', token_provider: -> { 'fresh-token' })

    _(client).wont_be_nil
  end

  it 'with_token fails when no token, provider or environment variable gives one' do
    prefix = Petstore::Client::Auth::BaseAuthenticator::ENV_PREFIX
    skip "#{prefix}_TOKEN is set in this environment" unless ENV.fetch("#{prefix}_TOKEN", '').empty?

    error = _(-> { Petstore::Client::Petstore.with_token('/api/v3') }).must_raise ArgumentError

    _(error.message).must_equal 'Bearer token must not be empty'
  end

  it 'BearerAuthenticator rejects CR/LF and non-ASCII' do
    # RFC 7230 §3.2.6 — Bearer tokens commonly arrive with trailing
    # newlines from .env / file reads, which would CRLF-inject the
    # Authorization header. Also reject non-ASCII.
    _(lambda {
      Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', "tok\r\nInjected: yes")
    }).must_raise ArgumentError

    _(lambda {
      Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'ñoño')
    }).must_raise ArgumentError
  end

  it 'BearerAuthenticator rejects an empty or whitespace token' do
    # bearer-no-empty-token-guard: an empty/whitespace token would emit a
    # bare "Authorization: Bearer " header, sending the request
    # unauthenticated, so the constructor must reject it.
    _(lambda {
      Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', '')
    }).must_raise ArgumentError

    _(lambda {
      Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', '   ')
    }).must_raise ArgumentError
  end

  it 'ApiKeyAuthenticator HEADER rejects CR/LF and non-ASCII' do
    # RFC 7230 §3.2.6 — header field-value is HTAB / SP / VCHAR.
    # The HEADER location must reject anything outside printable ASCII
    # + TAB to prevent header injection (CR/LF) and silent UTF-8
    # mangling that varies per HTTP lib.
    _(lambda {
      Petstore::Client::Auth::ApiKeyAuthenticator.new('/api/v3', 'X-Api-Key', "abc\r\nInjected: yes",
                                                      Petstore::Client::Auth::ApiKeyLocation::HEADER)
    }).must_raise ArgumentError

    _(lambda {
      Petstore::Client::Auth::ApiKeyAuthenticator.new('/api/v3', 'X-Api-Key', 'kéy',
                                                      Petstore::Client::Auth::ApiKeyLocation::HEADER)
    }).must_raise ArgumentError

    # Non-header locations accept arbitrary chars.
    query_auth = Petstore::Client::Auth::ApiKeyAuthenticator.new(
      '/api/v3', 'api_key', 'kéy', Petstore::Client::Auth::ApiKeyLocation::QUERY
    )
    _(query_auth.query_params).must_equal({ 'api_key' => 'kéy' })
  end

  it 'API groups are accessible' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = Petstore::Client::Petstore.new(authenticator)

    _(client.pet).wont_be_nil
    _(client.store).wont_be_nil
  end

  # The layout Zeitwerk expects, checked without Zeitwerk: every file under
  # lib/petstore/client declares exactly the one constant its path names
  # (errors/not_found_error.rb declares Errors::NotFoundError), no constant
  # lives in a file named after another, and no file claims a top-level
  # constant besides the gem's own module. Loading every file first makes
  # a file that no other file requires count too. The one exception is the
  # per-operation server types: they are declared inside the API file whose
  # operations use them, as in the other eleven SDKs, so they are listed by
  # name below and skipped.
  it 'declares exactly the constant each lib file path names' do
    lib = File.expand_path('../lib', File.dirname(__FILE__))
    root_dir = File.join(lib, 'petstore/client')
    files = Dir.glob(File.join(root_dir, '**', '*.rb'))
    files.each { |file| require file }
    normalize = ->(name) { name.to_s.downcase.delete('_') }
    under_lib = ->(file) { !file.nil? && file.start_with?("#{lib}/") }
    problems = []

    inline_server_types = %w[
      GetExternalPetInfoServer
      GetExternalPetInfoServerServer0
      GetMultiServerPetInfoServer
      GetMultiServerPetInfoServerRegionalRegion
      GetMultiServerPetInfoServerMirrorRegion
      GetMultiServerPetInfoServerChannel
      GetMultiServerPetInfoServerPrimary
      GetMultiServerPetInfoServerRegional
      GetMultiServerPetInfoServerMirror
      GetPetByIdServer
      GetPetByIdServerCDNBackedReadEndpointForPetDetails
      GetStagingPetInfoServer
      GetStagingPetInfoServerEnvironment
      GetStagingPetInfoServerVersion
      GetStagingPetInfoServerStagingServer
    ]

    top_level = 'Petstore::Client'.split('::').first
    Object.constants.each do |name|
      file, = Object.const_source_location(name)
      problems << "#{file} declares top-level #{name}" if under_lib.call(file) && name.to_s != top_level
    end

    walk = lambda do |namespace, dir|
      namespace.constants(false).each do |name|
        next if inline_server_types.include?(name.to_s)

        file, = namespace.const_source_location(name)
        next unless under_lib.call(file)

        sub_dir = Dir.glob(File.join(dir, '*')).find do |entry|
          File.directory?(entry) && normalize.call(File.basename(entry)) == normalize.call(name)
        end
        if sub_dir
          walk.call(namespace.const_get(name, false), sub_dir)
          next
        end
        problems << "#{namespace}::#{name} is declared in #{file}, not in a file of its own under #{dir}" if File.dirname(file) != dir || normalize.call(File.basename(file, '.rb')) != normalize.call(name)
      end
    end
    walk.call(Petstore::Client, root_dir)

    files.each do |file|
      segments = file.delete_prefix("#{root_dir}/").delete_suffix('.rb').split('/')
      namespace = Petstore::Client
      segments.each do |segment|
        name = namespace.constants(false).find { |c| normalize.call(c) == normalize.call(segment) }
        if name.nil?
          problems << "#{file} does not declare #{namespace}::<#{segment}>"
          break
        end
        namespace = namespace.const_get(name, false)
      end
    end

    _(problems).must_be_empty
  end

  # F857: one optional credential per security scheme, named after the scheme.
  def api_groups_of(client)
    client.instance_variables.map { |name| client.instance_variable_get(name) }.grep(Petstore::Client::Api::BaseApi)
  end

  it 'takes one optional keyword credential per security scheme, named after it' do
    keywords = Petstore::Client::Petstore.instance_method(:initialize).parameters
                                                  .filter_map { |kind, name| name if kind == :key }

    _(keywords).must_equal %i[pet_store_basic admin_basic pet_store_bearer service_token api_key_header internal_api_key api_key_query session_cookie machine_auth legacy_auth user_auth browser_auth sso_auth]
  end

  it 'is built from a scheme credential alone and takes its base URL from it' do
    credential = Petstore::Client::Auth::BearerAuthenticator.new('http://127.0.0.1:1', 'scheme-token')

    client = Petstore::Client::Petstore.new(nil, nil, pet_store_basic: credential)

    _(api_groups_of(client).map { |api| api.config.base_url }.uniq).must_equal ['http://127.0.0.1:1']
  end

  it 'takes its base URL from the authenticator before any scheme credential' do
    default = Petstore::Client::Auth::BearerAuthenticator.new('http://127.0.0.1:2', 'default-token')
    credential = Petstore::Client::Auth::BearerAuthenticator.new('http://127.0.0.1:1', 'scheme-token')

    client = Petstore::Client::Petstore.new(default, nil, pet_store_basic: credential)

    _(api_groups_of(client).map { |api| api.config.base_url }.uniq).must_equal ['http://127.0.0.1:2']
  end

  it 'is refused when it gets no credential at all' do
    error = _ { Petstore::Client::Petstore.new }.must_raise ArgumentError

    _(error.message).must_equal 'At least one credential is required'
  end

  it 'hands the shared transport to every credential that needs HTTP' do
    default = ClientTestHttpAwareCredential.new('http://127.0.0.1:2')
    credential = ClientTestHttpAwareCredential.new('http://127.0.0.1:1')

    client = Petstore::Client::Petstore.new(default, nil, pet_store_basic: credential)

    api_client = client.instance_variable_get(:@api_client)
    _(default.injected).must_be_same_as api_client
    _(credential.injected).must_be_same_as api_client
  end

  it 'close makes the client reject further requests' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')
    client = Petstore::Client::Petstore.new(authenticator)
    api_client = client.instance_variable_get(:@api_client)

    client.close
    client.close

    error = _ { api_client.send_request(:get, 'http://localhost/ping', {}, nil) }.must_raise RuntimeError
    _(error.message).must_include('closed')
  end

  it 'close leaves a supplied connection open' do
    conn = Faraday.new
    closed = [] # : Array[bool]
    conn.define_singleton_method(:close) { closed << true }
    transport = Petstore::Client::TransportOptions.builder.connection(conn).build
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    Petstore::Client::Petstore.new(authenticator, transport).close

    _(closed).must_be_empty
  end
end
