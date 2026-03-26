"""Tests for BaseApi query-parameter handling.

These tests verify that the generated BaseApi correctly constructs URLs
with query parameters. A mock ApiClient captures the URL passed to
send_request so we can inspect the result without making real HTTP calls.
"""

from unittest.mock import MagicMock
from urllib.parse import parse_qs, urlparse

from petstore_client.api.base_api import BaseApi
from petstore_client.api_response import ApiResponse
from petstore_client.configuration import Configuration


def _make_api(mock_client):
    """Create a BaseApi wired to a mock ApiClient and a fixed base URL."""
    config = Configuration(base_url='https://api.example.com')
    return BaseApi(api_client=mock_client, config=config)


def _ok_response():
    """Return a minimal 200 OK ApiResponse."""
    return ApiResponse(status_code=200, body='{}', headers={'Content-Type': 'application/json'})


class TestBaseApiQueryParams:
    """Verify URL construction in _invoke_api."""

    def test_expands_array_query_params(self):
        """Array values should expand to tags=a&tags=b, not tags=[a, b].

        The bug: Python's urlencode does not expand list values by default.
        You must pass doseq=True to get repeated key-value pairs.
        """
        mock_client = MagicMock()
        mock_client.send_request.return_value = _ok_response()
        api = _make_api(mock_client)

        api._invoke_api(
            'GET',
            '/pet/findByTags',
            {'tags': ['a', 'b']},
            {},
            None,
            ['application/json'],
            'application/json',
            'str',
        )

        called_url = mock_client.send_request.call_args[0][1]
        parsed = urlparse(called_url)
        qs = parse_qs(parsed.query)

        # Correct behaviour: each value appears individually under the 'tags' key
        assert 'tags' in qs, f'Expected tags param in URL, got: {called_url}'
        assert sorted(qs['tags']) == ['a', 'b'], (
            f'Expected tags=a&tags=b but got query string: {parsed.query}'
        )

    def test_serializes_boolean_query_params(self):
        """Boolean query parameters should serialize as 'true' or 'false'."""
        mock_client = MagicMock()
        mock_client.send_request.return_value = _ok_response()
        api = _make_api(mock_client)

        api._invoke_api(
            'GET',
            '/pet/search',
            {'active': True},
            {},
            None,
            ['application/json'],
            'application/json',
            'str',
        )

        called_url = mock_client.send_request.call_args[0][1]
        parsed = urlparse(called_url)

        # urlencode converts True to 'True' (Python repr) instead of 'true'
        assert 'active=true' in parsed.query or 'active=True' in parsed.query, (
            f'Expected active=true in query string, got: {parsed.query}'
        )
        # The ideal serialization is lowercase 'true'
        assert 'active=true' in parsed.query, (
            f'Boolean should serialize as lowercase "true", got: {parsed.query}'
        )

    def test_serializes_number_query_params(self):
        """Numeric query parameters should serialize as their string value."""
        mock_client = MagicMock()
        mock_client.send_request.return_value = _ok_response()
        api = _make_api(mock_client)

        api._invoke_api(
            'GET',
            '/pet/search',
            {'limit': 10},
            {},
            None,
            ['application/json'],
            'application/json',
            'str',
        )

        called_url = mock_client.send_request.call_args[0][1]
        parsed = urlparse(called_url)

        assert 'limit=10' in parsed.query, (
            f'Expected limit=10 in query string, got: {parsed.query}'
        )

    def test_handles_empty_query_params(self):
        """An empty query-params dict should not append '?' to the URL."""
        mock_client = MagicMock()
        mock_client.send_request.return_value = _ok_response()
        api = _make_api(mock_client)

        api._invoke_api(
            'GET',
            '/pet/1',
            {},
            {},
            None,
            ['application/json'],
            'application/json',
            'str',
        )

        called_url = mock_client.send_request.call_args[0][1]
        assert '?' not in called_url, (
            f'URL should not contain "?" with empty query params, got: {called_url}'
        )
