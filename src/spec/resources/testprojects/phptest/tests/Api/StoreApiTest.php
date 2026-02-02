<?php

namespace PetstoreClient\Test\Api;

use GuzzleHttp\Client;
use PHPUnit\Framework\TestCase;
use PetstoreClient\Api\StoreApi;
use PetstoreClient\Configuration;
use PetstoreClient\Model\Order;

class StoreApiTest extends TestCase
{
    private StoreApi $api;

    protected function setUp(): void
    {
        $config = Configuration::getDefaultConfiguration()
            ->setHost(getenv('API_BASE_URL') ?: 'http://localhost:4010');
        $this->api = new StoreApi(new Client(), $config);
    }

    public function testGetInventory(): void
    {
        $result = $this->api->getInventory();

        $this->assertIsArray($result);
    }

    public function testPlaceOrder(): void
    {
        $order = new Order([
            'id' => 1,
            'petId' => 12345,
            'quantity' => 1,
            'shipDate' => new \DateTime(),
            'status' => 'placed',
            'complete' => false
        ]);

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
        // Delete operation - Prism will return success
        $this->api->deleteOrder(1);

        // If we get here without exception, the call succeeded
        $this->assertTrue(true);
    }
}
