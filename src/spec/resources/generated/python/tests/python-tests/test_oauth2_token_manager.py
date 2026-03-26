"""Tests for OAuth2TokenManager.

These tests verify that the token manager correctly extracts access tokens,
stores refresh tokens, and detects token expiry.
"""

import json
import time
from unittest.mock import MagicMock

from petstore_client.api_response import ApiResponse
from petstore_client.auth.oauth.oauth2_token_manager import OAuth2TokenManager


def _mock_api_client(response_body, status_code=200):
    """Create a mock ApiClient that returns the given JSON response body."""
    mock = MagicMock()
    mock.send_request.return_value = ApiResponse(
        status_code=status_code,
        body=json.dumps(response_body),
        headers={'Content-Type': 'application/json'},
    )
    return mock


class TestOAuth2TokenManager:
    """Verify OAuth2TokenManager token lifecycle."""

    def test_stores_refresh_token(self):
        """After fetching a token response with 'refresh_token', the manager
        should store it so it can be used in subsequent refresh requests.

        BUG: The token manager does not have a _refresh_token field and does
        not store the refresh_token from the response. This means refresh
        flows cannot send back the refresh_token value.
        """
        token_response = {
            'access_token': 'at-12345',
            'refresh_token': 'rt-67890',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }

        mock_client = _mock_api_client(token_response)
        manager = OAuth2TokenManager()
        manager.set_api_client(mock_client)

        manager.get_access_token('https://auth.example.com/token', {
            'grant_type': 'authorization_code',
            'code': 'auth-code-123',
        })

        # The manager should have stored the refresh_token for later use
        has_refresh_token = hasattr(manager, '_refresh_token') and manager._refresh_token is not None
        assert has_refresh_token, (
            'OAuth2TokenManager does not store refresh_token from token response. '
            'The _refresh_token field is missing or None.'
        )

    def test_extracts_access_token(self):
        """The manager should extract 'access_token' from the token response."""
        token_response = {
            'access_token': 'at-valid-token',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }

        mock_client = _mock_api_client(token_response)
        manager = OAuth2TokenManager()
        manager.set_api_client(mock_client)

        token = manager.get_access_token('https://auth.example.com/token', {
            'grant_type': 'client_credentials',
        })

        assert token == 'at-valid-token', (
            f'Expected access_token "at-valid-token", got: {token}'
        )

    def test_detects_token_expiry(self):
        """The manager should re-fetch when the cached token has expired.

        We simulate expiry by setting a very short expires_in so that by
        the time of the second call the token is expired (the manager
        subtracts a 30-second buffer).
        """
        first_response = {
            'access_token': 'token-1',
            'expires_in': 31,  # 31 - 30 buffer = 1 second effective
            'token_type': 'Bearer',
        }
        second_response = {
            'access_token': 'token-2',
            'expires_in': 3600,
            'token_type': 'Bearer',
        }

        mock_client = MagicMock()
        mock_client.send_request.side_effect = [
            ApiResponse(status_code=200, body=json.dumps(first_response), headers={}),
            ApiResponse(status_code=200, body=json.dumps(second_response), headers={}),
        ]

        manager = OAuth2TokenManager()
        manager.set_api_client(mock_client)

        params = {'grant_type': 'client_credentials'}

        token1 = manager.get_access_token('https://auth.example.com/token', params)
        assert token1 == 'token-1'

        # Force the token to appear expired by manipulating the expiry time
        manager._token_expiry = time.time() - 1

        token2 = manager.get_access_token('https://auth.example.com/token', params)
        assert token2 == 'token-2', (
            f'Expected re-fetched token "token-2" after expiry, got: {token2}'
        )
        assert mock_client.send_request.call_count == 2, (
            'Expected two token fetches (initial + refresh after expiry)'
        )
