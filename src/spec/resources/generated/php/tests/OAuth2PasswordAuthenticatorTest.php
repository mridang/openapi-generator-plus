<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ApiClient;
use PetstoreClient\ApiResponse;
use PetstoreClient\Auth\OAuth\OAuth2PasswordAuthenticator;

/**
 * Tests for the OAuth2 Resource Owner Password flow authenticator.
 */
class OAuth2PasswordAuthenticatorTest extends TestCase
{
    private const HOST = 'https://api.example.com';
    private const CLIENT_ID = 'pw-client-id';
    private const CLIENT_SECRET = 'pw-client-secret';
    private const TOKEN_URL = 'https://auth.example.com/token';
    private const USERNAME = 'testuser';
    private const PASSWORD = 's3cret!';

    /**
     * Helper: create a mock ApiClient that captures the POST body.
     *
     * @param string|null &$capturedBody reference to capture body
     * @return ApiClient
     */
    private function createCapturingClient(?string &$capturedBody): ApiClient
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
                    'access_token' => 'pw-access-token',
                    'expires_in' => 3600,
                ]);
                return new ApiResponse(200, $json, []);
            }
        };
    }

    /**
     * The token request must include grant_type=password.
     */
    public function testSendsGrantType(): void
    {
        $capturedBody = null;
        $client = $this->createCapturingClient($capturedBody);

        $auth = new OAuth2PasswordAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::TOKEN_URL,
            self::USERNAME,
            self::PASSWORD,
            ['read']
        );
        $auth->setApiClient($client);

        $headers = $auth->getAuthHeaders();

        self::assertNotNull($capturedBody);
        parse_str($capturedBody, $params);
        self::assertSame('password', $params['grant_type']);

        self::assertArrayHasKey('Authorization', $headers);
        self::assertSame('Bearer pw-access-token', $headers['Authorization']);
    }

    /**
     * The token request must include the username and password.
     */
    public function testSendsUsernameAndPassword(): void
    {
        $capturedBody = null;
        $client = $this->createCapturingClient($capturedBody);

        $auth = new OAuth2PasswordAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::TOKEN_URL,
            self::USERNAME,
            self::PASSWORD,
            []
        );
        $auth->setApiClient($client);

        $auth->getAuthHeaders();

        self::assertNotNull($capturedBody);
        parse_str($capturedBody, $params);
        self::assertSame(self::USERNAME, $params['username']);
        self::assertSame(self::PASSWORD, $params['password']);
    }
}
