import json

import pytest

from petstore_client.default_api_client import DefaultApiClient
from petstore_client.transport_options import TransportOptions


class TestTlsVerificationDisabled:
    def test_makes_https_request_with_verify_ssl_false(self, wiremock_https_url):
        transport = TransportOptions.builder().verify_ssl(False).build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_https_url + '/api/test', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body


class TestCustomCaBundle:
    def test_makes_https_request_with_custom_ca_cert(self, wiremock_https_url, ca_cert_path):
        transport = TransportOptions.builder().verify_ssl(True).ca_cert_path(ca_cert_path).build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_https_url + '/api/test', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body


class TestHttpProxy:
    def test_makes_http_request_through_proxy(self, wiremock_http_url, proxy_url):
        transport = TransportOptions.builder().proxy(proxy_url).build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_http_url + '/api/test', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body


class TestHttpProxyWithTls:
    def test_makes_https_request_through_proxy_with_verify_ssl_false(self, wiremock_https_url, proxy_url):
        transport = TransportOptions.builder().proxy(proxy_url).verify_ssl(False).build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_https_url + '/api/test', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body


class TestHttpCompression:
    def test_decompresses_gzip_response(self):
        client = DefaultApiClient()
        response = client.send_request(
            'GET', 'https://jsonplaceholder.typicode.com/posts/1', {'Accept-Encoding': 'gzip'}, None
        )

        assert response.status_code == 200
        assert 'userId' in response.body

    def test_decompresses_brotli_response(self):
        client = DefaultApiClient()
        response = client.send_request(
            'GET', 'https://jsonplaceholder.typicode.com/posts/1', {'Accept-Encoding': 'br'}, None
        )

        assert response.status_code == 200
        assert 'userId' in response.body

    def test_decompresses_zstd_response(self):
        client = DefaultApiClient()
        response = client.send_request(
            'GET', 'https://jsonplaceholder.typicode.com/posts/1', {'Accept-Encoding': 'zstd'}, None
        )

        assert response.status_code == 200
        assert 'userId' in response.body


class TestRequestTimeout:
    def test_times_out_on_slow_endpoint(self, wiremock_http_url):
        transport = TransportOptions.builder().timeout(1).build()
        client = DefaultApiClient(transport)

        with pytest.raises(Exception):
            client.send_request('GET', wiremock_http_url + '/api/slow', {}, None)


class TestUserAgentHeader:
    def test_injects_custom_user_agent_header(self, wiremock_http_url):
        transport = TransportOptions.builder().user_agent('MyApp/1.0').build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_http_url + '/api/echo-headers', {}, None)

        assert response.status_code == 200
        body = json.loads(response.body)
        assert body['user-agent'] == 'MyApp/1.0'


class TestRequestIdInjection:
    def test_injects_request_id_header_with_uuid_format(self, wiremock_http_url):
        transport = TransportOptions.builder().inject_request_id(True).build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_http_url + '/api/echo-headers', {}, None)

        assert response.status_code == 200
        body = json.loads(response.body)
        request_id = body['x-request-id']
        assert request_id
        import re

        assert re.match(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
            request_id,
        )

    def test_generates_unique_request_id_per_request(self, wiremock_http_url):
        transport = TransportOptions.builder().inject_request_id(True).build()
        client = DefaultApiClient(transport)

        response1 = client.send_request('GET', wiremock_http_url + '/api/echo-headers', {}, None)
        request_id1 = json.loads(response1.body)['x-request-id']

        response2 = client.send_request('GET', wiremock_http_url + '/api/echo-headers', {}, None)
        request_id2 = json.loads(response2.body)['x-request-id']

        assert request_id1 != request_id2


class TestDefaultHeaders:
    def test_includes_transport_default_headers(self, wiremock_http_url):
        transport = TransportOptions.builder().default_header('X-Custom', 'custom-value').build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_http_url + '/api/echo-headers', {}, None)

        assert response.status_code == 200
        body = json.loads(response.body)
        assert body['x-custom'] == 'custom-value'

    def test_caller_headers_override_transport_defaults(self, wiremock_http_url):
        transport = TransportOptions.builder().default_header('Accept', 'text/plain').build()
        client = DefaultApiClient(transport)
        response = client.send_request(
            'GET', wiremock_http_url + '/api/echo-headers', {'Accept': 'application/json'}, None
        )

        assert response.status_code == 200
        body = json.loads(response.body)
        assert body['accept'] == 'application/json'


class TestRedirectHandling:
    def test_follows_redirects_when_enabled(self, wiremock_http_url):
        transport = TransportOptions.builder().follow_redirects(True).build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_http_url + '/api/redirect', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body

    def test_returns_redirect_when_disabled(self, wiremock_http_url):
        transport = TransportOptions.builder().follow_redirects(False).build()
        client = DefaultApiClient(transport)
        response = client.send_request('GET', wiremock_http_url + '/api/redirect', {}, None)

        assert response.status_code == 302
