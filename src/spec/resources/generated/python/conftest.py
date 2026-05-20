import os
import time

import docker
import pytest
from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import LogMessageWaitStrategy

@pytest.fixture(scope="session")
def prism_container():
    host_app_path = os.environ.get("HOST_APP_PATH", os.getcwd())
    spec_path = os.path.join(host_app_path, "test", "fixtures", "openapi.yaml")

    container = (
        DockerContainer("stoplight/prism:5")
        .with_exposed_ports(4010)
        .with_volume_mapping(spec_path, "/tmp/openapi.yaml", "ro")
        .with_command("mock -m false -h 0.0.0.0 /tmp/openapi.yaml")
        .waiting_for(LogMessageWaitStrategy("Prism is listening"))
    )
    container.start()
    yield container
    container.stop()

@pytest.fixture(scope="session")
def api_base_url(prism_container):
    host = prism_container.get_container_host_ip()
    port = prism_container.get_exposed_port(4010)
    return f"http://{host}:{port}"

@pytest.fixture(scope="session")
def proxy_network():
    client = docker.from_env()
    network = client.networks.create("proxy-test-network")
    yield network
    network.remove()

@pytest.fixture(scope="session")
def wiremock_container(proxy_network):
    host_app_path = os.environ.get("HOST_APP_PATH", os.getcwd())
    keystore_path = os.path.join(host_app_path, "test", "fixtures", "certs", "server-keystore.p12")
    mappings_path = os.path.join(host_app_path, "test", "fixtures", "wiremock", "mappings")

    container = (
        DockerContainer("wiremock/wiremock:3.13.0")
        .with_exposed_ports(8080, 8443)
        .with_volume_mapping(keystore_path, "/tmp/keystore.p12", "ro")
        .with_volume_mapping(mappings_path, "/home/wiremock/mappings", "ro")
        .with_command(
            "--port 8080 --https-port 8443 --https-keystore /tmp/keystore.p12 "
            "--keystore-type PKCS12 --keystore-password changeit "
            "--key-manager-password changeit --verbose"
        )
        .waiting_for(LogMessageWaitStrategy("port:"))
    )
    container.start()
    proxy_network.connect(container._container.id, aliases=["wiremock"])
    yield container
    container.stop()

@pytest.fixture(scope="session")
def squid_container(proxy_network):
    host_app_path = os.environ.get("HOST_APP_PATH", os.getcwd())
    squid_conf_path = os.path.join(host_app_path, "test", "fixtures", "proxy", "squid.conf")

    container = (
        DockerContainer("ubuntu/squid:5.2-22.04_beta")
        .with_exposed_ports(3128)
        .with_volume_mapping(squid_conf_path, "/etc/squid/squid.conf", "ro")
    )
    container.start()
    proxy_network.connect(container._container.id)
    time.sleep(3)
    yield container
    container.stop()

@pytest.fixture(scope="session")
def wiremock_https_url(wiremock_container):
    host = wiremock_container.get_container_host_ip()
    port = wiremock_container.get_exposed_port(8443)
    return f"https://{host}:{port}"

@pytest.fixture(scope="session")
def wiremock_http_url(wiremock_container):
    host = wiremock_container.get_container_host_ip()
    port = wiremock_container.get_exposed_port(8080)
    return f"http://{host}:{port}"

@pytest.fixture(scope="session")
def wiremock_internal_http_url():
    return "http://wiremock:8080"

@pytest.fixture(scope="session")
def wiremock_internal_https_url():
    return "https://wiremock:8443"

@pytest.fixture(scope="session")
def proxy_url(squid_container):
    host = squid_container.get_container_host_ip()
    port = squid_container.get_exposed_port(3128)
    return f"http://{host}:{port}"

@pytest.fixture(scope="session")
def ca_cert_path():
    return os.path.join(os.getcwd(), "test", "fixtures", "certs", "ca.pem")
