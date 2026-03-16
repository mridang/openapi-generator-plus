<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;

class DefaultApiClientTest extends TestCase
{
    // -- TLS verification disabled --

    public function testMakesHttpsRequestWithVerifySslFalse(): void
    {
        $wiremockUrl = getenv('WIREMOCK_HTTPS_URL');

        $config = (new Configuration())
            ->setBaseUrl($wiremockUrl)
            ->setVerifySsl(false);

        $client = new DefaultApiClient($config);
        $response = $client->sendRequest('GET', $wiremockUrl . '/api/test', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('success', $response->body);
    }

    // -- Custom CA bundle --

    public function testMakesHttpsRequestWithCustomCaCert(): void
    {
        $wiremockUrl = getenv('WIREMOCK_HTTPS_URL');
        $caCertPath = getenv('CA_CERT_PATH');

        $config = (new Configuration())
            ->setBaseUrl($wiremockUrl)
            ->setVerifySsl(true)
            ->setSslCaCert($caCertPath);

        $client = new DefaultApiClient($config);
        $response = $client->sendRequest('GET', $wiremockUrl . '/api/test', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('success', $response->body);
    }

    // -- HTTP proxy --

    public function testMakesHttpRequestThroughProxy(): void
    {
        $wiremockUrl = getenv('WIREMOCK_HTTP_URL');
        $proxyUrl = getenv('PROXY_URL');

        $config = (new Configuration())
            ->setBaseUrl($wiremockUrl)
            ->setProxy($proxyUrl);

        $client = new DefaultApiClient($config);
        $response = $client->sendRequest('GET', $wiremockUrl . '/api/test', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('success', $response->body);
    }

    // -- HTTP proxy with TLS --

    public function testMakesHttpsRequestThroughProxyWithVerifySslFalse(): void
    {
        $wiremockUrl = getenv('WIREMOCK_HTTPS_URL');
        $proxyUrl = getenv('PROXY_URL');

        $config = (new Configuration())
            ->setBaseUrl($wiremockUrl)
            ->setProxy($proxyUrl)
            ->setVerifySsl(false);

        $client = new DefaultApiClient($config);
        $response = $client->sendRequest('GET', $wiremockUrl . '/api/test', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('success', $response->body);
    }
}
