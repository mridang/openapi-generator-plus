<?php

declare(strict_types=1);

/* phpcs:disable Generic.Files.LineLength.TooLong */
/* phpcs:disable PSR1.Classes.ClassDeclaration.MultipleClasses */

namespace PetstoreClient\Api;

use PetstoreClient\ApiException;
use PetstoreClient\ApiResult;
use PetstoreClient\Models\Order;
use PetstoreClient\ValueSerializer;

/**
 * StoreApi provides methods for the Store API group.
 * Access to Petstore orders
 */

class StoreApi extends BaseApi
{
    /**
     * Delete purchase order by ID
     * @param int $orderId ID of the order to delete
     * @throws ApiException
     */
    public function deleteOrder(int $orderId): void
    {
        $this->deleteOrderWithHttpInfo($orderId);
    }

    /**
     * @param int $orderId ID of the order to delete
     * @return ApiResult<null>
     * @throws ApiException
     */
    public function deleteOrderWithHttpInfo(int $orderId): ApiResult
    {
        $path = '/store/order/{orderId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('orderId', $orderId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'orderId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<null> $result */
        $result = $this->invokeApiForResult(
            'DELETE',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            [],
            'application/json',
            null,
        );
        return $result;
    }

    /**
     * Returns pet inventories by status
     * @return array<string,int>
     * @throws ApiException
     */
    public function getInventory()
    {
        /** @var array<string,int> $result */
        $result = $this->getInventoryWithHttpInfo()->data;
        return $result;
    }

    /**
     * @return ApiResult<array<string,int>>
     * @throws ApiException
     */
    public function getInventoryWithHttpInfo(): ApiResult
    {
        $path = '/store/inventory';
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<array<string,int>> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            'array<string,int>',
        );
        return $result;
    }

    /**
     * Find purchase order by ID
     * @param int $orderId ID of order to return
     * @return Order
     * @throws ApiException
     */
    public function getOrderById(int $orderId)
    {
        /** @var Order $result */
        $result = $this->getOrderByIdWithHttpInfo($orderId)->data;
        return $result;
    }

    /**
     * @param int $orderId ID of order to return
     * @return ApiResult<Order>
     * @throws ApiException
     */
    public function getOrderByIdWithHttpInfo(int $orderId): ApiResult
    {
        $path = '/store/order/{orderId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('orderId', $orderId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'orderId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<Order> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Order',
        );
        return $result;
    }

    /**
     * Place an order for a pet
     * @return Order
     * @throws ApiException
     */
    public function placeOrder(Order|null $order = null)
    {
        /** @var Order $result */
        $result = $this->placeOrderWithHttpInfo($order)->data;
        return $result;
    }

    /**
     * @return ApiResult<Order>
     * @throws ApiException
     */
    public function placeOrderWithHttpInfo(Order|null $order = null): ApiResult
    {
        $path = '/store/order';
        $queryParams = [];
        $headerParams = [];
        $requestBody = $order;

        /** @var ApiResult<Order> $result */
        $result = $this->invokeApiForResult(
            'POST',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Order',
        );
        return $result;
    }
}
