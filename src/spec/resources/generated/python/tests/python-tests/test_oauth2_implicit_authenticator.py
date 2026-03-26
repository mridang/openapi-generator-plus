"""Tests for OAuth2ImplicitAuthenticator.

These tests verify that the implicit flow correctly builds authorization
URLs with the required parameters.
"""

from urllib.parse import parse_qs, urlparse

from petstore_client.auth.oauth.oauth2_implicit_authenticator import (
    OAuth2ImplicitAuthenticator,
)


def _make_authenticator():
    """Create a standard implicit flow authenticator for testing."""
    return OAuth2ImplicitAuthenticator(
        host='https://api.example.com',
        client_id='my-client-id',
        authorization_url='https://auth.example.com/authorize',
        scopes=['read', 'write'],
    )


class TestOAuth2ImplicitAuthenticator:
    """Verify OAuth2 Implicit flow."""

    def test_builds_authorization_url(self):
        """The authorization URL should include response_type=token."""
        auth = _make_authenticator()
        url = auth.build_authorization_url(state='state-abc')

        parsed = urlparse(url)
        qs = parse_qs(parsed.query)

        assert parsed.scheme == 'https'
        assert parsed.netloc == 'auth.example.com'
        assert parsed.path == '/authorize'

        assert qs.get('response_type') == ['token'], (
            f'Expected response_type=token, got: {qs.get("response_type")}'
        )
        assert qs.get('state') == ['state-abc'], (
            f'Expected state=state-abc, got: {qs.get("state")}'
        )
        assert qs.get('scope') == ['read write'], (
            f'Expected scope=read write, got: {qs.get("scope")}'
        )

    def test_includes_client_id(self):
        """The authorization URL should include the client_id parameter."""
        auth = _make_authenticator()
        url = auth.build_authorization_url()

        parsed = urlparse(url)
        qs = parse_qs(parsed.query)

        assert 'client_id' in qs, (
            f'BUG: Authorization URL is missing client_id parameter. '
            f'The implicit flow requires client_id in the authorization request. '
            f'Query string: {parsed.query}'
        )
