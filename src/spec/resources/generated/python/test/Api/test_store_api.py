"""Integration tests for the Store API endpoints."""

import pytest
import threading
from datetime import datetime, timezone
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Any
from petstore_client.api.store_api import StoreApi
from petstore_client.configuration import Configuration
from petstore_client.models.order import Order, OrderStatusEnum


class TestStoreApi:
    """Test suite for StoreApi operations."""

    @pytest.fixture(autouse=True)
    def setup(self, api_base_url: Any) -> None:
        config = (
            Configuration.builder().base_url(api_base_url).default_header('Authorization', 'Bearer test-token').build()
        )
        self.api = StoreApi(config=config)

    async def test_get_inventory(self) -> None:
        result = await self.api.get_inventory()

        assert isinstance(result, dict)

    async def test_place_order(self) -> None:
        order = Order(
            id=1,
            petId=12345,
            quantity=1,
            shipDate=datetime.now(timezone.utc),
            status=OrderStatusEnum.PLACED,
            complete=False,
        )

        result = await self.api.place_order(order)

        assert result is not None
        assert result.id is not None

    async def test_get_order_by_id(self) -> None:
        result = await self.api.get_order_by_id(1)

        assert result is not None
        assert result.id is not None

    async def test_delete_order(self) -> None:
        await self.api.delete_order(1)

        assert True


def _create_mock_server(status: int, content_type: str, body: str) -> tuple[StoreApi, HTTPServer]:
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.end_headers()
            self.wfile.write(body.encode('utf-8'))

        def do_POST(self) -> None:
            self.do_GET()

        def do_DELETE(self) -> None:
            self.do_GET()

        def log_message(self, format: str, *args: object) -> None:  # noqa: A002
            pass

    server = HTTPServer(('127.0.0.1', 0), Handler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.handle_request)
    thread.daemon = True
    thread.start()

    config = Configuration.builder().base_url(f'http://127.0.0.1:{port}').build()
    api = StoreApi(config=config)
    return api, server


class TestStoreApiErrorHandling:
    """Test suite for StoreApi error handling."""

    async def test_get_order_not_found(self) -> None:
        api, server = _create_mock_server(404, 'application/json', '{"message":"Order not found"}')
        with pytest.raises(Exception):
            await api.get_order_by_id(99999)

    async def test_place_order_server_error(self) -> None:
        api, server = _create_mock_server(500, 'application/json', '{"message":"Internal server error"}')
        order = Order(id=1, petId=12345, quantity=1, status=OrderStatusEnum.PLACED, complete=False)
        with pytest.raises(Exception):
            await api.place_order(order)

    async def test_delete_order_not_found(self) -> None:
        api, server = _create_mock_server(404, 'application/json', '{"message":"Order not found"}')
        with pytest.raises(Exception):
            await api.delete_order(99999)
