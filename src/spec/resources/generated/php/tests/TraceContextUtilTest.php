<?php

declare(strict_types=1);

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\TraceContextUtil;

class TraceContextUtilTest extends TestCase
{
    /**
     * Test that injectTraceContext does not inject traceparent when OTel is not installed.
     */
    public function testShouldNotInjectTraceparentWithoutOtel(): void
    {
        $headers = [];
        TraceContextUtil::injectTraceContext($headers);
        $this->assertArrayNotHasKey('traceparent', $headers);
    }

    /**
     * Test that injectTraceContext does not throw any exception.
     */
    public function testShouldNotThrowAnyException(): void
    {
        $headers = [];
        TraceContextUtil::injectTraceContext($headers);
        // If we get here, no exception was thrown
        $this->assertTrue(true);
    }
}
