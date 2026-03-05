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
     * @param ApiClient|null     $apiClient API client instance
     * @param Configuration|null $config    Configuration instance
     */
    public function __construct(
        ?ApiClient $apiClient = null,
        ?Configuration $config = null
    ) {
        parent::__construct($apiClient, $config);
    }

    /**
     * Delete purchase order by ID
     * @param  int $orderId ID of the order to delete (required)
     * @return void
     * @throws ApiException
     */
    public function deleteOrder($orderId)
    {
        if ($orderId === null) {
            throw new \InvalidArgumentException(
                'Missing the required parameter $orderId when calling deleteOrder'
            );
        }
        $path = '/store/order/{orderId}';
        $path = str_replace(
            '{' . 'orderId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($orderId)),
            $path
        );
        $queryParams = [];
        $headerParams = [];
        $body = null;

        return $this->invokeApi(
            'DELETE',
            $path,
            $queryParams,
            $headerParams,
            $body,
            [],
            'application/json',
            null
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
        $body = null;

        return $this->invokeApi(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $body,
            ['application/json'],
            'application/json',
            'array<string,int>'
        );
    }

    /**
     * Find purchase order by ID
     * @param  int $orderId ID of order to return (required)
     * @return \PetstoreClient\Models\Order
     * @throws ApiException
     */
    public function getOrderById($orderId)
    {
        if ($orderId === null) {
            throw new \InvalidArgumentException(
                'Missing the required parameter $orderId when calling getOrderById'
            );
        }
        $path = '/store/order/{orderId}';
        $path = str_replace(
            '{' . 'orderId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($orderId)),
            $path
        );
        $queryParams = [];
        $headerParams = [];
        $body = null;

        return $this->invokeApi(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $body,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Order'
        );
    }

    /**
     * Place an order for a pet
     * @param  \PetstoreClient\Models\Order|null $order (optional)
     * @return \PetstoreClient\Models\Order
     * @throws ApiException
     */
    public function placeOrder($order = null)
    {
        $path = '/store/order';
        $queryParams = [];
        $headerParams = [];
        $body = $order;

        return $this->invokeApi(
            'POST',
            $path,
            $queryParams,
            $headerParams,
            $body,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Order'
        );
    }
}
