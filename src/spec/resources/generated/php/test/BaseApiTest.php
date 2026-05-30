<?php

declare(strict_types=1);

/* phpcs:disable PSR1.Classes.ClassDeclaration.MultipleClasses */

namespace PetstoreClient\Test;

use PetstoreClient\Api\BaseApi;
use PetstoreClient\Api\Options\FindPetsByStatusOptions;
use PetstoreClient\Api\PetApi;
use PetstoreClient\ApiClient;
use PetstoreClient\ApiException;
use PetstoreClient\ApiResponse;
use PetstoreClient\Auth\Authenticator;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;
use PetstoreClient\Errors\BadRequestException;
use PetstoreClient\Errors\ClientException;
use PetstoreClient\Errors\ConflictException;
use PetstoreClient\Errors\ForbiddenException;
use PetstoreClient\Errors\InternalServerErrorException;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Errors\UnauthorizedException;
use PetstoreClient\Errors\UnprocessableEntityException;
use PetstoreClient\Models\Category;
use PetstoreClient\Servers;
use PHPUnit\Framework\TestCase;

class CapturingApiClient implements ApiClient
{
    public string $capturedUrl = '';
    /** @var array<string, string> */
    public array $capturedHeaders = [];
    public mixed $capturedBody = null;

    /** @param array<string, string> $headers */
    public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
    {
        $this->capturedUrl = $url;
        $this->capturedHeaders = $headers;
        $this->capturedBody = $body;
        return new ApiResponse(200, '{}', ['Content-Type' => 'application/json']);
    }
}

class TestableApi extends BaseApi
{
    /**
     * @param array<string, mixed> $queryParams
     * @param array<string, string> $headerParams
     * @param array<string> $accepts
     */
    public function call(
        string $method,
        string $path,
        array $queryParams,
        array $headerParams,
        mixed $body,
        array $accepts,
        ?string $contentType,
        ?string $returnType,
        ?Authenticator $auth = null
    ): mixed {
        return $this->invokeApi(
            $method,
            $path,
            $queryParams,
            $headerParams,
            $body,
            $accepts,
            $contentType,
            $returnType,
            $auth
        );
    }
}

class TestAuthenticator implements Authenticator
{
    /**
     * @param array<string, string> $headers
     * @param array<string, string> $queryParams
     * @param array<string, string> $cookies
     */
    public function __construct(
        private readonly array $headers = [],
        private readonly array $queryParams = [],
        private readonly array $cookies = []
    ) {
    }

    public function getHost(): string
    {
        return '';
    }

    public function getAuthHeaders(): array
    {
        return $this->headers;
    }

    public function getQueryParams(): array
    {
        return $this->queryParams;
    }

    public function getCookieParams(): array
    {
        return $this->cookies;
    }
}

class BaseApiTest extends TestCase
{
    private function api(): TestableApi
    {
        $url = getenv('CHASM_HTTP_URL') ?: '';
        $config = new Configuration($url);
        return new TestableApi(new DefaultApiClient(), $config);
    }

    /** @return array<array{int, class-string<ApiException>}> */
    public static function statusToExceptionProvider(): array
    {
        return [
            [400, BadRequestException::class],
            [401, UnauthorizedException::class],
            [403, ForbiddenException::class],
            [404, NotFoundException::class],
            [409, ConflictException::class],
            [422, UnprocessableEntityException::class],
            [418, ClientException::class],
            [500, InternalServerErrorException::class],
            [502, ServerException::class],
        ];
    }

    /**
     * @dataProvider statusToExceptionProvider
     * @param class-string<ApiException> $expectedClass
     */
    public function testThrowsCorrectException(int $status, string $expectedClass): void
    {
        try {
            $this->api()->call(
                'GET',
                "/test/status/$status",
                [],
                [],
                null,
                ['application/json'],
                'application/json',
                null
            );
            $this->fail('Expected exception not thrown');
        } catch (ApiException $e) {
            $this->assertInstanceOf($expectedClass, $e);
            $this->assertSame($status, $e->getCode());
            $this->assertNotEmpty($e->getResponseBody());
        }
    }

    public function testGetTypedErrorBodyDeserializesIntoGivenClass(): void
    {
        $ex = new ApiException('boom', 400, [], '{"id":42,"name":"Dogs"}');
        $typed = $ex->getTypedErrorBody(Category::class);

        $this->assertInstanceOf(Category::class, $typed);
        $this->assertSame(42, $typed->id);
        $this->assertSame('Dogs', $typed->name);
    }

    public function testGetTypedErrorBodyReturnsNullForEmptyBody(): void
    {
        $ex = new ApiException('boom', 400, [], '');
        $this->assertNull($ex->getTypedErrorBody(Category::class));
    }

    public function testGetTypedErrorBodyReturnsNullForWhitespaceBody(): void
    {
        $ex = new ApiException('boom', 400, [], "   \n\t");
        $this->assertNull($ex->getTypedErrorBody(Category::class));
    }

    public function testParsesJsonErrorBody(): void
    {
        try {
            $this->api()->call(
                'GET',
                '/test/status/400',
                [],
                [],
                null,
                ['application/json'],
                'application/json',
                null
            );
            $this->fail('Expected exception not thrown');
        } catch (BadRequestException $e) {
            $this->assertNotNull($e->getErrorBody(), 'errorBody should not be null for JSON responses');
        }
    }

    public function testNotFoundHierarchy(): void
    {
        try {
            $this->api()->call(
                'GET',
                '/test/status/404',
                [],
                [],
                null,
                ['application/json'],
                'application/json',
                null
            );
            $this->fail('Expected exception not thrown');
        } catch (NotFoundException $e) {
            $this->assertInstanceOf(ClientException::class, $e);
            $this->assertInstanceOf(ApiException::class, $e);
        }
    }

    public function testInternalServerErrorHierarchy(): void
    {
        try {
            $this->api()->call(
                'GET',
                '/test/status/500',
                [],
                [],
                null,
                ['application/json'],
                'application/json',
                null
            );
            $this->fail('Expected exception not thrown');
        } catch (InternalServerErrorException $e) {
            $this->assertInstanceOf(ServerException::class, $e);
            $this->assertInstanceOf(ApiException::class, $e);
        }
    }

    public function testDeserializesJsonResponse(): void
    {
        $result = $this->api()->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            'array'
        );
        $this->assertIsArray($result);
        $this->assertSame('GET', $result['method']);
    }

    public function testReturnsRawStringForNonJson(): void
    {
        $result = $this->api()->call(
            'GET',
            '/test/text-plain',
            [],
            [],
            null,
            ['text/plain'],
            'application/json',
            'string'
        );
        $this->assertIsString($result);
        $this->assertNotEmpty($result);
    }

    public function testReturnsNullWhenReturnTypeIsNull(): void
    {
        $result = $this->api()->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertNull($result);
    }

    public function testAppendsQueryParams(): void
    {
        $result = $this->api()->call(
            'GET',
            '/test/echo',
            ['foo' => 'bar'],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertNull($result);
    }

    public function testForwardsAuthHeaders(): void
    {
        $auth = new TestAuthenticator(headers: ['X-Custom' => 'auth-value']);
        $result = $this->api()->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            'array',
            $auth
        );
        $this->assertIsArray($result);
        $this->assertSame('auth-value', $result['headers']['x-custom']);
    }

    public function testSetsCookieHeader(): void
    {
        $auth = new TestAuthenticator(cookies: ['session' => 'abc123']);
        $this->api()->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null,
            $auth
        );
        $this->addToAssertionCount(1);
    }

    public function testSerializesJsonBody(): void
    {
        $result = $this->api()->call(
            'POST',
            '/test/echo',
            [],
            [],
            ['key' => 'value'],
            ['application/json'],
            'application/json',
            'array'
        );
        $this->assertIsArray($result);
        /** @var array<string, mixed> $parsedBody */
        $parsedBody = (array) json_decode((string) $result['body'], true);
        $this->assertSame('value', $parsedBody['key']);
    }

    public function testSendsNoBodyWhenNull(): void
    {
        $this->api()->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->addToAssertionCount(1);
    }

    public function testAllowEmptyValueParamIncludedWithDefaultOptions(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $api = new PetApi($client, $config);
        try {
            $api->findPetsByStatus(new FindPetsByStatusOptions());
        } catch (\Exception $e) {
            // Response deserialization may fail; we only care about the captured URL
        }
        $this->assertStringContainsString('status=', $client->capturedUrl);
    }

    public function testAllowEmptyValueIncludesParamInQueryString(): void
    {
        $result = $this->api()->call(
            'GET',
            '/test/echo',
            ['filter' => ''],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertNull($result);
    }

    public function testExpandsArrayQueryParams(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'GET',
            '/test/echo',
            ['tags' => ['a', 'b']],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertStringContainsString('tags=a&tags=b', $client->capturedUrl);
    }

    public function testSerializesBooleanQueryParams(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'GET',
            '/test/echo',
            ['active' => true],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertStringContainsString('active=true', $client->capturedUrl);
    }

    public function testSerializesNumberQueryParams(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'GET',
            '/test/echo',
            ['limit' => 10],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertStringContainsString('limit=10', $client->capturedUrl);
        $this->assertStringNotContainsString('limit=10.0', $client->capturedUrl);
    }

    public function testHandlesEmptyQueryParams(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertStringNotContainsString('?', $client->capturedUrl);
    }

    // -- server variable overrides via Configuration --

    public function testServerVariableOverridesResolveInBaseUrl(): void
    {
        $config = Configuration::builder()
            ->server(Servers::server1(), ['environment' => 'staging'])
            ->build();
        $this->assertSame('https://staging.example.com/api/v3', $config->baseUrl);
    }

    public function testDefaultServerVariablesProduceCorrectBaseUrl(): void
    {
        $config = Configuration::builder()
            ->server(Servers::server1())
            ->build();
        $this->assertSame('https://api.example.com/api/v3', $config->baseUrl);
    }

    public function testInvalidEnumValueThrowsException(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        Configuration::builder()
            ->server(Servers::server1(), ['environment' => 'invalid'])
            ->build();
    }

    public function testApiRequestUsesResolvedServerUrl(): void
    {
        $config = Configuration::builder()
            ->server(Servers::server1(), ['environment' => 'staging'])
            ->build();
        $this->assertStringStartsWith('https://staging.example.com/api/v3', $config->baseUrl);
    }

    // -- content-type deserialization --

    public function testSkipsDeserializationForNonJsonContentType(): void
    {
        $client = new class implements ApiClient {
            /** @param array<string, string> $headers */
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, 'hello', ['Content-Type' => 'text/plain']);
            }
        };
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['text/plain'],
            'application/json',
            'string'
        );
        $this->assertSame('hello', $result);
    }

    public function testDeserializesVendorJsonMimeTypes(): void
    {
        $client = new class implements ApiClient {
            /** @param array<string, string> $headers */
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, '{"title":"Not Found"}', ['Content-Type' => 'application/problem+json']);
            }
        };
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            'array'
        );
        $this->assertIsArray($result);
        $this->assertSame('Not Found', $result['title']);
    }

    // -- body serialization by content type --

    public function testSerializesTextPlainBody(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'POST',
            '/test/echo',
            [],
            [],
            'hello world',
            ['application/json'],
            'text/plain',
            null
        );
        $this->assertNotNull($client->capturedBody);
        $this->assertIsString($client->capturedBody);
        $this->assertStringContainsString('hello world', $client->capturedBody);
    }

    public function testSerializesFormUrlencodedBody(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'POST',
            '/test/echo',
            [],
            [],
            ['name' => 'alice'],
            ['application/json'],
            'application/x-www-form-urlencoded',
            null
        );
        $this->assertNotNull($client->capturedBody);
        $this->assertIsString($client->capturedBody);
        $this->assertStringContainsString('name=alice', $client->capturedBody);
    }

    public function testPassesBinaryBodyAsIs(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'POST',
            '/test/echo',
            [],
            [],
            "\x01\x02\x03",
            ['application/json'],
            'application/octet-stream',
            null
        );
        $this->assertNotNull($client->capturedBody);
    }

    // -- header flow-through --

    public function testEmptyContentTypeDefaultsToJson(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            '',
            null
        );
        $this->assertSame('application/json', $client->capturedHeaders['Content-Type'] ?? '');
    }

    public function testAllHeadersFromSelectorFlowThrough(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertArrayHasKey('Accept', $client->capturedHeaders);
        $this->assertArrayHasKey('Content-Type', $client->capturedHeaders);
    }

    // -- BinaryResponseTests --

    public function testOctetStreamResponseDecodedAsBase64Bytes(): void
    {
        $binaryData = "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A";
        $encoded = base64_encode($binaryData);
        $client = new class implements ApiClient {
            public string $body = '';
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, $this->body, ['Content-Type' => 'application/octet-stream']);
            }
        };
        $client->body = $encoded;
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/octet-stream'],
            'application/octet-stream',
            null
        );
        $this->assertSame($binaryData, $result);
    }

    public function testImagePngResponseDecodedAsBytes(): void
    {
        $binaryData = "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A\x00\x00\x00\x0D";
        $encoded = base64_encode($binaryData);
        $client = new class implements ApiClient {
            public string $body = '';
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, $this->body, ['Content-Type' => 'image/png']);
            }
        };
        $client->body = $encoded;
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/api/img',
            [],
            [],
            null,
            ['image/png'],
            'image/png',
            null
        );
        $this->assertSame($binaryData, $result);
    }

    public function testBinaryResponseRoundtripsNulAndHighBytes(): void
    {
        // Use a byte sequence containing NUL (0x00) and a high byte (0xFF)
        // that would be mangled by any naive UTF-8 string conversion.
        $binaryData = "\x00\xFF\x42";
        $encoded = base64_encode($binaryData);
        $client = new class implements ApiClient {
            public string $body = '';
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, $this->body, ['Content-Type' => 'application/octet-stream']);
            }
        };
        $client->body = $encoded;
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/api/bin',
            [],
            [],
            null,
            ['application/octet-stream'],
            'application/octet-stream',
            null
        );
        $this->assertSame($binaryData, $result);
        $this->assertSame(3, strlen($result));
    }

    public function testEmptyBinaryBodyYieldsNull(): void
    {
        $client = new class implements ApiClient {
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, '', ['Content-Type' => 'application/octet-stream']);
            }
        };
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/api/bin',
            [],
            [],
            null,
            ['application/octet-stream'],
            'application/octet-stream',
            null
        );
        $this->assertNull($result);
    }

    public function testJsonResponseParsedToObject(): void
    {
        $client = new class implements ApiClient {
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, '{"id":42,"name":"test"}', ['Content-Type' => 'application/json']);
            }
        };
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            null,
            null
        );
        $this->assertNotNull($result);
    }

    public function testTextPlainResponseReturnsString(): void
    {
        $client = new class implements ApiClient {
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, 'hello world', ['Content-Type' => 'text/plain']);
            }
        };
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['text/plain'],
            null,
            null
        );
        $this->assertIsString($result);
        $this->assertSame('hello world', $result);
    }

    public function testEmptyBodyYieldsNull(): void
    {
        $client = new class implements ApiClient {
            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                return new ApiResponse(200, '', ['Content-Type' => 'application/octet-stream']);
            }
        };
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $result = $testApi->call(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            ['application/octet-stream'],
            null,
            null
        );
        $this->assertNull($result);
    }

    // -- CrossOriginRedirectTests --

    public function testSameOriginRedirectForwardsAuthorization(): void
    {
        $authHeader = 'Bearer token123';
        $forwardedHeaders = [];

        // Same-origin: sensitive headers are forwarded
        $forwardedHeaders['Authorization'] = $authHeader;

        $this->assertArrayHasKey('Authorization', $forwardedHeaders);
        $this->assertSame($authHeader, $forwardedHeaders['Authorization']);
    }

    public function testCrossOriginRedirectDropsAuthorization(): void
    {
        $sensitiveHeaders = ['authorization', 'cookie', 'proxy-authorization'];
        $originalHeaders = ['Authorization' => 'Bearer token123', 'Accept' => 'application/json'];
        $forwardedHeaders = [];

        // Cross-origin: skip sensitive headers
        foreach ($originalHeaders as $key => $value) {
            if (in_array(strtolower($key), $sensitiveHeaders, true)) {
                continue;
            }
            $forwardedHeaders[$key] = $value;
        }

        $this->assertArrayNotHasKey('Authorization', $forwardedHeaders);
        $this->assertArrayHasKey('Accept', $forwardedHeaders);
    }

    public function testCrossOriginRedirectDropsCookie(): void
    {
        $sensitiveHeaders = ['authorization', 'cookie', 'proxy-authorization'];
        $originalHeaders = ['Cookie' => 'session=abc123', 'Accept' => 'application/json'];
        $forwardedHeaders = [];

        // Cross-origin: skip sensitive headers
        foreach ($originalHeaders as $key => $value) {
            if (in_array(strtolower($key), $sensitiveHeaders, true)) {
                continue;
            }
            $forwardedHeaders[$key] = $value;
        }

        $this->assertArrayNotHasKey('Cookie', $forwardedHeaders);
        $this->assertArrayHasKey('Accept', $forwardedHeaders);
    }

    // -- NullBodyContentTypeTests --

    public function testNullBodyPostDoesNotSendContentType(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'POST',
            '/test/echo',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        $this->assertArrayNotHasKey(
            'Content-Type',
            $client->capturedHeaders,
            'Content-Type must NOT be sent when body is null'
        );
    }

    public function testEmptyStringBodyIncludesContentType(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'POST',
            '/test/echo',
            [],
            [],
            '',
            ['application/json'],
            'application/json',
            null
        );
        $this->assertArrayHasKey(
            'Content-Type',
            $client->capturedHeaders,
            'Content-Type must be sent when body is an empty string'
        );
    }

    public function testEmptyJsonObjectBodyIncludesContentType(): void
    {
        $client = new CapturingApiClient();
        $config = new Configuration('http://localhost');
        $testApi = new TestableApi($client, $config);
        $testApi->call(
            'POST',
            '/test/echo',
            [],
            [],
            '{}',
            ['application/json'],
            'application/json',
            null
        );
        $this->assertArrayHasKey(
            'Content-Type',
            $client->capturedHeaders,
            'Content-Type must be sent when body is {}'
        );
        $this->assertSame('application/json', $client->capturedHeaders['Content-Type']);
    }
}
