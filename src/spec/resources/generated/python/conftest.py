import importlib.util
import os
import socket
import struct
import threading
import time

import pytest

# Every dependency an emitted test imports is listed, with the test that needs
# it, in .openapi-generator/DEV-DEPENDENCIES. A client keep-lists its own
# pyproject.toml, so a dependency the generator started using is missing there
# until someone copies it across -- and the whole suite then dies at collection
# with an ImportError from whichever module happened to be imported first.
# Probing here turns that into one message that names the file to reconcile
# against. The distributions below are imported inside the fixtures rather than
# at module scope so this check runs before the failure it is reporting on.
DEV_DEPENDENCIES = ".openapi-generator/DEV-DEPENDENCIES"

_REQUIRED_TEST_DISTRIBUTIONS = {
    "docker": "docker",
    "opentelemetry.sdk.trace": "opentelemetry-sdk",
    "pytest_asyncio": "pytest-asyncio",
    "pytest_cov": "pytest-cov",
    "testcontainers": "testcontainers",
}


def _is_installed(module):
    # find_spec imports the parent package of a dotted name, so it raises
    # rather than returning None when the parent is the part that is missing.
    try:
        return importlib.util.find_spec(module) is not None
    except (ImportError, ValueError):
        return False


def _assert_test_dependencies_installed():
    missing = sorted(
        distribution
        for module, distribution in _REQUIRED_TEST_DISTRIBUTIONS.items()
        if not _is_installed(module)
    )
    if missing:
        raise RuntimeError(
            "Missing test dependencies: "
            + ", ".join(missing)
            + ". Every dependency the generated tests import is listed in "
            + DEV_DEPENDENCIES
            + "; add the missing entries to this project's dependency manifest "
            "and reinstall."
        )


_assert_test_dependencies_installed()

# CHASM_HTTP_URL and PROXY_URL point at instances that are already running;
# the fixtures below then start nothing and read the addresses from the
# environment.
_EXTERNAL = bool(os.environ.get("CHASM_HTTP_URL") and os.environ.get("PROXY_URL"))


@pytest.fixture(scope="session")
def chasm_container(proxy_network):
    if _EXTERNAL:
        yield None
        return
    from testcontainers.core.container import DockerContainer
    from testcontainers.core.wait_strategies import LogMessageWaitStrategy

    host_app_path = os.environ.get("HOST_APP_PATH", os.getcwd())
    spec_path = os.path.join(host_app_path, "test", "fixtures", "openapi.yaml")
    cert_path = os.path.join(host_app_path, "test", "fixtures", "certs", "server.pem")
    key_path = os.path.join(
        host_app_path, "test", "fixtures", "certs", "server-key.pem"
    )

    container = (
        DockerContainer("mridang/chasm:1.3.0")
        .with_exposed_ports(4010, 8443)
        .with_volume_mapping(spec_path, "/tmp/openapi.yaml", "ro")
        .with_volume_mapping(cert_path, "/certs/cert.pem", "ro")
        .with_volume_mapping(key_path, "/certs/key.pem", "ro")
        .with_command(
            "mock /tmp/openapi.yaml --host 0.0.0.0 "
            "--tls-cert /certs/cert.pem --tls-key /certs/key.pem --tls-port 8443"
        )
        .waiting_for(LogMessageWaitStrategy("Listening on"))
    )
    container.start()
    proxy_network.connect(container.get_wrapped_container().id, aliases=["chasm"])
    yield container
    container.stop()


@pytest.fixture(scope="session")
def api_base_url(chasm_container):
    if _EXTERNAL:
        return os.environ["CHASM_HTTP_URL"]
    host = chasm_container.get_container_host_ip()
    port = chasm_container.get_exposed_port(4010)
    return f"http://{host}:{port}"


@pytest.fixture(scope="session")
def chasm_http_url(chasm_container):
    if _EXTERNAL:
        return os.environ["CHASM_HTTP_URL"]
    host = chasm_container.get_container_host_ip()
    port = chasm_container.get_exposed_port(4010)
    return f"http://{host}:{port}"


@pytest.fixture(scope="session")
def chasm_https_url(chasm_container):
    if _EXTERNAL:
        return os.environ["CHASM_HTTPS_URL"]
    host = chasm_container.get_container_host_ip()
    port = chasm_container.get_exposed_port(8443)
    return f"https://{host}:{port}"


@pytest.fixture(scope="session")
def chasm_internal_http_url():
    return "http://chasm:4010"


@pytest.fixture(scope="session")
def chasm_internal_https_url():
    return "https://chasm:8443"


@pytest.fixture(scope="session")
def proxy_network():
    if _EXTERNAL:
        yield None
        return
    import docker

    client = docker.from_env()
    # A unique name: a run killed before teardown leaves its network behind, and
    # a fixed name would make every later run fail with a 409 Conflict.
    network = client.networks.create("proxy-test-network-" + os.urandom(4).hex())
    yield network
    network.remove()


@pytest.fixture(scope="session")
def squid_container(proxy_network):
    if _EXTERNAL:
        yield None
        return
    from testcontainers.core.container import DockerContainer

    host_app_path = os.environ.get("HOST_APP_PATH", os.getcwd())
    squid_conf_path = os.path.join(
        host_app_path, "test", "fixtures", "proxy", "squid.conf"
    )

    # ubuntu/squid declares VOLUME /var/log/squid and VOLUME /var/spool/squid,
    # so every proxy container Docker creates leaves two anonymous volumes
    # behind after the suite stops it. Mounting both as tmpfs keeps the
    # container writable without allocating a volume. mode=1777 because squid
    # drops to the unprivileged `proxy` user before it opens its logs.
    container = (
        DockerContainer("ubuntu/squid:6.10-24.10_beta")
        .with_exposed_ports(3128, 3129)
        .with_volume_mapping(squid_conf_path, "/etc/squid/squid.conf", "ro")
        .with_tmpfs_mount("/var/log/squid", "rw,mode=1777")
        .with_tmpfs_mount("/var/spool/squid", "rw,mode=1777")
    )
    container.start()
    proxy_network.connect(container.get_wrapped_container().id)
    time.sleep(3)
    yield container
    container.stop()


@pytest.fixture(scope="session")
def proxy_url(squid_container):
    if _EXTERNAL:
        return os.environ["PROXY_URL"]
    host = squid_container.get_container_host_ip()
    port = squid_container.get_exposed_port(3128)
    return f"http://{host}:{port}"


@pytest.fixture(scope="session")
def proxy_auth_host_port(squid_container):
    """host:port of the fixture proxy port that requires Basic credentials."""
    if _EXTERNAL:
        return os.environ["PROXY_AUTH_HOST_PORT"]
    host = squid_container.get_container_host_ip()
    port = squid_container.get_exposed_port(3129)
    return f"{host}:{port}"


@pytest.fixture(scope="session")
def ca_cert_path():
    return os.path.join(os.getcwd(), "test", "fixtures", "certs", "ca.pem")


@pytest.fixture
def system_root_pem():
    """The first certificate of the system trust bundle, as PEM text, for a CA file
    that must hold a certificate unrelated to the chasm server."""
    import re
    import ssl

    cafile = ssl.get_default_verify_paths().cafile
    if cafile is None:
        pytest.skip("no system trust bundle file on this platform")
    with open(cafile) as handle:
        block = re.search(
            r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----",
            handle.read(),
            re.S,
        )
    assert block is not None, "the system trust bundle holds no certificate"
    return block.group(0) + "\n"


class RawConnection:
    """One accepted connection of a :class:`RawServer`, handed to the script.

    The script writes the response by hand, so a test can stall a body, cut it
    short or reset the connection, which neither the shared mock server nor a
    plain HTTP server can do.
    """

    def __init__(self, server, sock, hit, request_head):
        self.server = server
        self.sock = sock
        self.hit = hit
        self.request_head = request_head

    def upload(self, on_first=None):
        """Read the request body that follows the head and return ``(data, chunked)``.

        A chunked body is read chunk by chunk and one with a Content-Length to its end.
        ``on_first`` runs once the first bytes have arrived, while the rest is still to come.
        """
        lower = self.request_head.lower()
        reader = self.sock.makefile("rb")
        received = b""
        if "transfer-encoding: chunked" in lower:
            while True:
                size = reader.readline().strip()
                if not size:
                    return received, True
                length = int(size, 16)
                if length == 0:
                    reader.readline()
                    return received, True
                received += reader.read(length)
                reader.readline()
                if on_first is not None:
                    on_first()
        for line in lower.split("\r\n"):
            if line.startswith("content-length:"):
                received = reader.read(int(line.split(":", 1)[1]))
        if on_first is not None:
            on_first()
        return received, False

    def head(self, status, *headers):
        lines = [f"HTTP/1.1 {status} Test", *headers, "Connection: close", "", ""]
        self.sock.sendall("\r\n".join(lines).encode("latin-1"))

    def chunk(self, data):
        if isinstance(data, str):
            data = data.encode()
        self.sock.sendall(f"{len(data):x}\r\n".encode() + data + b"\r\n")

    def end(self):
        self.sock.sendall(b"0\r\n\r\n")

    def send(self, data):
        self.sock.sendall(data.encode() if isinstance(data, str) else data)

    def reset(self):
        """Make the close of the socket reset the connection instead of ending it cleanly."""
        self.sock.setsockopt(
            socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0)
        )

    def hold(self):
        """Wait until the test releases the server; False when that took too long."""
        return self.server.release.wait(20)

    def stream_forever(self, data=b"x" * 4096, pause=0.005):
        """Send chunks until the client drops the connection, which sets ``client_gone``."""
        try:
            while True:
                self.chunk(data)
                time.sleep(pause)
        except OSError:
            self.server.client_gone.set()


class RawServer:
    """An in-process HTTP/1.1 server on a raw socket that runs ``script(connection)``
    for every connection it accepts."""

    def __init__(self, script):
        self._script = script
        self._listener = socket.socket()
        self._listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._listener.bind(("127.0.0.1", 0))
        self._listener.listen(50)
        self._lock = threading.Lock()
        self._sockets = []
        self.hits = 0
        self.heads = []
        self.release = threading.Event()
        self.client_gone = threading.Event()
        threading.Thread(target=self._accept_loop, daemon=True).start()

    @property
    def port(self):
        return self._listener.getsockname()[1]

    @property
    def base_url(self):
        return f"http://127.0.0.1:{self.port}"

    def url(self, path):
        return self.base_url + path

    def _accept_loop(self):
        while True:
            try:
                sock, _ = self._listener.accept()
            except OSError:
                return
            with self._lock:
                self._sockets.append(sock)
            threading.Thread(target=self._handle, args=(sock,), daemon=True).start()

    def _handle(self, sock):
        try:
            head = b""
            while not head.endswith(b"\r\n\r\n"):
                data = sock.recv(1)
                if not data:
                    return
                head += data
            text = head.decode("latin-1")
            with self._lock:
                self.hits += 1
                hit = self.hits
                self.heads.append(text)
            self._script(RawConnection(self, sock, hit, text))
        except OSError:
            pass
        finally:
            sock.close()

    def close(self):
        self.release.set()
        self._listener.close()
        with self._lock:
            sockets = list(self._sockets)
        for sock in sockets:
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


@pytest.fixture
def raw_server():
    """Starts a :class:`RawServer` for a script and closes every one at teardown."""
    servers = []

    def start(script):
        server = RawServer(script)
        servers.append(server)
        return server

    yield start
    for server in servers:
        server.close()
