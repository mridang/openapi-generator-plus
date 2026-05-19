# frozen_string_literal: true

require 'test_helper'

describe PetstoreClient::Client do
  it 'constructs with authenticator only' do
    authenticator = PetstoreClient::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = PetstoreClient::Client.new(authenticator)

    _(client).wont_be_nil
  end

  it 'constructs with authenticator and nil transport options' do
    authenticator = PetstoreClient::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = PetstoreClient::Client.new(authenticator, nil)

    _(client).wont_be_nil
  end

  it 'constructs with authenticator and transport options' do
    authenticator = PetstoreClient::Auth::BearerAuthenticator.new('/api/v3', 'test-token')
    transport = PetstoreClient::TransportOptions.builder.build

    client = PetstoreClient::Client.new(authenticator, transport)

    _(client).wont_be_nil
  end

  it 'ApiKeyAuthenticator HEADER rejects CR/LF and non-ASCII' do
    # RFC 7230 §3.2.6 — header field-value is HTAB / SP / VCHAR.
    # The HEADER location must reject anything outside printable ASCII
    # + TAB to prevent header injection (CR/LF) and silent UTF-8
    # mangling that varies per HTTP lib.
    _(-> {
      PetstoreClient::Auth::ApiKeyAuthenticator.new('/api/v3', 'X-Api-Key', "abc\r\nInjected: yes",
                                                    PetstoreClient::Auth::ApiKeyLocation::HEADER)
    }).must_raise ArgumentError

    _(-> {
      PetstoreClient::Auth::ApiKeyAuthenticator.new('/api/v3', 'X-Api-Key', 'kéy',
                                                    PetstoreClient::Auth::ApiKeyLocation::HEADER)
    }).must_raise ArgumentError

    # Non-header locations accept arbitrary chars.
    query_auth = PetstoreClient::Auth::ApiKeyAuthenticator.new(
      '/api/v3', 'api_key', 'kéy', PetstoreClient::Auth::ApiKeyLocation::QUERY
    )
    _(query_auth.query_params).must_equal({ 'api_key' => 'kéy' })
  end

  it 'API groups are accessible' do
    authenticator = PetstoreClient::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = PetstoreClient::Client.new(authenticator)

    _(client.pet).wont_be_nil
    _(client.store).wont_be_nil
  end
end
