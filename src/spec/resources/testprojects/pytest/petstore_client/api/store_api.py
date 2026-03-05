from typing import Any, Dict, List, Optional
from urllib.parse import quote

from pydantic import Field, StrictInt
from typing import Dict, Optional
from typing_extensions import Annotated
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
        order_id: Annotated[StrictInt, Field(description='ID of the order to delete')],
    ) -> None:
        """Delete purchase order by ID
        :param order_id: ID of the order to delete (required)
        """
        if order_id is None:
            raise ValueError("Missing the required parameter 'order_id' when calling delete_order")
        path = '/store/order/{orderId}'
        path = path.replace('{' + 'orderId' + '}', quote(ObjectSerializer.to_path_value(order_id), safe=''))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self.invoke_api(
            'DELETE',
            path,
            query_params,
            header_params,
            body,
            [],
            'application/json',
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

        return self.invoke_api(
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Dict[str, int]',
        )

    def get_order_by_id(
        self,
        order_id: Annotated[StrictInt, Field(description='ID of order to return')],
    ) -> Order:
        """Find purchase order by ID
        :param order_id: ID of order to return (required)
        :return: Order
        """
        if order_id is None:
            raise ValueError("Missing the required parameter 'order_id' when calling get_order_by_id")
        path = '/store/order/{orderId}'
        path = path.replace('{' + 'orderId' + '}', quote(ObjectSerializer.to_path_value(order_id), safe=''))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self.invoke_api(
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Order',
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

        return self.invoke_api(
            'POST',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Order',
        )
