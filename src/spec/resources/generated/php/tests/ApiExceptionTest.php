<?php

declare(strict_types=1);

use PetstoreClient\Errors\ApiException;
use PetstoreClient\Errors\SerializationException;
use PetstoreClient\Errors\OpenAPIException;
use PetstoreClient\Models\Category;

test('exposes status code, message, body, headers and error body', function (): void {
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

test('null headers and body mark transport-no-response', function (): void {
    // apierror-responsebody-headers-nullable-split: null is distinct from an
    // empty header map / empty body so a pre-response transport failure can
    // be encoded.
    $ex = new ApiException(
        0,
        'connection reset',
        null,
    );

    expect($ex->getResponseHeaders())->toBeNull();
    expect($ex->getResponseBody())->toBeNull();
});

test('is an Exception subclass', function (): void {
    $ex = new ApiException(500, 'boom');

    expect($ex)->toBeInstanceOf(Exception::class);
    expect($ex->getMessage())->not->toBeEmpty();
});

test('ApiException extends the SDK root OpenAPIException', function (): void {
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

test('SerializationException reaches the SDK root OpenAPIException', function (): void {
    /* Unified hierarchy: serialization failures must also be catchable via
     * the single OpenAPIException root, not just the native \RuntimeException. */
    $ex = new SerializationException('serialize failed');

    expect($ex)->toBeInstanceOf(OpenAPIException::class);
    expect($ex)->toBeInstanceOf(Exception::class);
});

test('NetworkException and NetworkTimeoutException extend ApiException with status 0', function (): void {
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
        new \PetstoreClient\Errors\ClientException(418, 'client error'),
        new \PetstoreClient\Errors\InternalServerErrorException('server error'),
        new \PetstoreClient\Errors\ServerException(503, 'server error'),
    ];

    foreach ($typed as $ex) {
        expect($ex)->toBeInstanceOf(ApiException::class);
    }
});

test('a typed error is caught by a base ApiException catch', function (): void {
    $caught = null;
    try {
        throw new \PetstoreClient\Errors\BadRequestException('bad request');
    } catch (ApiException $e) {
        $caught = $e;
    }

    expect($caught)->toBeInstanceOf(\PetstoreClient\Errors\BadRequestException::class);
    expect($caught->getStatusCode())->toBe(400);
});

test('getTypedErrorBody deserializes the body into the given class', function (): void {
    $ex = new ApiException(400, 'bad request', [], '{"id":42,"name":"Dogs"}');

    $typed = $ex->getTypedErrorBody(Category::class);
    expect($typed)->toBeInstanceOf(Category::class);
    assert($typed instanceof Category);
    expect($typed->id)->toBe(42);
    expect($typed->name)->toBe('Dogs');
});

test('getTypedErrorBody returns null when there is no response body', function (): void {
    $ex = new ApiException(500, 'oops', []);

    expect($ex->getTypedErrorBody(Category::class))->toBeNull();
});

test('getTypedErrorBody ignores extraneous fields not on the model', function (): void {
    $ex = new ApiException(422, 'unprocessable', [], '{"id":1,"name":"Cat","extra":"drop-me"}');

    $typed = $ex->getTypedErrorBody(Category::class);
    expect($typed)->toBeInstanceOf(Category::class);
    assert($typed instanceof Category);
    expect($typed->id)->toBe(1);
    expect($typed->name)->toBe('Cat');
});

test('fromResponse maps every status to its exception', function (int $status, string $expected): void {
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
    [418, \PetstoreClient\Errors\ClientException::class],
    [500, \PetstoreClient\Errors\InternalServerErrorException::class],
    [503, \PetstoreClient\Errors\ServerException::class],
    [302, ApiException::class],
]);

test('fromResponse leaves the error body null when the body is not JSON', function (): void {
    $ex = ApiException::fromResponse(502, [], '<html>bad gateway</html>');

    expect($ex::class)->toBe(\PetstoreClient\Errors\ServerException::class);
    expect($ex->getResponseBody())->toBe('<html>bad gateway</html>');
    expect($ex->getErrorBody())->toBeNull();
});

test('every error type lives in the Errors namespace under the root', function (string $error): void {
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
    \PetstoreClient\Errors\InternalServerErrorException::class,
    \PetstoreClient\Errors\NetworkException::class,
    \PetstoreClient\Errors\NetworkTimeoutException::class,
    SerializationException::class,
    \PetstoreClient\Errors\OAuth2ServerException::class,
    \PetstoreClient\Errors\OAuth2TokenException::class,
]);
