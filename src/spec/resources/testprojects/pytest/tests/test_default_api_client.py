import os
import pytest
from petstore_client.configuration import Configuration
from petstore_client.default_api_client import DefaultApiClient


def get_env_or_skip(name):
    value = os.environ.get(name)
    if not value:
        pytest.skip(f'Skipping: {name} not set')
    return value


class TestTlsVerificationDisabled:
    def test_makes_https_request_with_verify_ssl_false(self):
        wiremock_url = get_env_or_skip('WIREMOCK_HTTPS_URL')

        config = Configuration(base_url=wiremock_url, verify_ssl=False)
        client = DefaultApiClient(config)
        response = client.send_request('GET', wiremock_url + '/api/test', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body


class TestCustomCaBundle:
    def test_makes_https_request_with_custom_ca_cert(self):
        wiremock_url = get_env_or_skip('WIREMOCK_HTTPS_URL')
        ca_cert_path = get_env_or_skip('CA_CERT_PATH')

        config = Configuration(base_url=wiremock_url, verify_ssl=True, ssl_ca_cert=ca_cert_path)
        client = DefaultApiClient(config)
        response = client.send_request('GET', wiremock_url + '/api/test', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body


class TestHttpProxy:
    def test_makes_http_request_through_proxy(self):
        wiremock_url = get_env_or_skip('WIREMOCK_HTTP_URL')
        proxy_url = get_env_or_skip('PROXY_URL')

        config = Configuration(base_url=wiremock_url, proxy=proxy_url)
        client = DefaultApiClient(config)
        response = client.send_request('GET', wiremock_url + '/api/test', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body


class TestHttpProxyWithTls:
    def test_makes_https_request_through_proxy_with_verify_ssl_false(self):
        wiremock_url = get_env_or_skip('WIREMOCK_HTTPS_URL')
        proxy_url = get_env_or_skip('PROXY_URL')

        config = Configuration(base_url=wiremock_url, proxy=proxy_url, verify_ssl=False)
        client = DefaultApiClient(config)
        response = client.send_request('GET', wiremock_url + '/api/test', {}, None)

        assert response.status_code == 200
        assert 'success' in response.body
