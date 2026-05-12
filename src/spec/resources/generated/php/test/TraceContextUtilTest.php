<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\TraceContextUtil;

class TraceContextUtilTest extends TestCase
{
    /**
     * Test that injectTraceContext does not inject traceparent when OTel is not installed.
     */
    public function testDoesNotInjectTraceparentWithoutOTel(): void
    {
        $headers = [];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertArrayNotHasKey('traceparent', $headers);
    }

    public function testNoOpWithoutTracer(): void
    {
        $headers = [];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertEmpty($headers);
    }

    public function testEmptyHeadersDoNotCauseException(): void
    {
        $headers = [];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertEmpty($headers);
    }

    public function testDoesNotInjectTracestateWithoutOTel(): void
    {
        $headers = [];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertArrayNotHasKey('tracestate', $headers);
    }

    public function testPreservesAuthorizationHeader(): void
    {
        $headers = ['Authorization' => 'Bearer token123'];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertEquals('Bearer token123', $headers['Authorization']);
    }

    public function testPreservesContentTypeHeader(): void
    {
        $headers = ['Content-Type' => 'application/json'];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertEquals('application/json', $headers['Content-Type']);
    }

    public function testPreservesXRequestIdHeader(): void
    {
        $headers = ['X-Request-ID' => 'req-12345'];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertEquals('req-12345', $headers['X-Request-ID']);
    }

    public function testPreservesAllExistingHeaders(): void
    {
        $headers = [
            'Authorization' => 'Bearer token',
            'Content-Type' => 'application/json',
            'X-Request-ID' => 'abc-123',
        ];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertCount(3, $headers);
        $this->assertEquals('Bearer token', $headers['Authorization']);
        $this->assertEquals('application/json', $headers['Content-Type']);
        $this->assertEquals('abc-123', $headers['X-Request-ID']);
    }
}
