<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Api\BaseApi;
use PetstoreClient\ApiClient;
use PetstoreClient\ApiResponse;
use PetstoreClient\Configuration;

class QueryCapturingApiClient implements ApiClient
{
    public ?string $lastUrl = null;

    public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
    {
        $this->lastUrl = $url;
        return new ApiResponse(200, '{}', []);
    }
}

class QueryTestableApi extends BaseApi
{
    public function call(
        string $method,
        string $path,
        array $queryParams
    ): mixed {
        return $this->invokeApi(
            $method, $path, $queryParams, [], null,
            ['application/json'], 'application/json', null);
    }
}

class BaseApiQueryParamsTest extends TestCase
{
    private QueryCapturingApiClient $client;
    private QueryTestableApi $api;

    protected function setUp(): void
    {
        $this->client = new QueryCapturingApiClient();
        $config = new Configuration('http://test');
        $this->api = new QueryTestableApi($this->client, $config);
    }

    public function testExpandsArrayQueryParams(): void
    {
        $this->api->call('GET', '/pets', ['tags' => ['dog', 'cat']]);
        $url = $this->client->lastUrl;
        self::assertNotNull($url);
        self::assertStringContainsString('tags=dog', $url);
        self::assertStringContainsString('tags=cat', $url);
        self::assertStringNotContainsString('[', $url);
        self::assertStringNotContainsString(']', $url);
    }

    public function testSerializesBooleanQueryParams(): void
    {
        $this->api->call('GET', '/pets', ['active' => true]);
        $url = $this->client->lastUrl;
        self::assertNotNull($url);
        self::assertStringContainsString('active=true', $url);
    }

    public function testSerializesNumberQueryParams(): void
    {
        $this->api->call('GET', '/pets', ['limit' => 10]);
        $url = $this->client->lastUrl;
        self::assertNotNull($url);
        self::assertStringContainsString('limit=10', $url);
    }

    public function testHandlesEmptyQueryParams(): void
    {
        $this->api->call('GET', '/pets', []);
        $url = $this->client->lastUrl;
        self::assertNotNull($url);
        self::assertStringNotContainsString('?', $url);
    }
}
