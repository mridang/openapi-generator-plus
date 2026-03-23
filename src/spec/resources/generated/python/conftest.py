import os
import time

import pytest
from testcontainers.core.container import DockerContainer
from testcontainers.core.waiting_utils import wait_for_logs


@pytest.fixture(scope='session')
def prism_container():
    host_app_path = os.environ.get('HOST_APP_PATH', os.getcwd())
    spec_path = os.path.join(host_app_path, 'specs', 'openapi.yaml')

    container = (
        DockerContainer('stoplight/prism:5')
        .with_exposed_ports(4010)
        .with_volume_mapping(spec_path, '/tmp/openapi.yaml', 'ro')
        .with_command('mock -h 0.0.0.0 /tmp/openapi.yaml')
    )
    container.start()
    wait_for_logs(container, 'Prism is listening')
    yield container
    container.stop()


@pytest.fixture(scope='session')
def api_base_url(prism_container):
    host = prism_container.get_container_host_ip()
    port = prism_container.get_exposed_port(4010)
    return f'http://{host}:{port}'


@pytest.fixture(scope='session')
def wiremock_container():
    host_app_path = os.environ.get('HOST_APP_PATH', os.getcwd())
    keystore_path = os.path.join(host_app_path, 'certs', 'server-keystore.p12')
    mappings_path = os.path.join(host_app_path, 'wiremock', 'mappings')

    container = (
        DockerContainer('wiremock/wiremock:3.13.0')
        .with_exposed_ports(8080, 8443)
        .with_volume_mapping(keystore_path, '/tmp/keystore.p12', 'ro')
        .with_volume_mapping(mappings_path, '/home/wiremock/mappings', 'ro')
        .with_command(
            '--port 8080 --https-port 8443 --https-keystore /tmp/keystore.p12 '
            '--keystore-type PKCS12 --keystore-password changeit '
            '--key-manager-password changeit --verbose'
        )
    )
    container.start()
    wait_for_logs(container, 'port:')
    yield container
    container.stop()


@pytest.fixture(scope='session')
def squid_container():
    host_app_path = os.environ.get('HOST_APP_PATH', os.getcwd())
    squid_conf_path = os.path.join(host_app_path, 'proxy', 'squid.conf')

    container = (
        DockerContainer('ubuntu/squid:5.2-22.04_beta')
        .with_exposed_ports(3128)
        .with_volume_mapping(squid_conf_path, '/etc/squid/squid.conf', 'ro')
    )
    container.start()
    time.sleep(3)
    yield container
    container.stop()


@pytest.fixture(scope='session')
def wiremock_https_url(wiremock_container):
    host = wiremock_container.get_container_host_ip()
    port = wiremock_container.get_exposed_port(8443)
    return f'https://{host}:{port}'


@pytest.fixture(scope='session')
def wiremock_http_url(wiremock_container):
    host = wiremock_container.get_container_host_ip()
    port = wiremock_container.get_exposed_port(8080)
    return f'http://{host}:{port}'


@pytest.fixture(scope='session')
def proxy_url(squid_container):
    host = squid_container.get_container_host_ip()
    port = squid_container.get_exposed_port(3128)
    return f'http://{host}:{port}'


@pytest.fixture(scope='session')
def ca_cert_path():
    return os.path.join(os.getcwd(), 'certs', 'ca.pem')
