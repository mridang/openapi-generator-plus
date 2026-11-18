# frozen_string_literal: true

require 'test_helper'

# Guards the claims the README makes about the SDK's own API.
describe 'README' do
  parallelize_me!

  def readme
    File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'README.md'))
  end

  it 'names the real proxy setter in the SOCKS section' do
    _(readme).must_include '`TransportOptions.builder.proxy(url)` accepts only `http://` and `https://` URLs.'
    _(readme).wont_include 'TransportOptions.proxy()'
  end

  it 'documents the schema constraints and uniqueItems ordering' do
    _(readme).must_include '### Schema constraints'
    _(readme).must_include '`pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,'
    _(readme).must_include '`uniqueItems` arrays'
  end

  it 'makes no claim about how the other SDKs treat a discriminator no-match' do
    _(readme).must_include 'raises'
    _(readme).wont_include 'This matches Python'
  end

  it 'lists the unsupported features and says uniqueItems order is unspecified' do
    ['### XML bodies and `nodeType`', '### Link Objects', '### Mutual TLS security schemes'].each do |heading|
      _(readme).must_include heading
    end
    _(readme).wont_include 'ascending natural order'
    _(readme).must_include '## Development'
  end

  it 'documents the retry option and no longer says requests are never retried' do
    _(readme).must_include '### Automatic retries'
    _(readme).must_include 'max_retries(n)'
    _(readme).wont_include 'never retries'
  end

  it 'documents the per-call options, the error request context and the design notes' do
    ['### Per-call options', '### Errors', '`request_path`', '### Response compression', '### Logging',
     '### Streaming', '### Versioning', '### Credentials', '### Optional and nullable are not told apart',
     '### An unknown enum value or discriminator fails loudly', '### Repeated response headers are joined',
     '### `Configuration` and `TransportOptions`'].each do |text|
      _(readme).must_include text
    end
  end

  it 'names the environment variable of every credential and says every operation has Options' do
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

  it 'documents the credential per security scheme, closing the client and cookie parameters' do
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
end
