<?php

namespace PetstoreClient\Test\Api;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Api\StoreApi;
use PetstoreClient\Configuration;
use PetstoreClient\Models\Order;

/**
 * Integration tests for the Store API endpoints.
 */
class StoreApiTest extends TestCase
{
    private StoreApi $api;

    protected function setUp(): void
    {
        $config = Configuration::getDefaultConfiguration()
            ->setBaseUrl(getenv('API_BASE_URL') ?: 'http://localhost:4010');
        $this->api = new StoreApi(config: $config);
    }

    public function testGetInventory(): void
    {
        $result = $this->api->getInventory();

        $this->assertIsArray($result);
    }

    public function testPlaceOrder(): void
    {
        $order = new Order();
        $order->setId(1);
        $order->setPetId(12345);
        $order->setQuantity(1);
        $order->setShipDate(new \DateTime());
        $order->setStatus('placed');
        $order->setComplete(false);

        $result = $this->api->placeOrder($order);

        $this->assertInstanceOf(Order::class, $result);
        $this->assertNotNull($result->getId());
    }

    public function testGetOrderById(): void
    {
        $result = $this->api->getOrderById(1);

        $this->assertInstanceOf(Order::class, $result);
        $this->assertNotNull($result->getId());
    }

    public function testDeleteOrder(): void
    {
        $this->api->deleteOrder(1);

        $this->assertTrue(true);
    }
}
