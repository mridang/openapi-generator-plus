<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\TransportOptions;

class TransportOptionsTest extends TestCase
{
    public function testBuilderProducesCorrectDefaults(): void
    {
        $opts = TransportOptions::builder()->build();

        $this->assertTrue($opts->verifySsl);
        $this->assertNull($opts->caCertPath);
        $this->assertNull($opts->proxy);
        $this->assertNull($opts->timeout);
        $this->assertTrue($opts->followRedirects);
        $this->assertNull($opts->maxRedirects);
        $this->assertSame('PetstoreClient/1.0.0 (php)', $opts->userAgent);
        $this->assertSame([], $opts->defaultHeaders);
        $this->assertFalse($opts->injectRequestId);
    }

    public function testBuilderSetsAllFields(): void
    {
        $opts = TransportOptions::builder()
            ->verifySsl(false)
            ->caCertPath('/path/to/ca.pem')
            ->proxy('http://proxy:8080')
            ->timeout(5000)
            ->followRedirects(false)
            ->maxRedirects(3)
            ->userAgent('TestAgent/1.0')
            ->defaultHeader('X-Custom', 'value')
            ->injectRequestId(true)
            ->build();

        $this->assertFalse($opts->verifySsl);
        $this->assertSame('/path/to/ca.pem', $opts->caCertPath);
        $this->assertSame('http://proxy:8080', $opts->proxy);
        $this->assertSame(5000, $opts->timeout);
        $this->assertFalse($opts->followRedirects);
        $this->assertSame(3, $opts->maxRedirects);
        $this->assertSame('TestAgent/1.0', $opts->userAgent);
        $this->assertSame(['X-Custom' => 'value'], $opts->defaultHeaders);
        $this->assertTrue($opts->injectRequestId);
    }

    public function testDefaultHeadersIsDefensiveCopy(): void
    {
        $headers = ['X-Original' => 'original'];

        $opts = TransportOptions::builder()
            ->defaultHeaders($headers)
            ->build();

        // PHP arrays are value types, so modifying $headers after build
        // cannot affect the built object. Verify the object has the original value.
        $headers['X-Added'] = 'added';

        $this->assertCount(1, $opts->defaultHeaders);
        $this->assertSame('original', $opts->defaultHeaders['X-Original']);
        $this->assertArrayNotHasKey('X-Added', $opts->defaultHeaders);
    }
}
