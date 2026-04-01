from types import MappingProxyType

from petstore_client.transport_options import TransportOptions


class TestTransportOptions:
    def test_builder_produces_correct_defaults(self) -> None:
        opts = TransportOptions.builder().build()

        assert opts.verify_ssl is True
        assert opts.ca_cert_path is None
        assert opts.proxy is None
        assert opts.timeout is None
        assert opts.follow_redirects is True
        assert opts.max_redirects is None
        assert opts.user_agent == 'petstore_client/1.0.0 (python)'
        assert len(opts.default_headers) == 0
        assert opts.inject_request_id is False

    def test_builder_sets_all_fields(self) -> None:
        opts = (
            TransportOptions.builder()
            .verify_ssl(False)
            .ca_cert_path('/path/to/ca.pem')
            .proxy('http://proxy:8080')
            .timeout(5000)
            .follow_redirects(False)
            .max_redirects(3)
            .user_agent('TestAgent/1.0')
            .default_header('X-Custom', 'value')
            .inject_request_id(True)
            .build()
        )

        assert opts.verify_ssl is False
        assert opts.ca_cert_path == '/path/to/ca.pem'
        assert opts.proxy == 'http://proxy:8080'
        assert opts.timeout == 5000
        assert opts.follow_redirects is False
        assert opts.max_redirects == 3
        assert opts.user_agent == 'TestAgent/1.0'
        assert opts.default_headers['X-Custom'] == 'value'
        assert opts.inject_request_id is True

    def test_default_headers_is_immutable(self) -> None:
        headers = {'X-Original': 'original'}

        opts = TransportOptions.builder().default_headers(headers).build()

        headers['X-Added'] = 'added'

        assert len(opts.default_headers) == 1
        assert opts.default_headers['X-Original'] == 'original'
        assert 'X-Added' not in opts.default_headers
        assert isinstance(opts.default_headers, MappingProxyType)
