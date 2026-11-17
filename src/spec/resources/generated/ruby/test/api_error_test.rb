# frozen_string_literal: true

require 'test_helper'

describe Petstore::Client::Errors::ApiError do
  parallelize_me!

  it 'bhv_errors_004 exposes status_code, response_body, response_headers and error_body' do
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

  it 'bhv_errors_021 nil headers and body mark transport-no-response' do
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

  it 'bhv_errors_019 is a StandardError subclass with a message' do
    err = Petstore::Client::Errors::ApiError.new(status_code: 500, message: 'boom')

    _(err).must_be_kind_of(StandardError)
    _(err.message).wont_be_empty
  end

  it 'bhv_errors_022 omits the headers and body sections when they are empty' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 404, message: 'missing', response_headers: {}, response_body: ''
    )

    _(err.message).must_equal("missing\nHTTP status code: 404")
  end

  it 'omits the headers and body sections when they are nil' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 0, message: 'connection reset', response_headers: nil, response_body: nil
    )

    _(err.message).must_equal('connection reset')
  end

  it 'bhv_errors_024 renders the headers and body sections when they carry content' do
    headers = { 'x-id' => '1' }
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 500, message: 'boom', response_headers: headers, response_body: 'oops'
    )

    _(err.message).must_equal("boom\nHTTP status code: 500\nResponse headers: {\"x-id\":\"1\"}\nResponse body: oops")
  end

  it 'cuts a long response body at 1 KiB and counts the bytes left out' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 502, message: 'bad gateway', response_headers: nil, response_body: 'a' * 3000
    )

    _(err.message).must_equal("bad gateway\nHTTP status code: 502\nResponse body: #{'a' * 1024}... (1976 more bytes)")
    _(err.response_body.bytesize).must_equal 3000
  end

  it 'bhv_errors_001 escapes control characters in the rendered response body' do
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 500, message: 'boom', response_headers: nil, response_body: "a\r\nb\tc\u0001"
    )

    _(err.message).must_equal("boom\nHTTP status code: 500\nResponse body: a\\r\\nb\\tc\\x01")
  end

  it 'bhv_errors_023 redacts credential headers and Set-Cookie in the rendered headers only' do
    headers = { 'set-cookie' => 'sid=abc', 'x-api-key' => 'k', 'x-id' => '1' }
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 500, message: 'boom', response_headers: headers, response_body: nil
    )

    _(err.message).must_equal("boom\nHTTP status code: 500\nResponse headers: {\"set-cookie\":\"***\",\"x-api-key\":\"***\",\"x-id\":\"1\"}")
    _(err.response_headers['set-cookie']).must_equal 'sid=abc'
  end

  it 'masks the challenge headers in the rendered headers' do
    headers = { 'WWW-Authenticate' => 'Bearer realm="x"', 'Proxy-Authenticate' => 'Basic', 'x-id' => '1' }
    err = Petstore::Client::Errors::ApiError.new(
      status_code: 401, message: 'denied', response_headers: headers, response_body: nil
    )

    _(err.message).must_include('"Proxy-Authenticate":"***","WWW-Authenticate":"***","x-id":"1"')
    _(err.response_headers['WWW-Authenticate']).must_equal 'Bearer realm="x"'
  end

  it 'substitutes the default text for an empty detail message' do
    _(Petstore::Client::Errors::ApiError.new('').message).must_equal('Error message: the server returns an error')
    _(Petstore::Client::Errors::ApiError.new(message: '').message).must_equal('Error message: the server returns an error')
    _(Petstore::Client::Errors::ApiError.new.message).must_equal('Error message: the server returns an error')
  end

  it 'bhv_errors_012 detail_message returns the bare detail without the rendered context' do
    err = Petstore::Client::Errors::ApiError.from_response(404, { 'x-id' => '1' }, 'gone')

    _(err.detail_message).must_equal('API returned status code 404')
    _(err.message).must_include("\nHTTP status code: 404")
    _(err.to_s).must_equal(err.message)
  end

  it 'detail_message falls back to the default text for a nil or empty detail' do
    _(Petstore::Client::Errors::ApiError.new(status_code: 500).detail_message).must_equal('Error message: the server returns an error')
    _(Petstore::Client::Errors::ApiError.new('').detail_message).must_equal('Error message: the server returns an error')
  end

  # Unified exception hierarchy: every SDK-thrown error must reach the
  # branded root (::Petstore::Client::Errors::OpenAPIError) so a single rescue catches them all.
  it 'bhv_errors_018 roots ApiError at the branded base' do
    err = Petstore::Client::Errors::ApiError.new(status_code: 500, message: 'boom')

    _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
  end

  it 'chains a typed status error up through ClientError, ApiError and the branded base' do
    err = Petstore::Client::Errors::BadRequestError.new(message: 'bad input')

    _(err).must_be_kind_of(Petstore::Client::Errors::ClientError)
    _(err).must_be_kind_of(Petstore::Client::Errors::ApiError)
    _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
  end

  it 'bhv_errors_020 chains NetworkTimeoutError up through NetworkError and ApiError with status 0' do
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

  it 'bhv_errors_025 roots SerializationError at the branded base' do
    err = Petstore::Client::Errors::SerializationError.new('boom')

    _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
  end

  it 'bhv_errors_046 OAuth2ServerError has three message forms' do
    raw = Petstore::Client::Errors::OAuth2ServerError.new(502, nil, nil, nil, '<html>bad gateway</html>')
    code = Petstore::Client::Errors::OAuth2ServerError.new(400, 'invalid_grant', nil, nil, '{}')
    described = Petstore::Client::Errors::OAuth2ServerError.new(400, 'invalid_grant', 'refresh token expired', nil, '{}')

    _(raw.message).must_equal('Token request failed with status 502: <html>bad gateway</html>')
    _(code.message).must_equal('Token request failed with status 400: invalid_grant')
    _(described.message).must_equal('Token request failed with status 400: invalid_grant -- refresh token expired')
  end

  it 'bhv_errors_047 a typed API error is an ApiError and the branded root' do
    err = Petstore::Client::Errors::ApiError.from_response(500, {}, '{}')

    _(err).must_be_kind_of(Petstore::Client::Errors::InternalServerError)
    _(err).must_be_kind_of(Petstore::Client::Errors::ApiError)
    _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
  end

  it 'bhv_errors_045 roots OAuth2 token-manager errors at the branded base' do
    token_err = Petstore::Client::Errors::OAuth2TokenError.new('missing access_token')
    server_err = Petstore::Client::Errors::OAuth2ServerError.new(400, 'invalid_grant', nil, nil, '{}')

    _(token_err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
    _(token_err).wont_be_kind_of(Petstore::Client::Errors::ApiError)
    _(server_err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
    _(server_err).wont_be_kind_of(Petstore::Client::Errors::ApiError)
  end

  # Requiring the gem loads the whole error tree, every class in Errors,
  # whatever security schemes the spec declares.
  it 'bhv_errors_002 loads every error class into the Errors namespace' do
    %w[
      OpenAPIError ApiError ClientError ServerError BadRequestError UnauthorizedError
      ForbiddenError NotFoundError ConflictError UnprocessableEntityError TooManyRequestsError
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
    429 => Petstore::Client::Errors::TooManyRequestsError,
    418 => Petstore::Client::Errors::ClientError,
    500 => Petstore::Client::Errors::InternalServerError,
    503 => Petstore::Client::Errors::ServerError,
    302 => Petstore::Client::Errors::ApiError
  }.each do |status, expected|
    it "bhv_errors_006 from_response maps #{status} to #{expected.name.split('::').last}" do
      err = Petstore::Client::Errors::ApiError.from_response(status, { 'x-request-id' => 'abc' }, '{"code":7}')

      _(err).must_be_instance_of(expected)
      _(err).must_be_kind_of(::Petstore::Client::Errors::OpenAPIError)
      _(err.status_code).must_equal(status)
      _(err.response_headers).must_equal({ 'x-request-id' => 'abc' })
      _(err.response_body).must_equal('{"code":7}')
      _(err.error_body).must_equal({ 'code' => 7 })
    end
  end

  it 'from_response treats a status at or above 600 as the base ApiError' do
    # The 5xx arm is bounded at < 600: a status outside 400-599 yields the
    # base ApiError, never ServerError.
    err = Petstore::Client::Errors::ApiError.from_response(600, { 'x-request-id' => 'abc' }, nil)

    _(err).must_be_instance_of(Petstore::Client::Errors::ApiError)
    _(err).wont_be_kind_of(Petstore::Client::Errors::ServerError)
    _(err.status_code).must_equal(600)
  end

  it 'bhv_errors_009 from_response refuses a deeply nested error body' do
    # The error body is attacker-controlled, so parsing is depth-capped: an
    # over-deep payload leaves error_body nil instead of exhausting the stack.
    body = ('[' * 2000) + (']' * 2000)

    err = Petstore::Client::Errors::ApiError.from_response(400, {}, body)

    _(err).must_be_instance_of(Petstore::Client::Errors::BadRequestError)
    _(err.response_body).must_equal(body)
    _(err.error_body).must_be_nil
  end

  it 'bhv_errors_008 from_response parses a BOM-prefixed error body' do
    err = Petstore::Client::Errors::ApiError.from_response(400, {}, "\uFEFF{\"code\":7}")

    _(err.error_body).must_equal('code' => 7)
  end

  it 'bhv_errors_005 from_response keeps a non-JSON body raw without an error body' do
    err = Petstore::Client::Errors::ApiError.from_response(500, {}, '<html>oops</html>')

    _(err).must_be_instance_of(Petstore::Client::Errors::InternalServerError)
    _(err.response_headers).must_equal({})
    _(err.response_body).must_equal('<html>oops</html>')
    _(err.error_body).must_be_nil
  end

  it 'bhv_errors_013 typed_error_body deserializes the body into the typed model' do
    # Built through from_response, the way a real response reaches a caller:
    # the typed body must come out of the raw body, not out of whatever the
    # generic parse happened to leave behind.
    err = Petstore::Client::Errors::ApiError.from_response(400, {}, '{"id":42,"name":"Dogs"}')

    body = err.typed_error_body('Category')
    _(body).must_be_kind_of(Petstore::Client::Models::Category)
    _(body.id).must_equal(42)
    _(body.name).must_equal('Dogs')
  end

  it 'bhv_errors_015 typed_error_body returns nil when there is no response body' do
    err = Petstore::Client::Errors::ApiError.from_response(500, {}, '')

    assert_nil(err.typed_error_body('Category'))
  end

  it 'bhv_errors_016 typed_error_body returns nil when the response body is absent' do
    err = Petstore::Client::Errors::ApiError.from_response(0, nil, nil)

    assert_nil(err.typed_error_body('Category'))
  end

  it 'bhv_errors_014 typed_error_body ignores extraneous fields not on the model' do
    err = Petstore::Client::Errors::ApiError.from_response(422, {}, '{"id":1,"name":"Cat","extra":"drop-me"}')

    body = err.typed_error_body('Category')
    _(body).must_be_kind_of(Petstore::Client::Models::Category)
    _(body.id).must_equal(1)
    _(body.name).must_equal('Cat')
  end

  it 'OAuth2ServerError names the status and the raw body when the body carries no error code' do
    err = Petstore::Client::Errors::OAuth2ServerError.new(502, nil, nil, nil, '<html>bad gateway</html>')

    _(err.message).must_equal('Token request failed with status 502: <html>bad gateway</html>')
  end

  it 'OAuth2ServerError names the status and the error code' do
    err = Petstore::Client::Errors::OAuth2ServerError.new(400, 'invalid_grant', nil, nil, '{}')

    _(err.message).must_equal('Token request failed with status 400: invalid_grant')
  end

  it 'OAuth2ServerError names the status, the error code and the description' do
    err = Petstore::Client::Errors::OAuth2ServerError.new(400, 'invalid_grant', 'refresh token expired', nil, '{}')

    _(err.message).must_equal('Token request failed with status 400: invalid_grant -- refresh token expired')
  end

  it 'bhv_errors_010 from_response leaves a scalar JSON error body as the parsed scalar' do
    err = Petstore::Client::Errors::ApiError.from_response(500, {}, '"upstream unavailable"')

    _(err.response_body).must_equal('"upstream unavailable"')
    _(err.error_body).must_equal('upstream unavailable')
  end

  # Raises a status-specific error from inside a rescue, so Ruby records the
  # rescued exception as its cause.
  def status_error_with_cause(klass, cause)
    raise cause
  rescue StandardError
    begin
      raise klass.new(message: 'failed')
    rescue klass => e
      e
    end
  end

  it 'bhv_errors_003 takes a cause for every status-specific error through Exception#cause' do
    inner = StandardError.new('transport')
    [
      Petstore::Client::Errors::BadRequestError, Petstore::Client::Errors::UnauthorizedError,
      Petstore::Client::Errors::NotFoundError, Petstore::Client::Errors::InternalServerError
    ].each do |klass|
      _(status_error_with_cause(klass, inner).cause).must_be_same_as inner
      _(klass.new(message: 'failed').cause).must_be_nil
    end
  end

  it 'bhv_errors_007 records the request method and path without the query string' do
    err = Petstore::Client::Errors::ApiError.from_response(404, {}, nil,
                                                           request_method: 'get', request_path: '/items/1?token=abc')

    _(err.request_method).must_equal 'GET'
    _(err.request_path).must_equal '/items/1'
  end

  it 'bhv_errors_011 names the request in the message and in the request fields' do
    err = Petstore::Client::Errors::ApiError.from_response(404, {}, nil,
                                                           request_method: 'delete', request_path: '/items/1')

    _(err.request_method).must_equal 'DELETE'
    _(err.request_path).must_equal '/items/1'
    _(err.message).must_equal "API returned status code 404\nHTTP status code: 404\nRequest: DELETE /items/1"
  end

  it 'bhv_errors_026 renders the request on its own line and never carries the query string' do
    err = Petstore::Client::Errors::ApiError.from_response(500, {}, nil,
                                                           request_method: 'get', request_path: '/items?api_key=secret')

    _(err.message.split("\n")).must_include 'Request: GET /items'
    _(err.message).wont_include 'secret'
  end

  it 'bhv_errors_030 typed_error_body returns nil when the response has no body at all' do
    err = Petstore::Client::Errors::ApiError.from_response(500, {}, nil)

    assert_nil(err.typed_error_body('Category'))
  end

  it 'bhv_errors_017 typed_error_body raises SerializationError for a body that is not the model' do
    err = Petstore::Client::Errors::ApiError.from_response(400, {}, '<html>oops</html>')

    raised = assert_raises(Petstore::Client::Errors::SerializationError) { err.typed_error_body('Category') }
    _(raised).must_be_instance_of(Petstore::Client::Errors::SerializationError)
  end
end
