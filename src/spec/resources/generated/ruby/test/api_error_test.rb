# frozen_string_literal: true

require 'test_helper'

describe Petstore::Client::Errors::ApiError do
  parallelize_me!

  it 'exposes status_code, response_body, response_headers and error_body' do
    err = Petstore::Client::Errors::ApiError.new(
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
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 0,
      message: 'connection reset',
      response_headers: nil,
      response_body: nil
    )

    assert_nil(err.response_headers)
    assert_nil(err.response_body)
  end

  it 'is a StandardError subclass with a message' do
    err = Petstore::Client::Errors::ApiError.new(status_code: 500, message: 'boom')

    _(err).must_be_kind_of(StandardError)
    _(err.message).wont_be_empty
  end

  # Unified exception hierarchy: every SDK-thrown error must reach the
  # branded root (::Petstore::Client::Errors::OpenAPIError) so a single rescue catches them all.
  it 'roots ApiError at the branded base' do
    err = Petstore::Client::Errors::ApiError.new(status_code: 500, message: 'boom')

    _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
  end

  it 'chains a typed status error up through ClientError, ApiError and the branded base' do
    err = Petstore::Client::Errors::BadRequestError.new(message: 'bad input')

    _(err).must_be_kind_of(Petstore::Client::Errors::ClientError)
    _(err).must_be_kind_of(Petstore::Client::Errors::ApiError)
    _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
  end

  it 'chains NetworkTimeoutError up through NetworkError and ApiError with status 0' do
    err = Petstore::Client::Errors::NetworkTimeoutError.new(message: 'timed out')

    _(err).must_be_kind_of(Petstore::Client::Errors::NetworkError)
    _(err).must_be_kind_of(Petstore::Client::Errors::ApiError)
    _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
    _(err.status_code).must_equal(0)
    _(Petstore::Client::Errors::NetworkError.new(message: 'refused').status_code).must_equal(0)
  end

  it 'defaults status_code to 0 when no HTTP response was received' do
    _(Petstore::Client::Errors::ApiError.new('connection reset').status_code).must_equal(0)
  end

  it 'roots SerializationError at the branded base' do
    err = Petstore::Client::Errors::SerializationError.new('boom')

    _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
  end

  it 'roots OAuth2 token-manager errors at the branded base' do
    token_err = Petstore::Client::Errors::OAuth2TokenError.new('missing access_token')
    server_err = Petstore::Client::Errors::OAuth2ServerError.new(400, 'invalid_grant', nil, nil, '{}')

    _(token_err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
    _(token_err).wont_be_kind_of(Petstore::Client::Errors::ApiError)
    _(server_err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
    _(server_err).wont_be_kind_of(Petstore::Client::Errors::ApiError)
  end

  # Requiring the gem loads the whole error tree, every class in Errors,
  # whatever security schemes the spec declares.
  it 'loads every error class into the Errors namespace' do
    %w[
      OpenAPIError ApiError ClientError ServerError BadRequestError UnauthorizedError
      ForbiddenError NotFoundError ConflictError UnprocessableEntityError
      InternalServerError NetworkError NetworkTimeoutError SerializationError
      OAuth2ServerError OAuth2TokenError
    ].each do |name|
      klass = Petstore::Client::Errors.const_get(name, false)
      _(klass).must_be_kind_of(Class)
      _(klass <= StandardError).must_equal(true)
    end
  end

  {
    400 => Petstore::Client::Errors::BadRequestError,
    401 => Petstore::Client::Errors::UnauthorizedError,
    403 => Petstore::Client::Errors::ForbiddenError,
    404 => Petstore::Client::Errors::NotFoundError,
    409 => Petstore::Client::Errors::ConflictError,
    422 => Petstore::Client::Errors::UnprocessableEntityError,
    418 => Petstore::Client::Errors::ClientError,
    500 => Petstore::Client::Errors::InternalServerError,
    503 => Petstore::Client::Errors::ServerError,
    302 => Petstore::Client::Errors::ApiError
  }.each do |status, expected|
    it "from_response maps #{status} to #{expected.name.split('::').last}" do
      err = Petstore::Client::Errors::ApiError.from_response(status, { 'x-request-id' => 'abc' }, '{"code":7}')

      _(err).must_be_instance_of(expected)
      _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
      _(err.status_code).must_equal(status)
      _(err.response_headers).must_equal({ 'x-request-id' => 'abc' })
      _(err.response_body).must_equal('{"code":7}')
      _(err.error_body).must_equal({ 'code' => 7 })
    end
  end

  it 'from_response keeps a non-JSON body raw without an error body' do
    err = Petstore::Client::Errors::ApiError.from_response(500, {}, '<html>oops</html>')

    _(err).must_be_instance_of(Petstore::Client::Errors::InternalServerError)
    _(err.response_headers).must_equal({})
    _(err.response_body).must_equal('<html>oops</html>')
    _(err.error_body).must_be_nil
  end

  it 'typed_error_body deserializes the body into the typed model' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 400,
      response_body: '{"id":42,"name":"Dogs"}',
      response_headers: {}
    )

    body = err.typed_error_body('Category')
    _(body).must_be_kind_of(Petstore::Client::Models::Category)
    _(body.id).must_equal(42)
    _(body.name).must_equal('Dogs')
  end

  it 'typed_error_body returns nil when there is no response body' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 500,
      response_body: '',
      response_headers: {}
    )

    assert_nil(err.typed_error_body('Category'))
  end

  it 'typed_error_body ignores extraneous fields not on the model' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 422,
      response_body: '{"id":1,"name":"Cat","extra":"drop-me"}',
      response_headers: {}
    )

    body = err.typed_error_body('Category')
    _(body).must_be_kind_of(Petstore::Client::Models::Category)
    _(body.id).must_equal(1)
    _(body.name).must_equal('Cat')
  end
end
