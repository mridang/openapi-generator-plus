<?php

namespace PetstoreClient\Api;

use PetstoreClient\ApiClient;
use PetstoreClient\ApiException;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;
use PetstoreClient\ObjectSerializer;

/**
 * StoreApi provides methods for the Store API group.
 */
class StoreApi extends BaseApi
{
    /**
     * Delete purchase order by ID
     * @param int $orderId ID of the order to delete
     * @throws ApiException
     */
    public function deleteOrder($orderId): void
    {
        $path = '/store/order/{orderId}';
        $path = str_replace(
            '{' . 'orderId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($orderId)),
            $path
        );
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        $this->invokeApi(
            'DELETE',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            [],
            'application/json',
            null,
        );
    }

    /**
     * Returns pet inventories by status
     * @return array<string,int>
     * @throws ApiException
     */
    public function getInventory()
    {
        $path = '/store/inventory';
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var array<string,int> $result */
        $result = $this->invokeApi(
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
     * @return \PetstoreClient\Models\Order
     * @throws ApiException
     */
    public function getOrderById($orderId)
    {
        $path = '/store/order/{orderId}';
        $path = str_replace(
            '{' . 'orderId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($orderId)),
            $path
        );
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var \PetstoreClient\Models\Order $result */
        $result = $this->invokeApi(
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
     * @param \PetstoreClient\Models\Order|null $order
     * @return \PetstoreClient\Models\Order
     * @throws ApiException
     */
    public function placeOrder($order = null)
    {
        $path = '/store/order';
        $queryParams = [];
        $headerParams = [];
        $requestBody = $order;

        /** @var \PetstoreClient\Models\Order $result */
        $result = $this->invokeApi(
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
