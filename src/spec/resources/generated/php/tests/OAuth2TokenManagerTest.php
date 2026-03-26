<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ApiClient;
use PetstoreClient\ApiResponse;
use PetstoreClient\Auth\OAuth\OAuth2TokenManager;

/**
 * Tests for OAuth2TokenManager token lifecycle.
 */
class OAuth2TokenManagerTest extends TestCase
{
    /**
     * Helper: create a mock ApiClient that returns a token response.
     *
     * @param string $responseJson the JSON response body
     * @param array<string,string>|null &$capturedBody captures the POST body
     * @return ApiClient
     */
    private function createTokenClient(string $responseJson, ?array &$capturedBody = null): ApiClient
    {
        return new class($responseJson, $capturedBody) implements ApiClient {
            private string $json;
            /** @var array<string,string>|null */
            private ?array $ref;

            /**
             * @param array<string,string>|null &$ref
             */
            public function __construct(string $json, ?array &$ref)
            {
                $this->json = $json;
                $this->ref = &$ref;
            }

            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                if ($this->ref !== null || $body !== null) {
                    parse_str((string) $body, $parsed);
                    $this->ref = $parsed;
                }
                return new ApiResponse(200, $this->json, []);
            }
        };
    }

    /**
     * BUG: When the token endpoint returns a refresh_token,
     * OAuth2TokenManager should store it for later use.
     * The current implementation ignores refresh_token entirely.
     */
    public function testStoresRefreshToken(): void
    {
        $tokenResponse = json_encode([
            'access_token' => 'at-abc123',
            'refresh_token' => 'rt-xyz789',
            'expires_in' => 3600,
        ]);

        $client = $this->createTokenClient($tokenResponse);

        $manager = new OAuth2TokenManager();
        $manager->setApiClient($client);

        $token = $manager->getAccessToken('https://auth.example.com/token', [
            'grant_type' => 'authorization_code',
            'code' => 'auth-code-123',
        ]);

        self::assertSame('at-abc123', $token);

        // Use reflection to check if refresh_token was stored.
        // BUG: OAuth2TokenManager never stores the refresh_token from the
        // response, so there is no property or method to retrieve it.
        $ref = new \ReflectionClass($manager);
        $hasRefreshTokenProperty = false;
        foreach ($ref->getProperties() as $prop) {
            if ($prop->getName() === 'refreshToken') {
                $hasRefreshTokenProperty = true;
                $prop->setAccessible(true);
                // This assertion will FAIL because the property does not exist
                // or is never set -- confirming the bug.
                self::assertNotNull(
                    $prop->getValue($manager),
                    'refresh_token should be stored but was not'
                );
                break;
            }
        }

        // If the property doesn't exist at all, that's also a bug.
        self::assertTrue($hasRefreshTokenProperty, 'OAuth2TokenManager has no refreshToken property -- refresh tokens are not stored');
    }

    /**
     * The manager should extract and return the access_token from the response.
     */
    public function testExtractsAccessToken(): void
    {
        $tokenResponse = json_encode([
            'access_token' => 'my-access-token-42',
            'expires_in' => 3600,
        ]);

        $client = $this->createTokenClient($tokenResponse);

        $manager = new OAuth2TokenManager();
        $manager->setApiClient($client);

        $token = $manager->getAccessToken('https://auth.example.com/token', [
            'grant_type' => 'client_credentials',
        ]);

        self::assertSame('my-access-token-42', $token);
    }

    /**
     * When the token has expired, the manager should re-fetch.
     * We simulate this by returning a token with expires_in=0 (which after
     * the 30-second buffer will be in the past), then calling getAccessToken
     * again to confirm a new request is made.
     */
    public function testDetectsTokenExpiry(): void
    {
        $callCount = 0;
        $client = new class($callCount) implements ApiClient {
            private int $count;

            public function __construct(int &$count)
            {
                $this->count = &$count;
            }

            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                $this->count++;
                $json = json_encode([
                    'access_token' => 'token-' . $this->count,
                    'expires_in' => 0,
                ]);
                return new ApiResponse(200, $json, []);
            }
        };

        $manager = new OAuth2TokenManager();
        $manager->setApiClient($client);

        $params = ['grant_type' => 'client_credentials'];

        // First call fetches a token
        $token1 = $manager->getAccessToken('https://auth.example.com/token', $params);
        self::assertSame('token-1', $token1);

        // Second call should re-fetch because the token with expires_in=0
        // is already expired (the manager subtracts a 30-second buffer).
        $token2 = $manager->getAccessToken('https://auth.example.com/token', $params);
        self::assertSame('token-2', $token2);
        self::assertSame(2, $callCount, 'Expected two token fetches due to expiry');
    }
}
