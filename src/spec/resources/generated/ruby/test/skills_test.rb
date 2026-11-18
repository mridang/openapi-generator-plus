# frozen_string_literal: true

require 'test_helper'

# Guards that the SKILLS.md examples can be run as written.
describe 'SKILLS' do
  parallelize_me!

  def error_handling_example
    skills = File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'SKILLS.md'))
    section = skills.split('## Error Handling', 2).last.to_s
    section.match(/```ruby file\n(.*?)```/m)&.captures&.first.to_s
  end

  it 'defines the Error Handling example as a method taking the operation arguments' do
    _(error_handling_example).must_include 'def add_pet_or_report(client, pet)'
    _(error_handling_example).must_include '  client.pet.add_pet(pet)'
  end

  it 'leaves no undefined variable in the Error Handling example' do
    _(error_handling_example).wont_include '(request)'
  end

  it 'bhv_skills_001 names the root error type and the non-HTTP error classes' do
    skills = File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'SKILLS.md'))
    section = skills.split('## Error Handling', 2).last.to_s.split('## Configuration', 2).first.to_s
    %w[SerializationError OAuth2ServerError OAuth2TokenError].each do |name|
      _(section).must_include "`#{name}`"
    end
    _(section).must_include '::Petstore::Client::Errors::OpenAPIError'
  end

  it 'ends the Error Handling example with a rescue of the root error type' do
    _(error_handling_example).must_include 'rescue Petstore::Client::Errors::SerializationError => e'
    _(error_handling_example).must_include 'rescue ::Petstore::Client::Errors::OpenAPIError => e'
  end

  it 'bhv_skills_002 documents the retry option in the Configuration section' do
    skills = File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'SKILLS.md'))
    section = skills.split('## Configuration', 2).last.to_s
    _(section).must_include '### Retries'
    _(section).must_include 'max_retries(0)'
    _(section).must_include '`Retry-After`'
  end

  it 'lists the transport subsections in the canonical order' do
    skills = File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'SKILLS.md'))
    section = skills.split('## Configuration', 2).last.to_s.split('## API Methods', 2).first.to_s
    headings = section.scan(/^### (.+)$/).flatten
    wanted = ['Custom Transport Options', 'Custom HTTP client', 'Retries', 'Cancellation']
    _(headings.select { |heading| wanted.include?(heading) }).must_equal wanted
  end

  it 'parses the Error Handling example' do
    _(RubyVM::InstructionSequence.compile(error_handling_example)).wont_be_nil
  end

  it 'documents the environment variable of every credential and the per-call options' do
    skills = File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'SKILLS.md'))
    _(skills).must_include '| `adminBasic` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` |'
    _(skills).must_include '| `adminBasic` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` |'
    _(skills).must_include '| `apiKeyHeader` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` |'
    _(skills).must_include '| `apiKeyQuery` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` |'
    _(skills).must_include '| `browserAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` |'
    _(skills).must_include '| `internalApiKey` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` |'
    _(skills).must_include '| `legacyAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` |'
    _(skills).must_include '| `legacyAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` |'
    _(skills).must_include '| `legacyAuth` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` |'
    _(skills).must_include '| `legacyAuth` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` |'
    _(skills).must_include '| `machineAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` |'
    _(skills).must_include '| `machineAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` |'
    _(skills).must_include '| `petStoreBasic` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` |'
    _(skills).must_include '| `petStoreBasic` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` |'
    _(skills).must_include '| `petStoreBearer` | `TOKEN` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` |'
    _(skills).must_include '| `serviceToken` | `TOKEN` | `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` |'
    _(skills).must_include '| `sessionCookie` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` |'
    _(skills).must_include '| `ssoAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` |'
    _(skills).must_include '| `ssoAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` |'
    _(skills).must_include '| `ssoAuth` | `REDIRECT_URI` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` |'
    _(skills).must_include '| `userAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` |'
    _(skills).must_include '| `userAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` |'
    _(skills).must_include '| `userAuth` | `REDIRECT_URI` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` |'
    _(skills).must_include '### Credentials from the environment'
    _(skills).must_include '### Per-call options'
  end

  it 'documents the credential per security scheme, closing the client and cookie parameters' do
    skills = File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'SKILLS.md'))
    _(skills).must_include '### Credentials per security scheme'
    _(skills).must_include '`pet_store_basic:`'
    _(skills).must_include '`admin_basic:`'
    _(skills).must_include '`pet_store_bearer:`'
    _(skills).must_include '`service_token:`'
    _(skills).must_include '`api_key_header:`'
    _(skills).must_include '`internal_api_key:`'
    _(skills).must_include '`api_key_query:`'
    _(skills).must_include '`session_cookie:`'
    _(skills).must_include '`machine_auth:`'
    _(skills).must_include '`legacy_auth:`'
    _(skills).must_include '`user_auth:`'
    _(skills).must_include '`browser_auth:`'
    _(skills).must_include '`sso_auth:`'
    _(skills).must_include 'there is no per-call override'
    _(skills).must_include '### Closing the client'
    _(skills).must_include 'a request already in flight finishes'
    _(skills).must_include '### Cookie parameters'
    _(skills).must_include 'RFC 6265 cookie-octet'
  end
end
