"""Tests for OpenIdConnectAuthenticator.

These tests verify that the OIDC authenticator discovers endpoints from
the well-known configuration document and delegates to the authorization
code flow for building URLs and exchanging tokens.
"""

import json
from unittest.mock import MagicMock
from urllib.parse import parse_qs, urlparse

from petstore_client.api_response import ApiResponse
from petstore_client.auth.oauth.openid_connect_authenticator import (
    OpenIdConnectAuthenticator,
)


def _discovery_document():
    """Return a minimal OIDC discovery document."""
    return {
        'issuer': 'https://auth.example.com',
        'authorization_endpoint': 'https://auth.example.com/authorize',
        'token_endpoint': 'https://auth.example.com/token',
        'userinfo_endpoint': 'https://auth.example.com/userinfo',
        'jwks_uri': 'https://auth.example.com/.well-known/jwks.json',
    }


def _make_authenticator():
    """Create a standard OIDC authenticator for testing."""
    return OpenIdConnectAuthenticator(
        host='https://api.example.com',
        openid_connect_url='https://auth.example.com/.well-known/openid-configuration',
        client_id='oidc-client-id',
        client_secret='oidc-client-secret',
        redirect_uri='https://app.example.com/callback',
        scopes=['openid', 'profile'],
    )


def _mock_api_client_with_discovery_and_token(discovery_doc, token_response):
    """Create a mock ApiClient that returns the discovery doc first,
    then the token response on subsequent calls.
    """
    mock = MagicMock()
    mock.send_request.side_effect = [
        # First call: OIDC discovery
        ApiResponse(
            status_code=200,
            body=json.dumps(discovery_doc),
            headers={'Content-Type': 'application/json'},
        ),
        # Second call: token exchange
        ApiResponse(
            status_code=200,
            body=json.dumps(token_response),
            headers={'Content-Type': 'application/json'},
        ),
    ]
    return mock


class TestOpenIdConnectAuthenticator:
    """Verify OpenID Connect authenticator."""

    def test_builds_authorization_url(self):
        """The OIDC authenticator should discover the authorization endpoint
        and build a URL with response_type=code and client_id.
        """
        discovery = _discovery_document()

        mock_client = MagicMock()
        mock_client.send_request.return_value = ApiResponse(
            status_code=200,
            body=json.dumps(discovery),
            headers={'Content-Type': 'application/json'},
        )

        auth = _make_authenticator()
        auth.set_api_client(mock_client)

        url = auth.build_authorization_url(state='oidc-state')

        # Verify discovery was fetched
        assert mock_client.send_request.called, 'Expected a discovery request'
        discovery_call = mock_client.send_request.call_args_list[0]
        assert discovery_call[0][0] == 'GET'
        assert discovery_call[0][1] == 'https://auth.example.com/.well-known/openid-configuration'

        # Verify the authorization URL uses the discovered endpoint
        parsed = urlparse(url)
        qs = parse_qs(parsed.query)

        assert parsed.netloc == 'auth.example.com'
        assert parsed.path == '/authorize'
        assert qs.get('response_type') == ['code'], (
            f'Expected response_type=code, got: {qs.get("response_type")}'
        )
        assert qs.get('client_id') == ['oidc-client-id'], (
            f'Expected client_id=oidc-client-id, got: {qs.get("client_id")}'
        )
        assert qs.get('state') == ['oidc-state'], (
            f'Expected state=oidc-state, got: {qs.get("state")}'
        )

    def test_obtains_token(self):
        """The OIDC authenticator should exchange an auth code for a token
        using the discovered token endpoint.
        """
        discovery = _discovery_document()
        token_response = {
            'access_token': 'oidc-access-token',
            'refresh_token': 'oidc-refresh-token',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }

        mock_client = _mock_api_client_with_discovery_and_token(discovery, token_response)
        auth = _make_authenticator()
        auth.set_api_client(mock_client)

        auth.exchange_code('oidc-auth-code')

        # Should have made 2 calls: discovery + token exchange
        assert mock_client.send_request.call_count == 2, (
            f'Expected 2 HTTP calls (discovery + token), '
            f'got {mock_client.send_request.call_count}'
        )

        # Verify the token exchange request
        token_call = mock_client.send_request.call_args_list[1]
        method = token_call[0][0]
        url = token_call[0][1]
        body = token_call[0][3]

        assert method == 'POST'
        assert url == 'https://auth.example.com/token'
        assert 'grant_type=authorization_code' in body, (
            f'Expected grant_type=authorization_code in body, got: {body}'
        )
        assert 'code=oidc-auth-code' in body, (
            f'Expected code=oidc-auth-code in body, got: {body}'
        )
