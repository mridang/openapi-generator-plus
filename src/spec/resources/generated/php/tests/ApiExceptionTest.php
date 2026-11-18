<?php

declare(strict_types=1);

use PetstoreClient\Errors\ApiException;
use PetstoreClient\Errors\SerializationException;
use PetstoreClient\Errors\OpenAPIException;
use PetstoreClient\Models\Category;

test('bhv errors 004 exposes status code, message, body, headers and error body', function (): void {
    $ex = new ApiException(
        404,
        'not found',
        ['content-type' => 'application/json'],
        '{"id":7,"name":"missing"}',
    );

    expect($ex->getStatusCode())->toBe(404);
    expect($ex->getMessage())->toBe('not found');
    expect($ex->getResponseBody())->toBe('{"id":7,"name":"missing"}');
    expect($ex->getResponseHeaders())->toBe(['content-type' => 'application/json']);
    expect($ex->getErrorBody())->toBeNull();
});

test('bhv errors 021 null headers and body mark transport-no-response', function (): void {
    // apierror-responsebody-headers-nullable-split: null is distinct from an
    // empty header map / empty body so a pre-response transport failure can
    // be encoded.
    $ex = new ApiException(
        0,
        'connection reset',
    );

    expect($ex->getResponseHeaders())->toBeNull();
    expect($ex->getResponseBody())->toBeNull();
});

test('bhv errors 019 is an Exception subclass with status in its string form', function (): void {
    // getMessage() stays the detail message the exception was constructed
    // with; the response context is rendered by the string conversion, one
    // labelled field per line.
    $ex = new ApiException(500, 'boom', ['x-request-id' => 'abc'], 'the body');

    expect($ex)->toBeInstanceOf(Exception::class);
    expect($ex->getMessage())->toBe('boom');
    expect((string) $ex)->toBe(
        "boom\nHTTP status code: 500"
        . "\nResponse headers: {\"x-request-id\":\"abc\"}"
        . "\nResponse body: the body"
    );
});

test('bhv errors 001 the string form caps the body, escapes control characters and redacts secret headers', function (): void {
    $ex = new ApiException(
        502,
        'bad gateway',
        ['x-request-id' => 'abc', 'set-cookie' => 'session=1', 'X-Api-Key' => 'k', 'authorization' => 'Bearer t'],
        str_repeat('a', 1024) . "tail\nforged: line"
    );

    $text = (string) $ex;

    expect($text)->toContain('"x-request-id":"abc"');
    expect($text)->toContain('"set-cookie":"***"');
    expect($text)->toContain('"X-Api-Key":"***"');
    expect($text)->toContain('"authorization":"***"');
    expect($text)->not->toContain('Bearer t');
    expect($text)->toContain(str_repeat('a', 1024) . '... (17 more bytes)');
    expect($text)->not->toContain('forged');
    expect($ex->getResponseBody())->toContain("tail\nforged: line");
});

test('a newline in the body is shown escaped, never as a line break', function (): void {
    $ex = new ApiException(500, 'boom', null, "one\r\ntwo");

    expect((string) $ex)->toContain('Response body: one\x0D\x0Atwo');
});

test('bhv errors 018 ApiException extends the SDK root OpenAPIException', function (): void {
    /* One SDK root: ApiException extends OpenAPIException, so a single
     * catch on OpenAPIException covers every API/HTTP error. */
    $ex = new ApiException(500, 'boom');

    expect($ex)->toBeInstanceOf(OpenAPIException::class);
    expect($ex)->toBeInstanceOf(Exception::class);
});

test('a thrown typed error is caught by a OpenAPIException catch', function (): void {
    /* Full chain: UnauthorizedException → ClientException → ApiException
     * → OpenAPIException → \Exception. Catching the root must catch the
     * 401 typed error. */
    $caught = null;
    try {
        throw new \PetstoreClient\Errors\UnauthorizedException('unauthorized');
    } catch (OpenAPIException $e) {
        $caught = $e;
    }

    expect($caught)->toBeInstanceOf(\PetstoreClient\Errors\UnauthorizedException::class);
    expect($caught)->toBeInstanceOf(ApiException::class);
    expect($caught)->toBeInstanceOf(OpenAPIException::class);
    assert($caught instanceof ApiException);
    expect($caught->getStatusCode())->toBe(401);
});

test('bhv errors 025 SerializationException reaches the SDK root OpenAPIException', function (): void {
    /* Unified hierarchy: serialization failures must also be catchable via
     * the single OpenAPIException root, not just the native \RuntimeException. */
    $ex = new SerializationException('serialize failed');

    expect($ex)->toBeInstanceOf(OpenAPIException::class);
    expect($ex)->toBeInstanceOf(Exception::class);
});

test('bhv errors 020 NetworkException and NetworkTimeoutException extend ApiException with status 0', function (): void {
    /* No HTTP response arrived, so the status is 0 and the transport failure
     * is kept as the previous exception. Both sit under ApiException so an
     * existing catch on ApiException keeps catching them. */
    $cause = new \RuntimeException('connection refused');
    $network = new \PetstoreClient\Errors\NetworkException('connection refused', $cause);
    $timeout = new \PetstoreClient\Errors\NetworkTimeoutException('timed out', $cause);

    expect($network)->toBeInstanceOf(ApiException::class);
    expect($network->getStatusCode())->toBe(0);
    expect($network->getPrevious())->toBe($cause);
    expect($timeout)->toBeInstanceOf(\PetstoreClient\Errors\NetworkException::class);
    expect($timeout)->toBeInstanceOf(ApiException::class);
    expect($timeout->getStatusCode())->toBe(0);
    expect($timeout->getPrevious())->toBe($cause);
});

test('bhv errors 003 every status-specific error keeps an optional cause', function (string $class): void {
    $cause = new \RuntimeException('root cause');

    $withCause = new $class('failed', null, null, null, $cause);
    $without = new $class('failed');
    assert($withCause instanceof \Throwable);
    assert($without instanceof \Throwable);

    expect($withCause->getPrevious())->toBe($cause);
    expect($without->getPrevious())->toBeNull();
})->with([
    \PetstoreClient\Errors\BadRequestException::class,
    \PetstoreClient\Errors\UnauthorizedException::class,
    \PetstoreClient\Errors\ForbiddenException::class,
    \PetstoreClient\Errors\NotFoundException::class,
    \PetstoreClient\Errors\ConflictException::class,
    \PetstoreClient\Errors\UnprocessableEntityException::class,
    \PetstoreClient\Errors\TooManyRequestsException::class,
    \PetstoreClient\Errors\InternalServerErrorException::class,
]);

test('every typed API error extends the base ApiException', function (): void {
    /* #5: a caller catching the base ApiException must catch every typed
     * error the SDK throws. ClientException/ServerException extend
     * ApiException directly; the status-specific errors extend those. A
     * catch on ApiException therefore covers them all. */
    $typed = [
        new \PetstoreClient\Errors\BadRequestException('bad request'),
        new \PetstoreClient\Errors\UnauthorizedException('unauthorized'),
        new \PetstoreClient\Errors\ForbiddenException('forbidden'),
        new \PetstoreClient\Errors\NotFoundException('not found'),
        new \PetstoreClient\Errors\ConflictException('conflict'),
        new \PetstoreClient\Errors\UnprocessableEntityException('unprocessable'),
        new \PetstoreClient\Errors\TooManyRequestsException('slow down'),
        new \PetstoreClient\Errors\ClientException(418, 'client error'),
        new \PetstoreClient\Errors\InternalServerErrorException('server error'),
        new \PetstoreClient\Errors\ServerException(503, 'server error'),
    ];

    foreach ($typed as $ex) {
        expect($ex)->toBeInstanceOf(ApiException::class);
    }
});

test('bhv errors 052 a typed error is caught by a base ApiException catch', function (): void {
    $caught = null;
    try {
        throw new \PetstoreClient\Errors\BadRequestException('bad request');
    } catch (ApiException $e) {
        $caught = $e;
    }

    expect($caught)->toBeInstanceOf(\PetstoreClient\Errors\BadRequestException::class);
    expect($caught->getStatusCode())->toBe(400);
});

test('bhv errors 013 getTypedErrorBody deserializes the body into the given class', function (): void {
    // Built through fromResponse, the way a real response reaches a caller:
    // the typed body must come out of the raw body, not out of whatever the
    // generic parse happened to leave behind.
    $ex = ApiException::fromResponse(400, [], '{"id":42,"name":"Dogs"}');

    $typed = $ex->getTypedErrorBody(Category::class);
    expect($typed)->toBeInstanceOf(Category::class);
    assert($typed instanceof Category);
    expect($typed->id)->toBe(42);
    expect($typed->name)->toBe('Dogs');
});

test('bhv errors 015 getTypedErrorBody returns null when there is no response body', function (): void {
    $ex = ApiException::fromResponse(500, [], null);

    expect($ex->getTypedErrorBody(Category::class))->toBeNull();
});

test('bhv errors 014 getTypedErrorBody ignores extraneous fields not on the model', function (): void {
    $ex = ApiException::fromResponse(422, [], '{"id":1,"name":"Cat","extra":"drop-me"}');

    $typed = $ex->getTypedErrorBody(Category::class);
    expect($typed)->toBeInstanceOf(Category::class);
    assert($typed instanceof Category);
    expect($typed->id)->toBe(1);
    expect($typed->name)->toBe('Cat');
});

test('bhv errors 006 fromResponse maps every status to its exception', function (int $status, string $expected): void {
    $ex = ApiException::fromResponse($status, ['x-request-id' => 'abc'], '{"code":"denied"}');

    expect($ex::class)->toBe($expected);
    expect($ex->getStatusCode())->toBe($status);
    expect($ex->getResponseHeaders())->toBe(['x-request-id' => 'abc']);
    expect($ex->getResponseBody())->toBe('{"code":"denied"}');
    expect($ex->getErrorBody())->toBe(['code' => 'denied']);
})->with([
    [400, \PetstoreClient\Errors\BadRequestException::class],
    [401, \PetstoreClient\Errors\UnauthorizedException::class],
    [403, \PetstoreClient\Errors\ForbiddenException::class],
    [404, \PetstoreClient\Errors\NotFoundException::class],
    [409, \PetstoreClient\Errors\ConflictException::class],
    [422, \PetstoreClient\Errors\UnprocessableEntityException::class],
    [429, \PetstoreClient\Errors\TooManyRequestsException::class],
    [418, \PetstoreClient\Errors\ClientException::class],
    [500, \PetstoreClient\Errors\InternalServerErrorException::class],
    [503, \PetstoreClient\Errors\ServerException::class],
    // 302 is below the client range and 600 above the server range: neither
    // is a ClientException or a ServerException, both are the base type.
    [302, ApiException::class],
    [600, ApiException::class],
]);

test('bhv errors 007 fromResponse carries the request method and the path without its query string', function (int $status, string $expected): void {
    $ex = ApiException::fromResponse($status, [], '{}', 'get', 'https://api.example.com/v2/items/7?token=secret#top');

    expect($ex::class)->toBe($expected);
    expect($ex->getRequestMethod())->toBe('GET');
    expect($ex->getRequestPath())->toBe('/v2/items/7');
})->with([
    [400, \PetstoreClient\Errors\BadRequestException::class],
    [401, \PetstoreClient\Errors\UnauthorizedException::class],
    [403, \PetstoreClient\Errors\ForbiddenException::class],
    [404, \PetstoreClient\Errors\NotFoundException::class],
    [409, \PetstoreClient\Errors\ConflictException::class],
    [418, \PetstoreClient\Errors\ClientException::class],
    [422, \PetstoreClient\Errors\UnprocessableEntityException::class],
    [429, \PetstoreClient\Errors\TooManyRequestsException::class],
    [500, \PetstoreClient\Errors\InternalServerErrorException::class],
    [503, \PetstoreClient\Errors\ServerException::class],
    [302, ApiException::class],
]);

test('bhv errors 026 the request method and path are rendered on their own line and never carry the query string', function (): void {
    $ex = ApiException::fromResponse(404, [], null, 'DELETE', '/items/7?api_key=secret');

    expect((string) $ex)->toBe("API returned status code 404\nHTTP status code: 404\nRequest: DELETE /items/7");
    expect((string) $ex)->not->toContain('secret');
});

test('bhv errors 050 response headers render in the error text as compact JSON with sorted keys and unescaped slashes', function (): void {
    $ex = new ApiException(302, 'moved', [
        'x-b' => '2',
        'location' => 'https://example.com/a',
        'x-a' => '1',
    ]);

    expect((string) $ex)->toContain('Response headers: {"location":"https://example.com/a","x-a":"1","x-b":"2"}');
});

test('bhv errors 051 an empty error message falls back to the generic error line', function (): void {
    expect((string) new ApiException(502, ''))
        ->toBe("Error message: the server returns an error\nHTTP status code: 502");
});

test('bhv errors 053 each named error pins its own status code', function (string $class, int $status): void {
    expect((new $class('x'))->getStatusCode())->toBe($status);
})->with([
    [\PetstoreClient\Errors\BadRequestException::class, 400],
    [\PetstoreClient\Errors\UnauthorizedException::class, 401],
    [\PetstoreClient\Errors\ForbiddenException::class, 403],
    [\PetstoreClient\Errors\NotFoundException::class, 404],
    [\PetstoreClient\Errors\ConflictException::class, 409],
    [\PetstoreClient\Errors\UnprocessableEntityException::class, 422],
    [\PetstoreClient\Errors\TooManyRequestsException::class, 429],
    [\PetstoreClient\Errors\InternalServerErrorException::class, 500],
]);

test('bhv errors 055 status error constructors default the response context to null', function (): void {
    $ex = new \PetstoreClient\Errors\BadRequestException();

    expect($ex->getStatusCode())->toBe(400);
    expect($ex->getResponseHeaders())->toBeNull();
    expect($ex->getResponseBody())->toBeNull();
    expect($ex->getErrorBody())->toBeNull();
    expect($ex->getRequestMethod())->toBeNull();
    expect($ex->getRequestPath())->toBeNull();
});

test('bhv errors 056 a status at or above 600 yields the base error and not a server error', function (): void {
    $ex = ApiException::fromResponse(600, ['x-request-id' => 'abc'], null);

    expect($ex::class)->toBe(ApiException::class);
    expect($ex)->not->toBeInstanceOf(\PetstoreClient\Errors\ServerException::class);
    expect($ex->getStatusCode())->toBe(600);
});

test('bhv errors 012 an error built without a request has no request context', function (): void {
    $ex = new ApiException(500, 'boom');

    expect($ex->getRequestMethod())->toBeNull();
    expect($ex->getRequestPath())->toBeNull();
    expect((string) $ex)->not->toContain('Request:');
});

test('a bare path or a query only target reduces to a path', function (string $target, string $expected): void {
    expect(ApiException::fromResponse(500, [], null, 'GET', $target)->getRequestPath())->toBe($expected);
})->with([
    'absolute path' => ['/items/7?x=1', '/items/7'],
    'full url' => ['https://h:8443/a/b?x=1', '/a/b'],
    'host only' => ['https://h?x=1', '/'],
    'fragment' => ['/a#frag', '/a'],
]);

test('bhv errors 005 fromResponse leaves the error body null when the body is not JSON', function (): void {
    $ex = ApiException::fromResponse(502, [], '<html>bad gateway</html>');

    expect($ex::class)->toBe(\PetstoreClient\Errors\ServerException::class);
    expect($ex->getResponseBody())->toBe('<html>bad gateway</html>');
    expect($ex->getErrorBody())->toBeNull();
});

test('bhv errors 008 fromResponse strips a leading byte order mark before decoding the error body', function (): void {
    $e = \PetstoreClient\Errors\ApiException::fromResponse(400, [], "\xEF\xBB\xBF{\"code\":7}");

    expect($e->getErrorBody())->toBe(['code' => 7]);
});

test('bhv errors 009 fromResponse decodes an error body nested exactly at the depth cap and drops one past it', function (): void {
    $cap = \PetstoreClient\ObjectSerializer::MAX_JSON_DEPTH;
    $atCap = str_repeat('[', $cap) . str_repeat(']', $cap);
    $pastCap = str_repeat('[', $cap + 1) . str_repeat(']', $cap + 1);

    expect(ApiException::fromResponse(400, [], $atCap)->getErrorBody())->toBeArray();
    expect(ApiException::fromResponse(400, [], $pastCap)->getErrorBody())->toBeNull();
});

test('bhv errors 044 every error type lives in the Errors namespace under the root', function (string $error): void {
    $namespace = substr($error, 0, (int) strrpos($error, '\\'));

    expect($namespace)->toBe('PetstoreClient\\Errors');
    expect(is_a($error, OpenAPIException::class, true))->toBeTrue();
})->with([
    OpenAPIException::class,
    ApiException::class,
    \PetstoreClient\Errors\ClientException::class,
    \PetstoreClient\Errors\ServerException::class,
    \PetstoreClient\Errors\BadRequestException::class,
    \PetstoreClient\Errors\UnauthorizedException::class,
    \PetstoreClient\Errors\ForbiddenException::class,
    \PetstoreClient\Errors\NotFoundException::class,
    \PetstoreClient\Errors\ConflictException::class,
    \PetstoreClient\Errors\UnprocessableEntityException::class,
    \PetstoreClient\Errors\TooManyRequestsException::class,
    \PetstoreClient\Errors\InternalServerErrorException::class,
    \PetstoreClient\Errors\NetworkException::class,
    \PetstoreClient\Errors\NetworkTimeoutException::class,
    SerializationException::class,
    \PetstoreClient\Errors\OAuth2ServerException::class,
    \PetstoreClient\Errors\OAuth2TokenException::class,
]);

test('bhv errors 010 fromResponse keeps a scalar json body as the error body', function (string $body, string|int $expected): void {
    $ex = ApiException::fromResponse(500, [], $body);

    expect($ex->getResponseBody())->toBe($body);
    expect($ex->getErrorBody())->toBe($expected);
})->with([
    'json string' => ['"upstream timeout"', 'upstream timeout'],
    'json number' => ['42', 42],
]);

test('bhv errors 017 getTypedErrorBody raises SerializationException when the body is not the requested type', function (): void {
    $ex = ApiException::fromResponse(502, [], '<html>bad gateway</html>');

    expect(fn () => $ex->getTypedErrorBody(Category::class))
        ->toThrow(SerializationException::class);
});

test('bhv errors 024 renders the message, status, headers and body', function (): void {
    $ex = new ApiException(404, 'not found', ['x-request-id' => 'abc'], '{"code":7}');
    expect((string) $ex)->toBe("not found\nHTTP status code: 404\nResponse headers: {\"x-request-id\":\"abc\"}\nResponse body: {\"code\":7}");
});

test('bhv errors 022 omits empty headers and an empty body', function (): void {
    $ex = new ApiException(502, 'bad gateway', [], '');
    expect((string) $ex)->toBe("bad gateway\nHTTP status code: 502");
});

test('bhv errors 023 rendered form masks credential headers', function (): void {
    $ex = new ApiException(401, 'denied', [
        'Authorization' => 'Bearer top-secret-token',
        'X-Api-Key' => 'key-secret',
        'Set-Cookie' => 'session=cookie-secret',
        'Content-Type' => 'application/json',
    ]);
    $rendered = (string) $ex;
    expect($rendered)->not->toContain('top-secret-token');
    expect($rendered)->not->toContain('key-secret');
    expect($rendered)->not->toContain('cookie-secret');
    expect($rendered)->toContain('application/json');
    expect($rendered)->toContain('***');
});

test('bhv errors 002 every status exception carries the request context', function (int $status): void {
    $ex = ApiException::fromResponse($status, [], '{}', 'get', '/items/7?token=secret');
    expect($ex)->toBeInstanceOf(ApiException::class);
    expect($ex->getRequestMethod())->toBe('GET');
    expect($ex->getRequestPath())->toBe('/items/7');
})->with([
    '400' => [400],
    '401' => [401],
    '404' => [404],
    '500' => [500],
    '503' => [503],
]);

test('bhv errors 011 fromResponse names the request in the message and the fields', function (): void {
    $ex = ApiException::fromResponse(404, null, null, 'get', '/items?x=1');
    expect($ex->getRequestMethod())->toBe('GET');
    expect($ex->getRequestPath())->toBe('/items');
    expect((string) $ex)->toContain("\nRequest: GET /items");
});

test('bhv errors 016 getTypedErrorBody returns null for an empty response body', function (): void {
    $ex = new ApiException(404, 'not found', null, '');
    expect($ex->getTypedErrorBody(Category::class))->toBeNull();
});

test('bhv errors 036 the error carries the method and the path without the query string', function (): void {
    $ex = new ApiException(500, 'boom', null, null, null, null, 'post', '/items?token=abc');
    expect($ex->getRequestMethod())->toBe('POST');
    expect($ex->getRequestPath())->toBe('/items');
});

test('bhv errors 049 an oauth2 server error message names the status and the error code', function (): void {
    $error = new \PetstoreClient\Errors\OAuth2ServerException(400, 'invalid_grant', null, null, '{}');

    expect($error->getMessage())->toBe('Token request failed with status 400: invalid_grant');
});
