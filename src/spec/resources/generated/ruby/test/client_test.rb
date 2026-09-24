# frozen_string_literal: true

require 'test_helper'

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

  it 'BearerAuthenticator rejects CR/LF and non-ASCII' do
    # RFC 7230 §3.2.6 — Bearer tokens commonly arrive with trailing
    # newlines from .env / file reads, which would CRLF-inject the
    # Authorization header. Also reject non-ASCII.
    _(-> {
      Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', "tok\r\nInjected: yes")
    }).must_raise ArgumentError

    _(-> {
      Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', 'ñoño')
    }).must_raise ArgumentError
  end

  it 'BearerAuthenticator rejects an empty or whitespace token' do
    # bearer-no-empty-token-guard: an empty/whitespace token would emit a
    # bare "Authorization: Bearer " header, sending the request
    # unauthenticated, so the constructor must reject it.
    _(-> {
      Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', '')
    }).must_raise ArgumentError

    _(-> {
      Petstore::Client::Auth::BearerAuthenticator.new('/api/v3', '   ')
    }).must_raise ArgumentError
  end

  it 'ApiKeyAuthenticator HEADER rejects CR/LF and non-ASCII' do
    # RFC 7230 §3.2.6 — header field-value is HTAB / SP / VCHAR.
    # The HEADER location must reject anything outside printable ASCII
    # + TAB to prevent header injection (CR/LF) and silent UTF-8
    # mangling that varies per HTTP lib.
    _(-> {
      Petstore::Client::Auth::ApiKeyAuthenticator.new('/api/v3', 'X-Api-Key', "abc\r\nInjected: yes",
                                                      Petstore::Client::Auth::ApiKeyLocation::HEADER)
    }).must_raise ArgumentError

    _(-> {
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
      GetMultiServerPetInfoServerRegion
      GetMultiServerPetInfoServerPrimary
      GetMultiServerPetInfoServerRegional
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
        if File.dirname(file) != dir || normalize.call(File.basename(file, '.rb')) != normalize.call(name)
          problems << "#{namespace}::#{name} is declared in #{file}, not in a file of its own under #{dir}"
        end
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
end
