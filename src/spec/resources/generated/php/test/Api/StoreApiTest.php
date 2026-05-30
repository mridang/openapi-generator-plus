<?php

declare(strict_types=1);

namespace PetstoreClient\Test\Api;

use PetstoreClient\Api\StoreApi;
use PetstoreClient\Configuration;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Models\Order;
use PetstoreClient\Models\OrderStatusEnum;

/**
 * Integration tests for the Store API endpoints.
 */

beforeEach(function (): void {
    $config = Configuration::builder()
        ->baseUrl(getenv('API_BASE_URL') ?: 'http://localhost:4010')
        ->defaultHeader('Authorization', 'Bearer test-token')
        ->build();
    $this->api = new StoreApi(config: $config);
});

function newStoreApiForMock(int $statusCode, string $contentType, string $body): StoreApi
{
    $client = new StoreMockApiClient($statusCode, $body, $contentType);
    $config = Configuration::builder()
        ->baseUrl('http://localhost:9999')
        ->build();
    return new StoreApi(apiClient: $client, config: $config);
}

// -- Integration tests via Chasm --

test('get inventory', function (): void {
    $result = $this->api->getInventory();

    expect($result)->toBeArray();
});

test('get order by id', function (): void {
    $result = $this->api->getOrderById(1);

    expect($result)->toBeInstanceOf(Order::class);
    expect($result->id)->not->toBeNull();
});

test('place order', function (): void {
    $order = new Order();
    $order->id = 1;
    $order->petId = 12345;
    $order->quantity = 1;
    $order->shipDate = new \DateTime();
    $order->status = OrderStatusEnum::PLACED;
    $order->complete = false;

    $result = $this->api->placeOrder($order);

    expect($result)->toBeInstanceOf(Order::class);
    expect($result->id)->not->toBeNull();
});

test('delete order', function (): void {
    $this->api->deleteOrder(1);

    expect(true)->toBeTrue();
});

// -- Mock-based error handling tests --

test('get order not found', function (): void {
    $api = newStoreApiForMock(404, 'application/json', '{"message":"Order not found"}');

    expect(fn () => $api->getOrderById(99999))->toThrow(NotFoundException::class);
});

test('place order server error', function (): void {
    $order = new Order();
    $order->id = 1;
    $order->petId = 12345;
    $order->quantity = 1;
    $order->shipDate = new \DateTime();
    $order->status = OrderStatusEnum::PLACED;
    $order->complete = false;

    $api = newStoreApiForMock(500, 'application/json', '{"message":"Internal server error"}');

    expect(fn () => $api->placeOrder($order))->toThrow(ServerException::class);
});

test('delete order not found', function (): void {
    $api = newStoreApiForMock(404, 'application/json', '{"message":"Order not found"}');

    expect(fn () => $api->deleteOrder(99999))->toThrow(NotFoundException::class);
});
