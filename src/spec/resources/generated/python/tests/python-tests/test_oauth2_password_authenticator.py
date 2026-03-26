"""Tests for OAuth2PasswordAuthenticator.

These tests verify that the password flow sends the correct grant_type,
username, and password when requesting a token.
"""

import json
from unittest.mock import MagicMock
from urllib.parse import parse_qs

from petstore_client.api_response import ApiResponse
from petstore_client.auth.oauth.oauth2_password_authenticator import (
    OAuth2PasswordAuthenticator,
)


def _make_authenticator():
    """Create a standard password flow authenticator for testing."""
    return OAuth2PasswordAuthenticator(
        host='https://api.example.com',
        client_id='pw-client-id',
        client_secret='pw-client-secret',
        token_url='https://auth.example.com/token',
        username='testuser',
        password='testpass',
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


class TestOAuth2PasswordAuthenticator:
    """Verify OAuth2 Password (Resource Owner) flow."""

    def test_sends_grant_type(self):
        """The token request should include grant_type=password."""
        token_response = {
            'access_token': 'at-pw-token',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }

        mock_client = _mock_api_client(token_response)
        auth = _make_authenticator()
        auth.set_api_client(mock_client)

        headers = auth.get_auth_headers()

        assert mock_client.send_request.called, 'Expected a token request to be made'

        call_args = mock_client.send_request.call_args
        method = call_args[0][0]
        url = call_args[0][1]
        body = call_args[0][3]

        assert method == 'POST'
        assert url == 'https://auth.example.com/token'
        assert 'grant_type=password' in body, (
            f'Expected grant_type=password in body, got: {body}'
        )

        # Verify the resulting auth header
        assert 'Authorization' in headers
        assert headers['Authorization'] == 'Bearer at-pw-token'

    def test_sends_username_and_password(self):
        """The token request should include the username and password."""
        token_response = {
            'access_token': 'at-pw-token-2',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }

        mock_client = _mock_api_client(token_response)
        auth = _make_authenticator()
        auth.set_api_client(mock_client)

        auth.get_auth_headers()

        call_args = mock_client.send_request.call_args
        body = call_args[0][3]

        # Parse the URL-encoded body to check individual parameters
        parsed = parse_qs(body)

        assert parsed.get('username') == ['testuser'], (
            f'Expected username=testuser in body, got: {parsed.get("username")}'
        )
        assert parsed.get('password') == ['testpass'], (
            f'Expected password=testpass in body, got: {parsed.get("password")}'
        )
