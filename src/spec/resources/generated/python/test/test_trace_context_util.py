"""Unit tests for TraceContextUtil."""

from unittest.mock import MagicMock, patch

from petstore_client.trace_context_util import inject_trace_context


class TestInjectTraceContext:
    """Tests for inject_trace_context function."""

    def test_should_inject_traceparent_with_mock_propagator(self) -> None:
        def mock_inject(carrier: dict[str, str]) -> None:
            carrier['traceparent'] = '00-abcdef1234567890abcdef1234567890-0123456789abcdef-01'

        mock_propagate = MagicMock()
        mock_propagate.inject = mock_inject

        with patch.dict(
            'sys.modules',
            {
                'opentelemetry': MagicMock(),
                'opentelemetry.propagate': mock_propagate,
            },
        ):
            headers: dict[str, str] = {}
            inject_trace_context(headers)
            assert 'traceparent' in headers
            assert headers['traceparent'] == '00-abcdef1234567890abcdef1234567890-0123456789abcdef-01'

    def test_should_not_inject_traceparent_without_otel(self) -> None:
        headers: dict[str, str] = {}
        inject_trace_context(headers)
        assert 'traceparent' not in headers

    def test_should_not_throw_any_exception(self) -> None:
        headers: dict[str, str] = {}
        inject_trace_context(headers)

    def test_empty_headers_do_not_cause_exception(self) -> None:
        headers: dict[str, str] = {}
        inject_trace_context(headers)
        assert len(headers) == 0

    def test_does_not_inject_tracestate_without_otel(self) -> None:
        headers: dict[str, str] = {}
        inject_trace_context(headers)
        assert 'tracestate' not in headers

    def test_preserves_authorization_header(self) -> None:
        headers: dict[str, str] = {'Authorization': 'Bearer token123'}
        inject_trace_context(headers)
        assert headers['Authorization'] == 'Bearer token123'

    def test_preserves_content_type_header(self) -> None:
        headers: dict[str, str] = {'Content-Type': 'application/json'}
        inject_trace_context(headers)
        assert headers['Content-Type'] == 'application/json'

    def test_preserves_x_request_id_header(self) -> None:
        headers: dict[str, str] = {'X-Request-ID': 'req-12345'}
        inject_trace_context(headers)
        assert headers['X-Request-ID'] == 'req-12345'

    def test_preserves_all_existing_headers(self) -> None:
        headers: dict[str, str] = {
            'Authorization': 'Bearer token',
            'Content-Type': 'application/json',
            'X-Request-ID': 'abc-123',
        }
        inject_trace_context(headers)
        assert len(headers) == 3
        assert headers['Authorization'] == 'Bearer token'
        assert headers['Content-Type'] == 'application/json'
        assert headers['X-Request-ID'] == 'abc-123'
