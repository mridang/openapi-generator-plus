# frozen_string_literal: true

require 'test_helper'

# Guards the claims the README makes about the SDK's own API.
describe 'README' do
  parallelize_me!

  def readme
    File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'README.md'))
  end

  it 'bhv_options_044 names the builder setter that rejects a SOCKS proxy' do
    _(readme).must_include '`TransportOptions.builder.proxy(url)` accepts only `http://` and `https://` URLs.'
    _(readme).wont_include 'TransportOptions.proxy()'
  end

  it 'bhv_langruby_068 documents the schema constraints and uniqueItems ordering' do
    _(readme).must_include '### Schema constraints'
    _(readme).must_include '`pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,'
    _(readme).must_include '`uniqueItems` arrays'
  end

  it 'bhv_langruby_069 makes no claim about how the other SDKs treat a discriminator no-match' do
    _(readme).must_include 'raises'
    _(readme).wont_include 'This matches Python'
  end

  it 'bhv_langruby_070 lists the unsupported features and says uniqueItems order is unspecified' do
    ['### XML bodies and `nodeType`', '### Link Objects', '### Mutual TLS security schemes'].each do |heading|
      _(readme).must_include heading
    end
    _(readme).wont_include 'ascending natural order'
    _(readme).must_include '## Development'
  end

  it 'bhv_langruby_071 documents the retry option and no longer says requests are never retried' do
    _(readme).must_include '### Automatic retries'
    _(readme).must_include 'max_retries(n)'
    _(readme).wont_include 'never retries'
  end

  it 'bhv_langruby_072 documents the per-call options, the error request context and the design notes' do
    ['### Per-call options', '### Errors', '`request_path`', '### Response compression', '### Logging',
     '### Streaming', '### Versioning', '### Credentials', '### Optional and nullable are not told apart',
     '### An unknown enum value or discriminator fails loudly', '### Repeated response headers are joined',
     '### `Configuration` and `TransportOptions`'].each do |text|
      _(readme).must_include text
    end
  end

  it 'bhv_langruby_073 names the environment variable of every credential and says every operation has Options' do
    _(readme).must_include '| `adminBasic` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` |'
    _(readme).must_include '| `adminBasic` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` |'
    _(readme).must_include '| `apiKeyHeader` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` |'
    _(readme).must_include '| `apiKeyQuery` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` |'
    _(readme).must_include '| `browserAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` |'
    _(readme).must_include '| `internalApiKey` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` |'
    _(readme).must_include '| `legacyAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` |'
    _(readme).must_include '| `legacyAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` |'
    _(readme).must_include '| `legacyAuth` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` |'
    _(readme).must_include '| `legacyAuth` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` |'
    _(readme).must_include '| `machineAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` |'
    _(readme).must_include '| `machineAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` |'
    _(readme).must_include '| `petStoreBasic` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` |'
    _(readme).must_include '| `petStoreBasic` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` |'
    _(readme).must_include '| `petStoreBearer` | `TOKEN` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` |'
    _(readme).must_include '| `serviceToken` | `TOKEN` | `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` |'
    _(readme).must_include '| `sessionCookie` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` |'
    _(readme).must_include '| `ssoAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` |'
    _(readme).must_include '| `ssoAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` |'
    _(readme).must_include '| `ssoAuth` | `REDIRECT_URI` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` |'
    _(readme).must_include '| `userAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` |'
    _(readme).must_include '| `userAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` |'
    _(readme).must_include '| `userAuth` | `REDIRECT_URI` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` |'
    _(readme).must_include 'an empty variable counts as unset'
    _(readme).must_include 'Every operation takes an optional Options object'
  end

  it 'bhv_langruby_074 documents the credential per security scheme, closing the client and cookie parameters' do
    _(readme).must_include '### Credentials per security scheme'
    _(readme).must_include '`pet_store_basic:`'
    _(readme).must_include '`admin_basic:`'
    _(readme).must_include '`pet_store_bearer:`'
    _(readme).must_include '`service_token:`'
    _(readme).must_include '`api_key_header:`'
    _(readme).must_include '`internal_api_key:`'
    _(readme).must_include '`api_key_query:`'
    _(readme).must_include '`session_cookie:`'
    _(readme).must_include '`machine_auth:`'
    _(readme).must_include '`legacy_auth:`'
    _(readme).must_include '`user_auth:`'
    _(readme).must_include '`browser_auth:`'
    _(readme).must_include '`sso_auth:`'
    _(readme).must_include 'there is no per-call override'
    _(readme).must_include '### Closing the client'
    _(readme).must_include 'a request already in flight finishes'
    _(readme).must_include '### Cookie parameters'
    _(readme).must_include 'RFC 6265 cookie-octet'
  end

  it 'bhv_mocking_009 testing your code names the group interface and the client interface' do
    start = readme.index("\n### Testing your code\n")
    _(start).wont_be_nil
    finish = readme.index("\n### ", start.to_i + 4)
    section = readme[start.to_i...(finish || readme.length)].to_s
    _(section).must_include '_PetApiInterface'
    _(section).must_include '_PetstoreInterface'
  end

  def root_file(name)
    File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), name))
  end

  def ruby_fences(text)
    text.scan(/^```(ruby[^\n]*)\n(.*?)^```/m)
  end

  def quickstart
    root_file('examples/quickstart.rb')
  end

  it 'bhv_examples_001 ships a quickstart that builds a client, calls an operation and closes it' do
    _(quickstart).must_include "require 'petstore_client'"
    _(quickstart).must_include 'Petstore::Client::Petstore.with_token(host, token)'
    _(quickstart).must_include 'client.store.export_orders'
    _(quickstart).must_include 'client.close'
  end

  it 'bhv_examples_002 reads the host and the token from the variables the README names' do
    %w[SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN].each do |name|
      _(quickstart).must_include "ENV.fetch('#{name}')"
      _(readme).must_include name
    end
    _(quickstart).wont_include 'your-token'
  end

  it 'bhv_examples_003 holds the statements of the README Usage fence in the quickstart, in order' do
    usage = readme.split("\n## Usage\n", 2).last.to_s.split("\n## ", 2).first.to_s
    fence = ruby_fences(usage).first
    _(fence).wont_be_nil
    position = 0
    fence.last.each_line do |line|
      stripped = line.strip
      next if stripped.empty? || stripped.start_with?('require ')

      found = quickstart.index(stripped, position)
      _(found).wont_be_nil stripped
      position = found + stripped.length
    end
  end

  it 'bhv_examples_004 declares the kind of every ruby fence of README.md and SKILLS.md' do
    %w[README.md SKILLS.md].each do |name|
      ruby_fences(root_file(name)).map(&:first).each do |info|
        _(['ruby file', 'ruby statements']).must_include info, name
      end
    end
  end

  it 'bhv_examples_005 leaves no placeholder line in a ruby fence of README.md and SKILLS.md' do
    %w[README.md SKILLS.md].each do |name|
      ruby_fences(root_file(name)).map(&:last).each do |body|
        body.each_line do |line|
          stripped = line.strip
          _(['...', '# ...']).wont_include stripped, name
          _(stripped).wont_match(/\A<\w+>\z/, name)
          _(stripped).wont_match(/<[A-Z]\w*>/, name)
        end
      end
    end
  end

  it 'bhv_examples_006 has Makefile targets that check and run the examples directory' do
    makefile = root_file('Makefile')
    _(makefile).must_include "\nexamples: install\n"
    _(makefile).must_include "\nexamples-run: examples\n"
    _(makefile).must_include "find examples -name '*.rb'"
  end
end
