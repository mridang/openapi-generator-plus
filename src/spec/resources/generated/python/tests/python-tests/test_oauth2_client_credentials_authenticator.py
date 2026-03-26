"""Tests for OAuth2ClientCredentialsAuthenticator.

These tests verify that the client credentials flow sends the correct
grant_type and client credentials when requesting a token.
"""

import json
from unittest.mock import MagicMock
from urllib.parse import parse_qs

from petstore_client.api_response import ApiResponse
from petstore_client.auth.oauth.oauth2_client_credentials_authenticator import (
    OAuth2ClientCredentialsAuthenticator,
)


def _make_authenticator():
    """Create a standard client credentials authenticator for testing."""
    return OAuth2ClientCredentialsAuthenticator(
        host='https://api.example.com',
        client_id='cc-client-id',
        client_secret='cc-client-secret',
        token_url='https://auth.example.com/token',
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


class TestOAuth2ClientCredentialsAuthenticator:
    """Verify OAuth2 Client Credentials flow."""

    def test_sends_grant_type(self):
        """The token request should include grant_type=client_credentials."""
        token_response = {
            'access_token': 'at-cc-token',
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
        assert 'grant_type=client_credentials' in body, (
            f'Expected grant_type=client_credentials in body, got: {body}'
        )

        # Verify the resulting auth header
        assert 'Authorization' in headers
        assert headers['Authorization'] == 'Bearer at-cc-token'

    def test_sends_client_credentials(self):
        """The token request should include client_id and client_secret."""
        token_response = {
            'access_token': 'at-cc-token-2',
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

        assert parsed.get('client_id') == ['cc-client-id'], (
            f'Expected client_id=cc-client-id in body, got: {parsed.get("client_id")}'
        )
        assert parsed.get('client_secret') == ['cc-client-secret'], (
            f'Expected client_secret=cc-client-secret in body, got: {parsed.get("client_secret")}'
        )
