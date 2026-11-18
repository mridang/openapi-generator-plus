<?php

declare(strict_types=1);

use PetstoreClient\ApiResult;

test('bhv results 006 the headers of a result are a copy taken when it is built and cannot be modified', function (): void {
    $source = ['X-Request-Id' => 'abc'];
    $result = new ApiResult(statusCode: 200, data: null, rawBody: '', headers: $source);

    $source['X-Request-Id'] = 'changed';

    expect($result->headers)->toBe(['X-Request-Id' => 'abc']);
    expect(function () use ($result): void {
        $headers = (new \ReflectionProperty(ApiResult::class, 'headers'))->getValue($result);
        \assert(is_array($headers));
        $headers['X-Injected'] = 'x';
        (new \ReflectionProperty(ApiResult::class, 'headers'))->setValue($result, $headers);
    })->toThrow(\Error::class);
});

test('bhv results 003 exposes status code', function (): void {
    $result = new ApiResult(statusCode: 200, data: null, rawBody: '', headers: []);

    expect($result->statusCode)->toBe(200);
});

test('bhv results 004 exposes deserialized data', function (): void {
    $data = ['id' => 1, 'name' => 'example'];
    $result = new ApiResult(statusCode: 200, data: $data, rawBody: '', headers: []);

    expect($result->data)->toBe($data);
});

test('bhv results 001 data may be null', function (): void {
    $result = new ApiResult(statusCode: 204, data: null, rawBody: '', headers: []);

    expect($result->data)->toBeNull();
});

test('bhv results 005 exposes raw body', function (): void {
    $result = new ApiResult(statusCode: 200, data: null, rawBody: '{"id":1}', headers: []);

    expect($result->rawBody)->toBe('{"id":1}');
});

test('bhv results 002 exposes headers', function (): void {
    $headers = ['Content-Type' => 'application/json', 'X-Request-Id' => 'abc'];
    $result = new ApiResult(statusCode: 200, data: null, rawBody: '', headers: $headers);

    expect($result->headers)->toBe($headers);
});

test('bhv langphp 063 carries all fields together', function (): void {
    $result = new ApiResult(
        statusCode: 201,
        data: ['ok' => true],
        rawBody: '{"ok":true}',
        headers: ['Location' => '/resources/1'],
    );

    expect($result->statusCode)->toBe(201);
    expect($result->data)->toBe(['ok' => true]);
    expect($result->rawBody)->toBe('{"ok":true}');
    expect($result->headers)->toBe(['Location' => '/resources/1']);
});
