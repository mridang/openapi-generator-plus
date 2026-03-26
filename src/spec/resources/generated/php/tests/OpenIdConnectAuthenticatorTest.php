<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ApiClient;
use PetstoreClient\ApiResponse;
use PetstoreClient\Auth\OAuth\OpenIdConnectAuthenticator;

/**
 * Tests for the OpenID Connect authenticator.
 */
class OpenIdConnectAuthenticatorTest extends TestCase
{
    private const HOST = 'https://api.example.com';
    private const OIDC_URL = 'https://auth.example.com/.well-known/openid-configuration';
    private const CLIENT_ID = 'oidc-client-id';
    private const CLIENT_SECRET = 'oidc-client-secret';
    private const REDIRECT_URI = 'https://app.example.com/callback';

    private const DISCOVERED_AUTH_URL = 'https://auth.example.com/authorize';
    private const DISCOVERED_TOKEN_URL = 'https://auth.example.com/token';

    /**
     * Helper: create a mock ApiClient that serves the OIDC discovery document
     * and captures token exchange requests.
     *
     * @param string[] &$requestUrls captures all request URLs
     * @param string[] &$requestBodies captures all POST request bodies
     * @return ApiClient
     */
    private function createOidcClient(array &$requestUrls, array &$requestBodies): ApiClient
    {
        $discoveryJson = json_encode([
            'issuer' => 'https://auth.example.com',
            'authorization_endpoint' => self::DISCOVERED_AUTH_URL,
            'token_endpoint' => self::DISCOVERED_TOKEN_URL,
            'userinfo_endpoint' => 'https://auth.example.com/userinfo',
        ]);

        $tokenJson = json_encode([
            'access_token' => 'oidc-access-token',
            'refresh_token' => 'oidc-refresh-token',
            'expires_in' => 3600,
            'id_token' => 'eyJ.fake.id-token',
        ]);

        return new class($discoveryJson, $tokenJson, $requestUrls, $requestBodies) implements ApiClient {
            private string $discoveryJson;
            private string $tokenJson;
            /** @var string[] */
            private array $urls;
            /** @var string[] */
            private array $bodies;

            /**
             * @param string[] &$urls
             * @param string[] &$bodies
             */
            public function __construct(
                string $discoveryJson,
                string $tokenJson,
                array &$urls,
                array &$bodies,
            ) {
                $this->discoveryJson = $discoveryJson;
                $this->tokenJson = $tokenJson;
                $this->urls = &$urls;
                $this->bodies = &$bodies;
            }

            public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
            {
                $this->urls[] = $url;
                if ($body !== null) {
                    $this->bodies[] = (string) $body;
                }

                // Respond to discovery GET
                if ($method === 'GET' && str_contains($url, '.well-known')) {
                    return new ApiResponse(200, $this->discoveryJson, []);
                }

                // Respond to token POST
                return new ApiResponse(200, $this->tokenJson, []);
            }
        };
    }

    /**
     * buildAuthorizationUrl() should use the authorization_endpoint from
     * the OIDC discovery document.
     */
    public function testBuildsAuthorizationUrl(): void
    {
        $requestUrls = [];
        $requestBodies = [];
        $client = $this->createOidcClient($requestUrls, $requestBodies);

        $auth = new OpenIdConnectAuthenticator(
            self::HOST,
            self::OIDC_URL,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::REDIRECT_URI,
            ['openid', 'profile']
        );
        $auth->setApiClient($client);

        $url = $auth->buildAuthorizationUrl('oidc-state-42');

        // Verify it fetched the discovery document
        self::assertNotEmpty($requestUrls);
        self::assertStringContainsString('.well-known', $requestUrls[0]);

        // Verify the authorization URL uses the discovered endpoint
        self::assertStringStartsWith(self::DISCOVERED_AUTH_URL . '?', $url);
        $query = parse_url($url, PHP_URL_QUERY);
        self::assertNotNull($query);
        parse_str($query, $params);
        self::assertSame('code', $params['response_type']);
        self::assertSame(self::CLIENT_ID, $params['client_id']);
        self::assertSame(self::REDIRECT_URI, $params['redirect_uri']);
        self::assertSame('openid profile', $params['scope']);
        self::assertSame('oidc-state-42', $params['state']);
    }

    /**
     * exchangeCode() should exchange the authorization code for tokens
     * using the discovered token_endpoint.
     */
    public function testObtainsToken(): void
    {
        $requestUrls = [];
        $requestBodies = [];
        $client = $this->createOidcClient($requestUrls, $requestBodies);

        $auth = new OpenIdConnectAuthenticator(
            self::HOST,
            self::OIDC_URL,
            self::CLIENT_ID,
            self::CLIENT_SECRET,
            self::REDIRECT_URI,
            ['openid']
        );
        $auth->setApiClient($client);

        $auth->exchangeCode('oidc-auth-code-123');

        // There should be at least two requests: discovery + token exchange
        self::assertGreaterThanOrEqual(2, count($requestUrls));

        // The token exchange should be sent to the discovered token endpoint
        self::assertStringContainsString('token', $requestUrls[1]);

        // Verify the token exchange body
        self::assertNotEmpty($requestBodies);
        parse_str($requestBodies[0], $params);
        self::assertSame('authorization_code', $params['grant_type']);
        self::assertSame('oidc-auth-code-123', $params['code']);
        self::assertSame(self::CLIENT_ID, $params['client_id']);
        self::assertSame(self::CLIENT_SECRET, $params['client_secret']);
    }
}
