import json
from http.server import HTTPServer, BaseHTTPRequestHandler
from threading import Thread

from petstore_client.default_api_client import DefaultApiClient
from petstore_client.transport_options import TransportOptions


class _EchoHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self._respond()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode() if length else ""
        self._respond(body)

    def do_PUT(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode() if length else ""
        self._respond(body)

    def do_DELETE(self):
        self._respond()

    def _respond(self, body=""):
        if self.path == "/not-found":
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"not found")
            return

        # Echo back all received headers as JSON
        received_headers = dict(self.headers)
        data = json.dumps({"method": self.command, "body": body, "headers": received_headers})
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("X-Test-Header", "test-value")
        self.end_headers()
        self.wfile.write(data.encode())

    def log_message(self, format, *args):
        pass


class TestDefaultApiClientUnit:
    @classmethod
    def setup_class(cls):
        cls.server = HTTPServer(("127.0.0.1", 0), _EchoHandler)
        cls.port = cls.server.server_address[1]
        cls.base_url = f"http://127.0.0.1:{cls.port}"
        cls.thread = Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def teardown_class(cls):
        cls.server.shutdown()

    def test_sends_get_request(self):
        client = DefaultApiClient()
        response = client.send_request("GET", f"{self.base_url}/echo", {}, None)
        assert response.status_code == 200
        body = json.loads(response.body)
        assert body["method"] == "GET"

    def test_sends_post_with_json_body(self):
        client = DefaultApiClient()
        headers = {"Content-Type": "application/json"}
        response = client.send_request(
            "POST", f"{self.base_url}/echo", headers, '{"key":"value"}'
        )
        assert response.status_code == 200
        body = json.loads(response.body)
        assert body["method"] == "POST"
        assert "key" in body["body"]

    def test_returns_response_headers(self):
        client = DefaultApiClient()
        response = client.send_request("GET", f"{self.base_url}/echo", {}, None)
        lower_headers = {k.lower(): v for k, v in response.headers.items()}
        assert "x-test-header" in lower_headers
        assert lower_headers["x-test-header"] == "test-value"

    def test_returns_non_2xx_status(self):
        client = DefaultApiClient()
        response = client.send_request("GET", f"{self.base_url}/not-found", {}, None)
        assert response.status_code == 404
        assert response.body == "not found"

    def test_sends_put_request(self):
        client = DefaultApiClient()
        response = client.send_request("PUT", f"{self.base_url}/echo", {}, "update")
        assert response.status_code == 200
        body = json.loads(response.body)
        assert body["method"] == "PUT"

    def test_sends_delete_request(self):
        client = DefaultApiClient()
        response = client.send_request("DELETE", f"{self.base_url}/echo", {}, None)
        assert response.status_code == 200
        body = json.loads(response.body)
        assert body["method"] == "DELETE"

    def test_injects_user_agent_header(self):
        transport = TransportOptions.builder().user_agent("TestAgent/1.0").build()
        client = DefaultApiClient(transport)
        response = client.send_request("GET", f"{self.base_url}/echo", {}, None)
        assert response.status_code == 200
        body = json.loads(response.body)
        assert body["headers"].get("User-Agent") == "TestAgent/1.0"

    def test_does_not_override_caller_user_agent(self):
        transport = TransportOptions.builder().user_agent("TestAgent/1.0").build()
        client = DefaultApiClient(transport)
        response = client.send_request(
            "GET", f"{self.base_url}/echo", {"User-Agent": "CallerAgent/2.0"}, None
        )
        assert response.status_code == 200
        body = json.loads(response.body)
        assert body["headers"].get("User-Agent") == "CallerAgent/2.0"

    def test_injects_request_id_header(self):
        transport = TransportOptions.builder().inject_request_id(True).build()
        client = DefaultApiClient(transport)
        response = client.send_request("GET", f"{self.base_url}/echo", {}, None)
        assert response.status_code == 200
        body = json.loads(response.body)
        assert "X-Request-ID" in body["headers"]
        assert len(body["headers"]["X-Request-ID"]) > 0

    def test_does_not_inject_request_id_when_disabled(self):
        transport = TransportOptions.builder().inject_request_id(False).build()
        client = DefaultApiClient(transport)
        response = client.send_request("GET", f"{self.base_url}/echo", {}, None)
        assert response.status_code == 200
        body = json.loads(response.body)
        assert "X-Request-ID" not in body["headers"]

    def test_transport_default_headers_are_sent(self):
        transport = (
            TransportOptions.builder()
            .default_header("X-Custom-Transport", "transport-value")
            .build()
        )
        client = DefaultApiClient(transport)
        response = client.send_request("GET", f"{self.base_url}/echo", {}, None)
        assert response.status_code == 200
        body = json.loads(response.body)
        assert body["headers"].get("X-Custom-Transport") == "transport-value"

    def test_caller_headers_override_transport_defaults(self):
        transport = (
            TransportOptions.builder()
            .default_header("X-Override", "transport")
            .build()
        )
        client = DefaultApiClient(transport)
        response = client.send_request(
            "GET", f"{self.base_url}/echo", {"X-Override": "caller"}, None
        )
        assert response.status_code == 200
        body = json.loads(response.body)
        assert body["headers"].get("X-Override") == "caller"
