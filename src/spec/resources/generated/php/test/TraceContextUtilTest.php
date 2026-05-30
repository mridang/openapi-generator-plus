<?php

declare(strict_types=1);

use PetstoreClient\TraceContextUtil;

test('no op without tracer', function (): void {
    $headers = [];
    TraceContextUtil::injectTraceContext($headers);
    expect($headers)->toBeEmpty();
});

test('empty headers do not cause exception', function (): void {
    $headers = [];
    TraceContextUtil::injectTraceContext($headers);
    expect($headers)->toBeEmpty();
});

/**
 * Test that injectTraceContext does not inject traceparent when OTel is not installed.
 */
test('does not inject traceparent without o tel', function (): void {
    $headers = [];
    TraceContextUtil::injectTraceContext($headers);
    expect($headers)->not->toHaveKey('traceparent');
});

test('does not inject tracestate without o tel', function (): void {
    $headers = [];
    TraceContextUtil::injectTraceContext($headers);
    expect($headers)->not->toHaveKey('tracestate');
});

test('preserves authorization header', function (): void {
    $headers = ['Authorization' => 'Bearer token123'];
    TraceContextUtil::injectTraceContext($headers);
    expect($headers['Authorization'])->toEqual('Bearer token123');
});

test('preserves content type header', function (): void {
    $headers = ['Content-Type' => 'application/json'];
    TraceContextUtil::injectTraceContext($headers);
    expect($headers['Content-Type'])->toEqual('application/json');
});

test('preserves x request id header', function (): void {
    $headers = ['X-Request-ID' => 'req-12345'];
    TraceContextUtil::injectTraceContext($headers);
    expect($headers['X-Request-ID'])->toEqual('req-12345');
});

test('preserves all existing headers', function (): void {
    $headers = [
        'Authorization' => 'Bearer token',
        'Content-Type' => 'application/json',
        'X-Request-ID' => 'abc-123',
    ];
    TraceContextUtil::injectTraceContext($headers);
    expect($headers)->toHaveCount(3);
    expect($headers['Authorization'])->toEqual('Bearer token');
    expect($headers['Content-Type'])->toEqual('application/json');
    expect($headers['X-Request-ID'])->toEqual('abc-123');
});
