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

describe 'Client' do
  parallelize_me!

  it 'bhv_lifecycle_012 construct with authenticator only' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = Petstore::Client::Petstore.new(authenticator)

    _(client).wont_be_nil
  end

  it 'bhv_lifecycle_010 construct with authenticator and nil transport options' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = Petstore::Client::Petstore.new(authenticator, nil)

    _(client).wont_be_nil
  end

  it 'bhv_lifecycle_011 construct with authenticator and transport options' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')
    transport = Petstore::Client::TransportOptions.builder.build

    client = Petstore::Client::Petstore.new(authenticator, transport)

    _(client).wont_be_nil
  end

  it 'bhv_lifecycle_014 reads the token from the environment when none is passed' do
    prefix = Petstore::Client::Auth::BaseAuthenticator::ENV_PREFIX
    variable = "#{prefix}_TOKEN"
    saved = ENV.fetch(variable, nil)
    begin
      ENV[variable] = 'env-token'
      _(Petstore::Client::Petstore.with_token('/api/v3')).wont_be_nil
      ENV.delete(variable)
      error = _(-> { Petstore::Client::Petstore.with_token('/api/v3') }).must_raise ArgumentError
      _(error.message).must_equal "Missing Bearer token: pass it or set the environment variable #{variable}"
    ensure
      if saved.nil?
        ENV.delete(variable)
      else
        ENV[variable] = saved
      end
    end
  end

  it 'bhv_lifecycle_017 with_token takes the token provider as the credential' do
    client = Petstore::Client::Petstore.with_token('/api/v3', token_provider: -> { 'fresh-token' })

    _(client).wont_be_nil
  end

  it 'bhv_lifecycle_016 with_token fails when no token, provider or environment variable gives one' do
    prefix = Petstore::Client::Auth::BaseAuthenticator::ENV_PREFIX
    skip "#{prefix}_TOKEN is set in this environment" unless ENV.fetch("#{prefix}_TOKEN", '').empty?

    error = _(-> { Petstore::Client::Petstore.with_token('/api/v3') }).must_raise ArgumentError

    _(error.message).must_equal "Missing Bearer token: pass it or set the environment variable #{prefix}_TOKEN"
  end

  it 'bhv_lifecycle_006 BearerAuthenticator rejects CR/LF and non-ASCII' do
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

  it 'bhv_lifecycle_005 BearerAuthenticator rejects an empty or whitespace token' do
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

  it 'bhv_lifecycle_002 ApiKeyAuthenticator HEADER rejects a CR/LF injected key' do
    # RFC 7230 section 3.2.6: a header value must not carry CR or LF, or the
    # key would inject a second header.
    _(lambda {
      Petstore::Client::Auth::ApiKeyAuthenticator.new('/api/v3', 'X-Api-Key', "abc\r\nInjected: yes",
                                                      Petstore::Client::Auth::ApiKeyLocation::HEADER)
    }).must_raise ArgumentError
  end

  it 'bhv_lifecycle_004 ApiKeyAuthenticator HEADER rejects CR/LF and non-ASCII (RFC 7230 3.2.6)' do
    # RFC 7230 section 3.2.6: header field-value is HTAB / SP / VCHAR.
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
  end

  it 'bhv_lifecycle_003 api key query accepts non-ASCII' do
    query_auth = Petstore::Client::Auth::ApiKeyAuthenticator.new(
      '/api/v3', 'api_key', 'kéy', Petstore::Client::Auth::ApiKeyLocation::QUERY
    )
    _(query_auth.query_params).must_equal({ 'api_key' => 'kéy' })
  end

  it 'bhv_lifecycle_001 API groups are accessible' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = Petstore::Client::Petstore.new(authenticator)

    _(client.pet).wont_be_nil
    _(client.problems).wont_be_nil
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

  it 'bhv_lifecycle_015 takes one optional keyword credential per security scheme, named after it, then the call hook' do
    keywords = Petstore::Client::Petstore.instance_method(:initialize).parameters
                                                  .filter_map { |kind, name| name if kind == :key }

    _(keywords).must_equal %i[pet_store_basic admin_basic pet_store_bearer service_token api_key_header internal_api_key api_key_query session_cookie machine_auth legacy_auth user_auth browser_auth sso_auth call_hook]
  end

  it 'bhv_lifecycle_030 the base URL comes from the authenticator first, then from the first scheme credential' do
    credential = Petstore::Client::Auth::BearerAuthenticator.new('http://127.0.0.1:1', 'scheme-token')
    default = Petstore::Client::Auth::BearerAuthenticator.new('http://127.0.0.1:2', 'default-token')

    with_default = Petstore::Client::Petstore.new(default, nil, pet_store_basic: credential)
    without_default = Petstore::Client::Petstore.new(nil, nil, pet_store_basic: credential)

    _(api_groups_of(with_default).map { |api| api.config.base_url }.uniq).must_equal ['http://127.0.0.1:2']
    _(api_groups_of(without_default).map { |api| api.config.base_url }.uniq).must_equal ['http://127.0.0.1:1']
  end

  it 'bhv_lifecycle_026 is refused when it gets no credential at all' do
    error = _ { Petstore::Client::Petstore.new }.must_raise ArgumentError

    _(error.message).must_equal 'At least one credential is required'
  end

  it 'bhv_auth_025 hands the shared transport to every credential that needs HTTP' do
    default = ClientTestHttpAwareCredential.new('http://127.0.0.1:2')
    credential = ClientTestHttpAwareCredential.new('http://127.0.0.1:1')

    client = Petstore::Client::Petstore.new(default, nil, pet_store_basic: credential)

    api_client = client.instance_variable_get(:@api_client)
    _(default.injected).must_be_same_as api_client
    _(credential.injected).must_be_same_as api_client
  end

  it 'bhv_lifecycle_028 an explicit host and token win over the environment' do
    prefix = Petstore::Client::Auth::BaseAuthenticator::ENV_PREFIX
    variable = "#{prefix}_TOKEN"
    saved = ENV.fetch(variable, nil)
    begin
      ENV[variable] = 'env-token'
      client = Petstore::Client::Petstore.with_token('https://arg.example.com', 'arg-token')
      api = client.instance_variables.map { |name| client.instance_variable_get(name) }.grep(Petstore::Client::Api::BaseApi).first

      _(api.config.base_url).must_equal 'https://arg.example.com'
      _(api.instance_variable_get(:@authenticator).auth_headers['Authorization']).must_equal 'Bearer arg-token'
    ensure
      if saved.nil?
        ENV.delete(variable)
      else
        ENV[variable] = saved
      end
    end
  end

  it 'bhv_lifecycle_032 a token passed to the client is preferred to the one in the environment' do
    prefix = Petstore::Client::Auth::BaseAuthenticator::ENV_PREFIX
    variable = "#{prefix}_TOKEN"
    saved = ENV.fetch(variable, nil)
    begin
      ENV[variable] = 'env-token'
      client = Petstore::Client::Petstore.with_token('/api/v3', 'arg-token')
      api = client.instance_variables.map { |name| client.instance_variable_get(name) }.grep(Petstore::Client::Api::BaseApi).first

      _(api.instance_variable_get(:@authenticator).auth_headers['Authorization']).must_equal 'Bearer arg-token'
    ensure
      if saved.nil?
        ENV.delete(variable)
      else
        ENV[variable] = saved
      end
    end
  end

  def call_hook_groups_of(client)
    client.instance_variables.map { |name| client.instance_variable_get(name) }.grep(Petstore::Client::Api::BaseApi)
  end

  it 'hands the call hook to every API group' do
    hook = RecordingCallHook.new
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = Petstore::Client::Petstore.new(authenticator, nil, call_hook: hook)
    from_token = Petstore::Client::Petstore.with_token('/api/v3', 'test-token', call_hook: hook)
    from_authenticator = Petstore::Client::Petstore.with_authenticator(authenticator, call_hook: hook)

    [client, from_token, from_authenticator].each do |built|
      groups = call_hook_groups_of(built)
      _(groups).wont_be_empty
      _(groups.map { |api| api.config.call_hook }.uniq).must_equal [hook]
    end
  end

  it 'has no call hook unless one is given' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    groups = call_hook_groups_of(Petstore::Client::Petstore.new(authenticator))

    _(groups.map { |api| api.config.call_hook }.uniq).must_equal [nil]
  end

  it 'bhv_lifecycle_008 close makes the client reject further requests' do
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')
    client = Petstore::Client::Petstore.new(authenticator)
    api_client = client.instance_variable_get(:@api_client)

    client.close
    client.close

    error = _ { api_client.send_request(:get, 'http://localhost/ping', {}, nil) }.must_raise RuntimeError
    _(error.message).must_include('closed')
  end

  it 'bhv_lifecycle_035 a request after close fails with the closed error and never reaches the transport' do
    reached = []
    conn = Faraday.new
    conn.define_singleton_method(:run_request) { |*_args, **_kwargs, &_block| reached << true }
    transport = Petstore::Client::TransportOptions.builder.connection(conn).build
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')
    client = Petstore::Client::Petstore.new(authenticator, transport)
    api_client = client.instance_variable_get(:@api_client)

    client.close

    error = _ { api_client.send_request(:get, 'http://localhost/ping', {}, nil) }.must_raise RuntimeError
    _(error.message).must_equal 'ApiClient has been closed and can no longer send requests'
    _(reached).must_be_empty
  end

  it 'bhv_lifecycle_007 close leaves a supplied connection open' do
    conn = Faraday.new
    closed = [] # : Array[bool]
    conn.define_singleton_method(:close) { closed << true }
    transport = Petstore::Client::TransportOptions.builder.connection(conn).build
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    Petstore::Client::Petstore.new(authenticator, transport).close

    _(closed).must_be_empty
  end

  it 'bhv_config_025 an empty token variable is treated as unset and construction names it' do
    prefix = Petstore::Client::Auth::BaseAuthenticator::ENV_PREFIX
    variable = "#{prefix}_TOKEN"
    saved = ENV.fetch(variable, nil)
    begin
      ENV[variable] = ''
      error = _(-> { Petstore::Client::Petstore.with_token('/api/v3') }).must_raise ArgumentError
      _(error.message).must_equal "Missing Bearer token: pass it or set the environment variable #{variable}"
    ensure
      if saved.nil?
        ENV.delete(variable)
      else
        ENV[variable] = saved
      end
    end
  end

  it 'bhv_lifecycle_039 a client with no host or an empty token is refused at construction and a valid token builds one' do
    _(-> { Petstore::Client::Petstore.with_token(nil, 'test-token') }).must_raise ArgumentError # steep:ignore ArgumentTypeMismatch
    _(-> { Petstore::Client::Petstore.with_token('', 'test-token') }).must_raise ArgumentError
    _(-> { Petstore::Client::Petstore.with_token('/api/v3', '') }).must_raise ArgumentError
    _(Petstore::Client::Petstore.with_token('/api/v3', 'test-token')).wont_be_nil
  end

  # The group interfaces are RBS (Ruby has no interface keyword), so the tests read the
  # signature files the gem ships and check them against the classes.
  def signature_dir
    File.expand_path('../sig', File.dirname(__FILE__))
  end

  def interface_methods(source, name)
    body = source[/interface #{Regexp.escape(name)}\n(.*?)\n\s*end\n/m, 1]
    _(body).wont_be_nil
    body.to_s.scan(/^\s*def (\w+[?!]?):/).flatten.sort
  end

  it 'bhv_mocking_001 every group has an interface the class implements' do
    source = File.read(Dir.glob(File.join(signature_dir, '**', 'pet_api.rbs')).fetch(0))
    listed = interface_methods(source, '_PetApiInterface')
    group = Petstore::Client::Api::PetApi.new(nil, TEST_CONFIGURATION)
    _(listed).must_include 'add_pet'
    _(listed).must_include 'add_pet_with_http_info'
    _(group).must_respond_to :add_pet
    _(group).must_respond_to :add_pet_with_http_info
    _(listed).must_include 'add_pet_photos'
    _(listed).must_include 'add_pet_photos_with_http_info'
    _(group).must_respond_to :add_pet_photos
    _(group).must_respond_to :add_pet_photos_with_http_info
    _(listed).must_include 'add_pet_treatment'
    _(listed).must_include 'add_pet_treatment_with_http_info'
    _(group).must_respond_to :add_pet_treatment
    _(group).must_respond_to :add_pet_treatment_with_http_info
    _(listed).must_include 'delete_pet'
    _(listed).must_include 'delete_pet_with_http_info'
    _(group).must_respond_to :delete_pet
    _(group).must_respond_to :delete_pet_with_http_info
    _(listed).must_include 'download_pet_document'
    _(listed).must_include 'download_pet_document_with_http_info'
    _(group).must_respond_to :download_pet_document
    _(group).must_respond_to :download_pet_document_with_http_info
    _(listed).must_include 'find_pets_born_on'
    _(listed).must_include 'find_pets_born_on_with_http_info'
    _(group).must_respond_to :find_pets_born_on
    _(group).must_respond_to :find_pets_born_on_with_http_info
    _(listed).must_include 'find_pets_by_status'
    _(listed).must_include 'find_pets_by_status_with_http_info'
    _(group).must_respond_to :find_pets_by_status
    _(group).must_respond_to :find_pets_by_status_with_http_info
    _(listed).must_include 'get_external_pet_info'
    _(listed).must_include 'get_external_pet_info_with_http_info'
    _(group).must_respond_to :get_external_pet_info
    _(group).must_respond_to :get_external_pet_info_with_http_info
    _(listed).must_include 'get_multi_server_pet_info'
    _(listed).must_include 'get_multi_server_pet_info_with_http_info'
    _(group).must_respond_to :get_multi_server_pet_info
    _(group).must_respond_to :get_multi_server_pet_info_with_http_info
    _(listed).must_include 'get_pet_avatar'
    _(listed).must_include 'get_pet_avatar_with_http_info'
    _(group).must_respond_to :get_pet_avatar
    _(group).must_respond_to :get_pet_avatar_with_http_info
    _(listed).must_include 'get_pet_avatar_thumbnail'
    _(listed).must_include 'get_pet_avatar_thumbnail_with_http_info'
    _(group).must_respond_to :get_pet_avatar_thumbnail
    _(group).must_respond_to :get_pet_avatar_thumbnail_with_http_info
    _(listed).must_include 'get_pet_by_id'
    _(listed).must_include 'get_pet_by_id_with_http_info'
    _(group).must_respond_to :get_pet_by_id
    _(group).must_respond_to :get_pet_by_id_with_http_info
    _(listed).must_include 'get_pet_by_name'
    _(listed).must_include 'get_pet_by_name_with_http_info'
    _(group).must_respond_to :get_pet_by_name
    _(group).must_respond_to :get_pet_by_name_with_http_info
    _(listed).must_include 'get_pet_by_note'
    _(listed).must_include 'get_pet_by_note_with_http_info'
    _(group).must_respond_to :get_pet_by_note
    _(group).must_respond_to :get_pet_by_note_with_http_info
    _(listed).must_include 'get_pet_passport'
    _(listed).must_include 'get_pet_passport_with_http_info'
    _(group).must_respond_to :get_pet_passport
    _(group).must_respond_to :get_pet_passport_with_http_info
    _(listed).must_include 'get_pet_photo'
    _(listed).must_include 'get_pet_photo_with_http_info'
    _(group).must_respond_to :get_pet_photo
    _(group).must_respond_to :get_pet_photo_with_http_info
    _(listed).must_include 'get_pet_summary'
    _(listed).must_include 'get_pet_summary_with_http_info'
    _(group).must_respond_to :get_pet_summary
    _(group).must_respond_to :get_pet_summary_with_http_info
    _(listed).must_include 'get_pet_tag'
    _(listed).must_include 'get_pet_tag_with_http_info'
    _(group).must_respond_to :get_pet_tag
    _(group).must_respond_to :get_pet_tag_with_http_info
    _(listed).must_include 'get_staging_pet_info'
    _(listed).must_include 'get_staging_pet_info_with_http_info'
    _(group).must_respond_to :get_staging_pet_info
    _(group).must_respond_to :get_staging_pet_info_with_http_info
    _(listed).must_include 'set_pet_avatar'
    _(listed).must_include 'set_pet_avatar_with_http_info'
    _(group).must_respond_to :set_pet_avatar
    _(group).must_respond_to :set_pet_avatar_with_http_info
    _(listed).must_include 'set_pet_avatar_thumbnail'
    _(listed).must_include 'set_pet_avatar_thumbnail_with_http_info'
    _(group).must_respond_to :set_pet_avatar_thumbnail
    _(group).must_respond_to :set_pet_avatar_thumbnail_with_http_info
    _(listed).must_include 'set_pet_preferences'
    _(listed).must_include 'set_pet_preferences_with_http_info'
    _(group).must_respond_to :set_pet_preferences
    _(group).must_respond_to :set_pet_preferences_with_http_info
    _(listed).must_include 'update_pet'
    _(listed).must_include 'update_pet_with_http_info'
    _(group).must_respond_to :update_pet
    _(group).must_respond_to :update_pet_with_http_info
    _(listed).must_include 'upload_pet_certificate'
    _(listed).must_include 'upload_pet_certificate_with_http_info'
    _(group).must_respond_to :upload_pet_certificate
    _(group).must_respond_to :upload_pet_certificate_with_http_info
    _(listed).must_include 'upload_pet_document'
    _(listed).must_include 'upload_pet_document_with_http_info'
    _(group).must_respond_to :upload_pet_document
    _(group).must_respond_to :upload_pet_document_with_http_info
    source = File.read(Dir.glob(File.join(signature_dir, '**', 'problems_api.rbs')).fetch(0))
    listed = interface_methods(source, '_ProblemsApiInterface')
    group = Petstore::Client::Api::ProblemsApi.new(nil, TEST_CONFIGURATION)
    _(listed).must_include 'get_fallback'
    _(listed).must_include 'get_fallback_with_http_info'
    _(group).must_respond_to :get_fallback
    _(group).must_respond_to :get_fallback_with_http_info
    _(listed).must_include 'get_member'
    _(listed).must_include 'get_member_with_http_info'
    _(group).must_respond_to :get_member
    _(group).must_respond_to :get_member_with_http_info
    _(listed).must_include 'get_project'
    _(listed).must_include 'get_project_with_http_info'
    _(group).must_respond_to :get_project
    _(group).must_respond_to :get_project_with_http_info
    _(listed).must_include 'get_range'
    _(listed).must_include 'get_range_with_http_info'
    _(group).must_respond_to :get_range
    _(group).must_respond_to :get_range_with_http_info
    _(listed).must_include 'get_upstream'
    _(listed).must_include 'get_upstream_with_http_info'
    _(group).must_respond_to :get_upstream
    _(group).must_respond_to :get_upstream_with_http_info
    _(listed).must_include 'take_lock'
    _(listed).must_include 'take_lock_with_http_info'
    _(group).must_respond_to :take_lock
    _(group).must_respond_to :take_lock_with_http_info
    source = File.read(Dir.glob(File.join(signature_dir, '**', 'store_api.rbs')).fetch(0))
    listed = interface_methods(source, '_StoreApiInterface')
    group = Petstore::Client::Api::StoreApi.new(nil, TEST_CONFIGURATION)
    _(listed).must_include 'create_account'
    _(listed).must_include 'create_account_with_http_info'
    _(group).must_respond_to :create_account
    _(group).must_respond_to :create_account_with_http_info
    _(listed).must_include 'delete_order'
    _(listed).must_include 'delete_order_with_http_info'
    _(group).must_respond_to :delete_order
    _(group).must_respond_to :delete_order_with_http_info
    _(listed).must_include 'get_by_swatch'
    _(listed).must_include 'get_by_swatch_with_http_info'
    _(group).must_respond_to :get_by_swatch
    _(group).must_respond_to :get_by_swatch_with_http_info
    _(listed).must_include 'get_defaults'
    _(listed).must_include 'get_defaults_with_http_info'
    _(group).must_respond_to :get_defaults
    _(group).must_respond_to :get_defaults_with_http_info
    _(listed).must_include 'get_department'
    _(listed).must_include 'get_department_with_http_info'
    _(group).must_respond_to :get_department
    _(group).must_respond_to :get_department_with_http_info
    _(listed).must_include 'get_grouped_categories'
    _(listed).must_include 'get_grouped_categories_with_http_info'
    _(group).must_respond_to :get_grouped_categories
    _(group).must_respond_to :get_grouped_categories_with_http_info
    _(listed).must_include 'get_inventory'
    _(listed).must_include 'get_inventory_with_http_info'
    _(group).must_respond_to :get_inventory
    _(group).must_respond_to :get_inventory_with_http_info
    _(listed).must_include 'get_matrix'
    _(listed).must_include 'get_matrix_with_http_info'
    _(group).must_respond_to :get_matrix
    _(group).must_respond_to :get_matrix_with_http_info
    _(listed).must_include 'get_native_values'
    _(listed).must_include 'get_native_values_with_http_info'
    _(group).must_respond_to :get_native_values
    _(group).must_respond_to :get_native_values_with_http_info
    _(listed).must_include 'get_order_by_id'
    _(listed).must_include 'get_order_by_id_with_http_info'
    _(group).must_respond_to :get_order_by_id
    _(group).must_respond_to :get_order_by_id_with_http_info
    _(listed).must_include 'get_order_by_tracking_id'
    _(listed).must_include 'get_order_by_tracking_id_with_http_info'
    _(group).must_respond_to :get_order_by_tracking_id
    _(group).must_respond_to :get_order_by_tracking_id_with_http_info
    _(listed).must_include 'get_stock_item'
    _(listed).must_include 'get_stock_item_with_http_info'
    _(group).must_respond_to :get_stock_item
    _(group).must_respond_to :get_stock_item_with_http_info
    _(listed).must_include 'get_swatch'
    _(listed).must_include 'get_swatch_with_http_info'
    _(group).must_respond_to :get_swatch
    _(group).must_respond_to :get_swatch_with_http_info
    _(listed).must_include 'get_swatch_groups'
    _(listed).must_include 'get_swatch_groups_with_http_info'
    _(group).must_respond_to :get_swatch_groups
    _(group).must_respond_to :get_swatch_groups_with_http_info
    _(listed).must_include 'get_timestamp_groups'
    _(listed).must_include 'get_timestamp_groups_with_http_info'
    _(group).must_respond_to :get_timestamp_groups
    _(group).must_respond_to :get_timestamp_groups_with_http_info
    _(listed).must_include 'get_tree'
    _(listed).must_include 'get_tree_with_http_info'
    _(group).must_respond_to :get_tree
    _(group).must_respond_to :get_tree_with_http_info
    _(listed).must_include 'place_order'
    _(listed).must_include 'place_order_with_http_info'
    _(group).must_respond_to :place_order
    _(group).must_respond_to :place_order_with_http_info
    _(listed).must_include 'reserve_stock'
    _(listed).must_include 'reserve_stock_with_http_info'
    _(group).must_respond_to :reserve_stock
    _(group).must_respond_to :reserve_stock_with_http_info
    _(listed).must_include 'search_store'
    _(listed).must_include 'search_store_with_http_info'
    _(group).must_respond_to :search_store
    _(group).must_respond_to :search_store_with_http_info
  end

  it 'bhv_mocking_002 the group interface lists exactly the public operations of the class' do
    source = File.read(Dir.glob(File.join(signature_dir, '**', 'pet_api.rbs')).fetch(0))
    _(interface_methods(source, '_PetApiInterface'))
      .must_equal Petstore::Client::Api::PetApi.public_instance_methods(false).map(&:to_s).sort
    source = File.read(Dir.glob(File.join(signature_dir, '**', 'problems_api.rbs')).fetch(0))
    _(interface_methods(source, '_ProblemsApiInterface'))
      .must_equal Petstore::Client::Api::ProblemsApi.public_instance_methods(false).map(&:to_s).sort
    source = File.read(Dir.glob(File.join(signature_dir, '**', 'store_api.rbs')).fetch(0))
    _(interface_methods(source, '_StoreApiInterface'))
      .must_equal Petstore::Client::Api::StoreApi.public_instance_methods(false).map(&:to_s).sort
  end

  it 'bhv_mocking_007 the client interface exposes every group typed as its interface and close' do
    source = File.read(File.join(signature_dir, 'infrastructure.rbs'))
    listed = interface_methods(source, '_PetstoreInterface')
    authenticator = Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'test-token')
    client = Petstore::Client::Petstore.new(authenticator)

    _(listed).must_include 'pet'
    _(source).must_include 'def pet: () -> Api::_PetApiInterface'
    _(client).must_respond_to :pet
    _(listed).must_include 'problems'
    _(source).must_include 'def problems: () -> Api::_ProblemsApiInterface'
    _(client).must_respond_to :problems
    _(listed).must_include 'store'
    _(source).must_include 'def store: () -> Api::_StoreApiInterface'
    _(client).must_respond_to :store
    _(listed).must_include 'close'
    _(client).must_respond_to :close
  end

  it 'bhv_mocking_008 a client built from fakes never connects and closes without error' do
    closed = 0
    fake = Struct.new(:pet, :problems, :store, :closer) do
      define_method(:close) { closer.call }
    end.new(Object.new, Object.new, Object.new, -> { closed += 1 })

    fake.close
    fake.close

    _(closed).must_equal 2
  end
end
