# frozen_string_literal: true

require 'test_helper'

describe PetstoreClient::ApiError do
  parallelize_me!

  it 'exposes status_code, response_body, response_headers and error_body' do
    err = PetstoreClient::ApiError.new(
      status_code: 404,
      message: 'not found',
      response_headers: { 'content-type' => 'application/json' },
      response_body: '{"id":7,"name":"missing"}',
      error_body: nil
    )

    _(err.status_code).must_equal(404)
    _(err.response_body).must_equal('{"id":7,"name":"missing"}')
    _(err.response_headers['content-type']).must_equal('application/json')
    assert_nil(err.error_body)
  end

  it 'nil headers and body mark transport-no-response' do
    # apierror-responsebody-headers-nullable-split: nil is distinct from an
    # empty header map / empty body so a pre-response transport failure can
    # be encoded.
    err = PetstoreClient::ApiError.new(
      status_code: 0,
      message: 'connection reset',
      response_headers: nil,
      response_body: nil
    )

    assert_nil(err.response_headers)
    assert_nil(err.response_body)
  end

  it 'is a StandardError subclass with a message' do
    err = PetstoreClient::ApiError.new(status_code: 500, message: 'boom')

    _(err).must_be_kind_of(StandardError)
    _(err.message).wont_be_empty
  end

  # Unified exception hierarchy: every SDK-thrown error must reach the
  # branded root (StandardError) so a single rescue catches them all.
  it 'roots ApiError at the branded base' do
    err = PetstoreClient::ApiError.new(status_code: 500, message: 'boom')

    _(err).must_be_kind_of(StandardError)
  end

  it 'chains a typed status error up through ClientError, ApiError and the branded base' do
    err = PetstoreClient::Errors::BadRequestError.new(message: 'bad input')

    _(err).must_be_kind_of(PetstoreClient::Errors::ClientError)
    _(err).must_be_kind_of(PetstoreClient::ApiError)
    _(err).must_be_kind_of(StandardError)
  end

  it 'roots SerializationError at the branded base' do
    err = PetstoreClient::SerializationError.new('boom')

    _(err).must_be_kind_of(StandardError)
  end

  it 'roots SchemaMismatchError at the branded base' do
    err = PetstoreClient::SchemaMismatchError.new('no variant matched')

    _(err).must_be_kind_of(StandardError)
  end

  it 'roots OAuth2 token-manager errors at the branded base' do
    token_err = PetstoreClient::Auth::OAuth::OAuth2TokenError.new('missing access_token')
    server_err = PetstoreClient::Auth::OAuth::OAuth2ServerError.new(400, 'invalid_grant', nil, nil, '{}')

    _(token_err).must_be_kind_of(PetstoreClient::ApiError)
    _(token_err).must_be_kind_of(StandardError)
    _(server_err).must_be_kind_of(PetstoreClient::ApiError)
    _(server_err).must_be_kind_of(StandardError)
  end

  it 'typed_error_body deserializes the body into the typed model' do
    err = PetstoreClient::ApiError.new(
      status_code: 400,
      response_body: '{"id":42,"name":"Dogs"}',
      response_headers: {}
    )

    body = err.typed_error_body('Category')
    _(body).must_be_kind_of(PetstoreClient::Models::Category)
    _(body.id).must_equal(42)
    _(body.name).must_equal('Dogs')
  end

  it 'typed_error_body returns nil when there is no response body' do
    err = PetstoreClient::ApiError.new(
      status_code: 500,
      response_body: '',
      response_headers: {}
    )

    assert_nil(err.typed_error_body('Category'))
  end

  it 'typed_error_body ignores extraneous fields not on the model' do
    err = PetstoreClient::ApiError.new(
      status_code: 422,
      response_body: '{"id":1,"name":"Cat","extra":"drop-me"}',
      response_headers: {}
    )

    body = err.typed_error_body('Category')
    _(body).must_be_kind_of(PetstoreClient::Models::Category)
    _(body.id).must_equal(1)
    _(body.name).must_equal('Cat')
  end
end
