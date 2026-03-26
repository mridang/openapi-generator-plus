"""Tests for OAuth2AuthorizationCodeAuthenticator.

These tests verify that the authorization code flow correctly builds
authorization URLs, exchanges codes for tokens, and handles refresh
with the actual refresh_token value.
"""

import json
from unittest.mock import MagicMock
from urllib.parse import parse_qs, urlencode, urlparse

from petstore_client.api_response import ApiResponse
from petstore_client.auth.oauth.oauth2_auth_code_authenticator import (
    OAuth2AuthorizationCodeAuthenticator,
)


def _make_authenticator():
    """Create a standard auth code authenticator for testing."""
    return OAuth2AuthorizationCodeAuthenticator(
        host='https://api.example.com',
        client_id='my-client-id',
        client_secret='my-client-secret',
        authorization_url='https://auth.example.com/authorize',
        token_url='https://auth.example.com/token',
        redirect_uri='https://app.example.com/callback',
        scopes=['read', 'write'],
    )


def _mock_api_client(response_body, status_code=200):
    """Create a mock ApiClient returning the given JSON response."""
    mock = MagicMock()
    mock.send_request.return_value = ApiResponse(
        status_code=status_code,
        body=json.dumps(response_body),
        headers={'Content-Type': 'application/json'},
    )
    return mock


class TestOAuth2AuthCodeAuthenticator:
    """Verify OAuth2 Authorization Code flow."""

    def test_builds_authorization_url(self):
        """The authorization URL should include response_type=code, client_id,
        and redirect_uri as query parameters.
        """
        auth = _make_authenticator()
        url = auth.build_authorization_url(state='csrf-state-123')

        parsed = urlparse(url)
        qs = parse_qs(parsed.query)

        assert parsed.scheme == 'https'
        assert parsed.netloc == 'auth.example.com'
        assert parsed.path == '/authorize'

        assert qs.get('response_type') == ['code'], (
            f'Expected response_type=code, got: {qs.get("response_type")}'
        )
        assert qs.get('client_id') == ['my-client-id'], (
            f'Expected client_id=my-client-id, got: {qs.get("client_id")}'
        )
        assert qs.get('redirect_uri') == ['https://app.example.com/callback'], (
            f'Expected redirect_uri, got: {qs.get("redirect_uri")}'
        )
        assert qs.get('state') == ['csrf-state-123'], (
            f'Expected state=csrf-state-123, got: {qs.get("state")}'
        )
        assert qs.get('scope') == ['read write'], (
            f'Expected scope=read write, got: {qs.get("scope")}'
        )

    def test_exchanges_code_for_token(self):
        """Exchanging an auth code should POST grant_type=authorization_code
        to the token endpoint.
        """
        token_response = {
            'access_token': 'at-from-code',
            'refresh_token': 'rt-from-code',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }

        mock_client = _mock_api_client(token_response)
        auth = _make_authenticator()
        auth.set_api_client(mock_client)

        auth.exchange_code('auth-code-xyz')

        # Verify the token request was made
        assert mock_client.send_request.called, 'Expected a token request to be made'

        call_args = mock_client.send_request.call_args
        method = call_args[0][0]
        url = call_args[0][1]
        body = call_args[0][3]

        assert method == 'POST'
        assert url == 'https://auth.example.com/token'

        # The body is URL-encoded form data
        assert 'grant_type=authorization_code' in body, (
            f'Expected grant_type=authorization_code in body, got: {body}'
        )
        assert 'code=auth-code-xyz' in body, (
            f'Expected code=auth-code-xyz in body, got: {body}'
        )

    def test_refresh_includes_refresh_token(self):
        """When refreshing a token, the request should include the actual
        refresh_token value, not just the grant_type.

        BUG: The get_auth_headers method sends grant_type=refresh_token but
        does not include the actual refresh_token value in the request params.
        """
        token_response = {
            'access_token': 'at-initial',
            'refresh_token': 'rt-for-refresh',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }

        mock_client = _mock_api_client(token_response)
        auth = _make_authenticator()
        auth.set_api_client(mock_client)

        # First, exchange a code to get tokens
        auth.exchange_code('auth-code-xyz')

        # Force the token to expire so get_auth_headers triggers a refresh
        auth._token_manager._token_expiry = 0

        # Now request auth headers, which should trigger a refresh
        mock_client.send_request.reset_mock()
        refresh_response = {
            'access_token': 'at-refreshed',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }
        mock_client.send_request.return_value = ApiResponse(
            status_code=200,
            body=json.dumps(refresh_response),
            headers={'Content-Type': 'application/json'},
        )

        auth.get_auth_headers()

        # The refresh request should include refresh_token in the body
        assert mock_client.send_request.called, 'Expected a refresh token request'
        call_args = mock_client.send_request.call_args
        body = call_args[0][3]

        assert 'grant_type=refresh_token' in body, (
            f'Expected grant_type=refresh_token in body, got: {body}'
        )
        assert 'refresh_token=' in body, (
            f'BUG: Refresh request does not include refresh_token value. Body: {body}'
        )
        # The actual refresh token value should be present, not empty
        assert 'refresh_token=rt-for-refresh' in body, (
            f'BUG: Refresh request should send refresh_token=rt-for-refresh. Body: {body}'
        )
