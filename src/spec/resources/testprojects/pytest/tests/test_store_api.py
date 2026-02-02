import pytest
from datetime import datetime, timezone
from petstore_client.api.store_api import StoreApi
from petstore_client.api_client import ApiClient
from petstore_client.configuration import Configuration
from petstore_client.models.order import Order


class TestStoreApi:

    @pytest.fixture(autouse=True)
    def setup(self, api_base_url):
        config = Configuration(host=api_base_url)
        client = ApiClient(config)
        self.api = StoreApi(client)

    def test_get_inventory(self):
        result = self.api.get_inventory()

        assert isinstance(result, dict)

    def test_place_order(self):
        order = Order(
            id=1,
            pet_id=12345,
            quantity=1,
            ship_date=datetime.now(timezone.utc),
            status='placed',
            complete=False
        )

        result = self.api.place_order(order)

        assert result is not None
        assert result.id is not None

    def test_get_order_by_id(self):
        result = self.api.get_order_by_id(1)

        assert result is not None
        assert result.id is not None

    def test_delete_order(self):
        # Delete operation - Prism will return success
        self.api.delete_order(1)

        # If we get here without exception, the call succeeded
        assert True
