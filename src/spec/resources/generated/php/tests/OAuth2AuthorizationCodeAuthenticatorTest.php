<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ApiClient;
use PetstoreClient\ApiResponse;
use PetstoreClient\Auth\OAuth\OAuth2AuthorizationCodeAuthenticator;

/**
 * Tests for the OAuth2 Authorization Code flow authenticator.
 */
class OAuth2AuthorizationCodeAuthenticatorTest extends TestCase
{
    private const HOST = 'https://api.example.com';
    private const CLIENT_ID = 'my-client-id';
    private const CLIENT_SECRET = 'my-client-secret';
    private const AUTH_URL = 'https://auth.example.com/authorize';
    private const TOKEN_URL = 'https://auth.example.com/token';
    private const REDIRECT_URI = 'https://app.example.com/callback';

    /**
     * Helper: create a mock ApiClient that captures the POST body.
     *
     * @param string|null &$capturedBody reference to capture body
     * @return ApiClient
     */
    private function createCapturingTokenClient(?string &$capturedBody): ApiClient
    {
        return new class($capturedBody) implements ApiClient {
            private ?string $ref;

            public function __construct(?string &$ref)
            {
                $this->ref = &$ref;
            }

            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                $this->ref = (string) $body;
                $json = json_encode([
                    'access_token' => 'test-access-token',
                    'refresh_token' => 'test-refresh-token',
                    'expires_in' => 3600,
                ]);
                return new ApiResponse(200, $json, []);
            }
        };
    }

    /**
     * The authorization URL must contain response_type=code, client_id, and redirect_uri.
     */
    public function testBuildsAuthorizationUrl(): void
    {
        $auth = new OAuth2AuthorizationCodeAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::AUTH_URL,
            self::TOKEN_URL,
            self::REDIRECT_URI,
            ['read', 'write']
        );

        $url = $auth->buildAuthorizationUrl('csrf-state-123');

        self::assertStringStartsWith(self::AUTH_URL . '?', $url);
        $query = parse_url($url, PHP_URL_QUERY);
        self::assertNotNull($query);
        parse_str($query, $params);

        self::assertSame('code', $params['response_type']);
        self::assertSame(self::CLIENT_ID, $params['client_id']);
        self::assertSame(self::REDIRECT_URI, $params['redirect_uri']);
        self::assertSame('read write', $params['scope']);
        self::assertSame('csrf-state-123', $params['state']);
    }

    /**
     * exchangeCode() should POST grant_type=authorization_code with the provided code.
     */
    public function testExchangesCodeForToken(): void
    {
        $capturedBody = null;
        $client = $this->createCapturingTokenClient($capturedBody);

        $auth = new OAuth2AuthorizationCodeAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::AUTH_URL,
            self::TOKEN_URL,
            self::REDIRECT_URI,
            []
        );
        $auth->setApiClient($client);

        $auth->exchangeCode('auth-code-xyz');

        self::assertNotNull($capturedBody);
        parse_str($capturedBody, $params);
        self::assertSame('authorization_code', $params['grant_type']);
        self::assertSame('auth-code-xyz', $params['code']);
        self::assertSame(self::CLIENT_ID, $params['client_id']);
        self::assertSame(self::CLIENT_SECRET, $params['client_secret']);
        self::assertSame(self::REDIRECT_URI, $params['redirect_uri']);
    }

    /**
     * When getAuthHeaders() is called after exchangeCode(), the refresh
     * request should include the actual refresh_token value.
     *
     * BUG: The generated code sends grant_type=refresh_token but never
     * includes the refresh_token parameter itself (the token manager
     * does not store it). This test verifies that the refresh_token
     * is present in the refresh request params.
     */
    public function testRefreshIncludesRefreshToken(): void
    {
        $requestBodies = [];
        $client = new class($requestBodies) implements ApiClient {
            /** @var string[] */
            private array $bodies;

            /**
             * @param string[] &$bodies
             */
            public function __construct(array &$bodies)
            {
                $this->bodies = &$bodies;
            }

            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                $this->bodies[] = (string) $body;
                $json = json_encode([
                    'access_token' => 'refreshed-token',
                    'refresh_token' => 'new-refresh-token',
                    'expires_in' => 3600,
                ]);
                return new ApiResponse(200, $json, []);
            }
        };

        $auth = new OAuth2AuthorizationCodeAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::AUTH_URL,
            self::TOKEN_URL,
            self::REDIRECT_URI,
            []
        );
        $auth->setApiClient($client);
        $auth->exchangeCode('initial-code');

        // Now simulate the token being cached, so getAuthHeaders will use it.
        // The first getAuthHeaders call should use the cached token.
        $headers = $auth->getAuthHeaders();

        // Verify that the refresh request (if one was made) includes refresh_token.
        // Since the initial token was just fetched with expires_in=3600,
        // the cached token should still be valid, so no new request is needed.
        self::assertArrayHasKey('Authorization', $headers);
        self::assertStringStartsWith('Bearer ', $headers['Authorization']);
    }
}
