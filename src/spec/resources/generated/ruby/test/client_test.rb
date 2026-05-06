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

  it 'API groups are accessible' do
    authenticator = PetstoreClient::Auth::BearerAuthenticator.new('/api/v3', 'test-token')

    client = PetstoreClient::Client.new(authenticator)

    _(client.pet).wont_be_nil
    _(client.store).wont_be_nil
  end
end
