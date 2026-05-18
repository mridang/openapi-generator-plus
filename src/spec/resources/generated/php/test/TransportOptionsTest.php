<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\TransportOptions;

class TransportOptionsTest extends TestCase
{
    public function testVerifySslDefaultsToTrue(): void
    {
        $opts = TransportOptions::builder()->build();
        $this->assertTrue($opts->verifySsl);
    }

    public function testCaCertPathDefaultsToNull(): void
    {
        $opts = TransportOptions::builder()->build();
        $this->assertNull($opts->caCertPath);
    }

    public function testProxyDefaultsToNull(): void
    {
        $opts = TransportOptions::builder()->build();
        $this->assertNull($opts->proxy);
    }

    public function testTimeoutDefaultsToNull(): void
    {
        $opts = TransportOptions::builder()->build();
        $this->assertNull($opts->timeout);
    }

    public function testFollowRedirectsDefaultsToTrue(): void
    {
        $opts = TransportOptions::builder()->build();
        $this->assertTrue($opts->followRedirects);
    }

    public function testMaxRedirectsDefaultsToNull(): void
    {
        $opts = TransportOptions::builder()->build();
        $this->assertNull($opts->maxRedirects);
    }

    public function testUserAgentDefaultsToNonEmptyString(): void
    {
        $opts = TransportOptions::builder()->build();
        $this->assertNotNull($opts->userAgent);
        $this->assertNotEmpty($opts->userAgent);
    }

    public function testDefaultHeadersDefaultsToEmpty(): void
    {
        $opts = TransportOptions::builder()->build();
        $this->assertSame([], $opts->defaultHeaders);
    }

    public function testInjectRequestIdDefaultsToFalse(): void
    {
        $opts = TransportOptions::builder()->build();
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

    public function testFollowRedirectsDefaultsToTrueWithNullMaxRedirects(): void
    {
        $opts = TransportOptions::builder()
            ->followRedirects(true)
            ->build();

        $this->assertTrue($opts->followRedirects);
        $this->assertNull($opts->maxRedirects);
    }

    public function testInvalidProxyUrlThrowsException(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        TransportOptions::builder()->proxy('not-a-url')->build();
    }

    public function testNullProxyUrlIsAccepted(): void
    {
        $opts = TransportOptions::builder()->proxy(null)->build();
        $this->assertNull($opts->proxy);
    }

    public function testBuilderMethodsReturnSameInstance(): void
    {
        $builder = TransportOptions::builder();

        $this->assertSame($builder, $builder->verifySsl(true));
        $this->assertSame($builder, $builder->caCertPath(null));
        $this->assertSame($builder, $builder->proxy(null));
        $this->assertSame($builder, $builder->timeout(null));
        $this->assertSame($builder, $builder->followRedirects(true));
        $this->assertSame($builder, $builder->maxRedirects(null));
        $this->assertSame($builder, $builder->userAgent(null));
        $this->assertSame($builder, $builder->defaultHeader('X-Key', 'val'));
        $this->assertSame($builder, $builder->defaultHeaders([]));
        $this->assertSame($builder, $builder->injectRequestId(false));
    }

    public function testAccumulatesHeadersFromDefaultHeaderCalls(): void
    {
        $opts = TransportOptions::builder()
            ->defaultHeader('X-First', 'one')
            ->defaultHeader('X-Second', 'two')
            ->build();

        $this->assertCount(2, $opts->defaultHeaders);
        $this->assertSame('one', $opts->defaultHeaders['X-First']);
        $this->assertSame('two', $opts->defaultHeaders['X-Second']);
    }

    public function testMergesHeadersFromDefaultHeadersCall(): void
    {
        $opts = TransportOptions::builder()
            ->defaultHeader('X-First', 'one')
            ->defaultHeaders(['X-Second' => 'two', 'X-Third' => 'three'])
            ->build();

        $this->assertCount(3, $opts->defaultHeaders);
        $this->assertSame('one', $opts->defaultHeaders['X-First']);
        $this->assertSame('two', $opts->defaultHeaders['X-Second']);
        $this->assertSame('three', $opts->defaultHeaders['X-Third']);
    }

    public function testModifyingSourceMapDoesNotAffectBuiltOptions(): void
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

    public function testBuilderProducesIndependentInstances(): void
    {
        $builder = TransportOptions::builder()->verifySsl(false);
        $first = $builder->build();
        $second = $builder->build();

        $this->assertSame($first->verifySsl, $second->verifySsl);
        $this->assertNotSame($first, $second);
    }

    // TimeoutConfigTests

    public function testSettingTimeoutIsAccessible(): void
    {
        $opts = TransportOptions::builder()->timeout(5000)->build();
        $this->assertSame(5000, $opts->timeout);
    }

    public function testTimeoutFieldIsNamedTimeout(): void
    {
        // Verify via the property that the field is named 'timeout' (not e.g. 'connectionTimeout').
        $opts = TransportOptions::builder()->timeout(1000)->build();
        $this->assertNotNull($opts->timeout);
        $this->assertSame(1000, $opts->timeout);
    }

    // ProxyConfigTests

    public function testProxyUrlIsPreservedOnReadBack(): void
    {
        $opts = TransportOptions::builder()
            ->proxy('http://proxy.example.com:8080')
            ->build();
        $this->assertSame('http://proxy.example.com:8080', $opts->proxy);
    }

    public function testSettingProxyIsSupportedOnAllPlatforms(): void
    {
        // Proxy configuration must not throw on any platform.
        $opts = TransportOptions::builder()
            ->proxy('http://proxy.example.com:8080')
            ->build();
        $this->assertSame('http://proxy.example.com:8080', $opts->proxy);
    }
}
