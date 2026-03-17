from typing import Any, Dict, List, Optional, Union
from urllib.parse import quote

from petstore_client.models.order import Order

from ..api_client import ApiClient
from ..default_api_client import DefaultApiClient
from ..configuration import Configuration
from .base_api import BaseApi
from ..object_serializer import ObjectSerializer


class StoreApi(BaseApi):
    """StoreApi provides methods for the store API group."""

    def __init__(
        self,
        api_client: Optional[ApiClient] = None,
        config: Optional[Configuration] = None,
    ):
        super().__init__(api_client, config)

    def delete_order(
        self,
        order_id: int,
    ) -> None:
        """Delete purchase order by ID
        :param order_id: ID of the order to delete (required)
        """
        if order_id is None:
            raise ValueError("Missing the required parameter 'order_id'")

        path = '/store/order/{orderId}'
        path = path.replace('{' + 'orderId' + '}', quote(ObjectSerializer.to_path_value(order_id), safe=''))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
            'DELETE',
            path,
            query_params,
            header_params,
            body,
            [],
            'application/json',
            None,
            None,
        )

    def get_inventory(
        self,
    ) -> Dict[str, int]:
        """Returns pet inventories by status
        :return: Dict[str, int]
        """
        path = '/store/inventory'
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Dict[str, int]',
            None,
        )

    def get_order_by_id(
        self,
        order_id: int,
    ) -> Order:
        """Find purchase order by ID
        :param order_id: ID of order to return (required)
        :return: Order
        """
        if order_id is None:
            raise ValueError("Missing the required parameter 'order_id'")

        path = '/store/order/{orderId}'
        path = path.replace('{' + 'orderId' + '}', quote(ObjectSerializer.to_path_value(order_id), safe=''))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Order',
            None,
        )

    def place_order(
        self,
        order: Optional[Order] = None,
    ) -> Order:
        """Place an order for a pet
        :param order:  (optional)
        :return: Order
        """
        path = '/store/order'
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = order

        return self._invoke_api(  # type: ignore[no-any-return]
            'POST',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Order',
            None,
        )
