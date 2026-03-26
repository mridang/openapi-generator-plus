<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ApiClient;
use PetstoreClient\ApiResponse;
use PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator;

/**
 * Tests for the OAuth2 Client Credentials flow authenticator.
 */
class OAuth2ClientCredentialsAuthenticatorTest extends TestCase
{
    private const HOST = 'https://api.example.com';
    private const CLIENT_ID = 'cc-client-id';
    private const CLIENT_SECRET = 'cc-client-secret';
    private const TOKEN_URL = 'https://auth.example.com/token';

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
                    'access_token' => 'cc-access-token',
                    'expires_in' => 3600,
                ]);
                return new ApiResponse(200, $json, []);
            }
        };
    }

    /**
     * The token request must include grant_type=client_credentials.
     */
    public function testSendsGrantType(): void
    {
        $capturedBody = null;
        $client = $this->createCapturingClient($capturedBody);

        $auth = new OAuth2ClientCredentialsAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::TOKEN_URL,
            ['read']
        );
        $auth->setApiClient($client);

        $headers = $auth->getAuthHeaders();

        self::assertNotNull($capturedBody);
        parse_str($capturedBody, $params);
        self::assertSame('client_credentials', $params['grant_type']);

        self::assertArrayHasKey('Authorization', $headers);
        self::assertSame('Bearer cc-access-token', $headers['Authorization']);
    }

    /**
     * The token request must include both client_id and client_secret.
     */
    public function testSendsClientCredentials(): void
    {
        $capturedBody = null;
        $client = $this->createCapturingClient($capturedBody);

        $auth = new OAuth2ClientCredentialsAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::TOKEN_URL,
            []
        );
        $auth->setApiClient($client);

        $auth->getAuthHeaders();

        self::assertNotNull($capturedBody);
        parse_str($capturedBody, $params);
        self::assertSame(self::CLIENT_ID, $params['client_id']);
        self::assertSame(self::CLIENT_SECRET, $params['client_secret']);
    }
}
