from abc import ABC, abstractmethod
from enum import Enum
from typing import Any, Dict, List, Optional, Union
from urllib.parse import quote

from petstore_client.models.order import Order

from ..api_client import ApiClient
from ..api_result import ApiResult
from ..default_api_client import DefaultApiClient
from ..configuration import Configuration
from .base_api import BaseApi
from ..value_serializer import ValueSerializer


class StoreApi(BaseApi):
    """StoreApi provides methods for the store API group.
    Access to Petstore orders
    """

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
        :raises ApiException: if fails to make API call
        """
        if order_id is None:
            raise ValueError("Missing the required parameter 'order_id'")

        result = self.delete_order_with_http_info(order_id)
        return result.data

    def delete_order_with_http_info(
        self,
        order_id: int,
    ) -> 'ApiResult[None]':
        """Delete purchase order by ID (with HTTP info)
        :param order_id: ID of the order to delete (required)
        :return: ApiResult containing the response data, status code, raw body, and headers
        :raises ApiException: if fails to make API call
        """
        if order_id is None:
            raise ValueError("Missing the required parameter 'order_id'")

        path = '/store/order/{orderId}'
        path = path.replace(
            '{' + 'orderId' + '}',
            str(ValueSerializer.serialize_styled('orderId', order_id, 'path', 'int', None, 'simple', False)),
        )
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api_for_result(
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
        :raises ApiException: if fails to make API call
        """
        result = self.get_inventory_with_http_info()
        assert result.data is not None
        return result.data

    def get_inventory_with_http_info(
        self,
    ) -> 'ApiResult[Dict[str, int]]':
        """Returns pet inventories by status (with HTTP info)
        :return: ApiResult containing the response data, status code, raw body, and headers
        :raises ApiException: if fails to make API call
        """
        path = '/store/inventory'
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api_for_result(
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
        :raises ApiException: if fails to make API call
        """
        if order_id is None:
            raise ValueError("Missing the required parameter 'order_id'")

        result = self.get_order_by_id_with_http_info(order_id)
        assert result.data is not None
        return result.data

    def get_order_by_id_with_http_info(
        self,
        order_id: int,
    ) -> 'ApiResult[Order]':
        """Find purchase order by ID (with HTTP info)
        :param order_id: ID of order to return (required)
        :return: ApiResult containing the response data, status code, raw body, and headers
        :raises ApiException: if fails to make API call
        """
        if order_id is None:
            raise ValueError("Missing the required parameter 'order_id'")

        path = '/store/order/{orderId}'
        path = path.replace(
            '{' + 'orderId' + '}',
            str(ValueSerializer.serialize_styled('orderId', order_id, 'path', 'int', None, 'simple', False)),
        )
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api_for_result(
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
        :raises ApiException: if fails to make API call
        """
        result = self.place_order_with_http_info(order)
        assert result.data is not None
        return result.data

    def place_order_with_http_info(
        self,
        order: Optional[Order] = None,
    ) -> 'ApiResult[Order]':
        """Place an order for a pet (with HTTP info)
        :param order:  (optional)
        :return: ApiResult containing the response data, status code, raw body, and headers
        :raises ApiException: if fails to make API call
        """
        path = '/store/order'
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = order

        return self._invoke_api_for_result(
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
