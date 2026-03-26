<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Auth\OAuth\OAuth2ImplicitAuthenticator;

/**
 * Tests for the OAuth2 Implicit flow authenticator.
 */
class OAuth2ImplicitAuthenticatorTest extends TestCase
{
    private const HOST = 'https://api.example.com';
    private const AUTH_URL = 'https://auth.example.com/authorize';
    private const CLIENT_ID = 'my-implicit-client';

    /**
     * The authorization URL must contain response_type=token.
     */
    public function testBuildsAuthorizationUrl(): void
    {
        $auth = new OAuth2ImplicitAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::AUTH_URL,
            ['read', 'write']
        );

        $url = $auth->buildAuthorizationUrl('state-abc');

        self::assertStringStartsWith(self::AUTH_URL . '?', $url);
        $query = parse_url($url, PHP_URL_QUERY);
        self::assertNotNull($query);
        parse_str($query, $params);

        self::assertSame('token', $params['response_type']);
        self::assertSame('read write', $params['scope']);
        self::assertSame('state-abc', $params['state']);
    }

    /**
     * The implicit flow authorization URL must include client_id
     * per RFC 6749 section 4.2.1.
     */
    public function testIncludesClientId(): void
    {
        $auth = new OAuth2ImplicitAuthenticator(
            self::HOST,
            self::CLIENT_ID,
            self::AUTH_URL,
            ['read']
        );

        $url = $auth->buildAuthorizationUrl();

        $query = parse_url($url, PHP_URL_QUERY);
        self::assertNotNull($query, 'Authorization URL should have query parameters');
        parse_str($query, $params);

        // The implicit flow MUST include client_id in the authorization URL
        // per RFC 6749 section 4.2.1
        self::assertArrayHasKey('client_id', $params, 'Authorization URL is missing client_id parameter');
        self::assertSame(self::CLIENT_ID, $params['client_id'] ?? null);
    }
}
