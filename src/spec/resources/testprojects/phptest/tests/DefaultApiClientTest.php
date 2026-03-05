<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;

class DefaultApiClientTest extends TestCase
{
    private function getEnvOrSkip(string $name): string
    {
        $value = getenv($name);
        if ($value === false || $value === '') {
            $this->markTestSkipped("Skipping: {$name} not set");
        }
        return $value;
    }

    // -- TLS verification disabled --

    public function testMakesHttpsRequestWithVerifySslFalse(): void
    {
        $wiremockUrl = $this->getEnvOrSkip('WIREMOCK_HTTPS_URL');

        $config = Configuration::getDefaultConfiguration()
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
        $wiremockUrl = $this->getEnvOrSkip('WIREMOCK_HTTPS_URL');
        $caCertPath = $this->getEnvOrSkip('CA_CERT_PATH');

        $config = Configuration::getDefaultConfiguration()
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
        $wiremockUrl = $this->getEnvOrSkip('WIREMOCK_HTTP_URL');
        $proxyUrl = $this->getEnvOrSkip('PROXY_URL');

        $config = Configuration::getDefaultConfiguration()
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
        $wiremockUrl = $this->getEnvOrSkip('WIREMOCK_HTTPS_URL');
        $proxyUrl = $this->getEnvOrSkip('PROXY_URL');

        $config = Configuration::getDefaultConfiguration()
            ->setBaseUrl($wiremockUrl)
            ->setProxy($proxyUrl)
            ->setVerifySsl(false);

        $client = new DefaultApiClient($config);
        $response = $client->sendRequest('GET', $wiremockUrl . '/api/test', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('success', $response->body);
    }
}
