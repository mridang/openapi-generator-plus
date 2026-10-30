<?php

/**
 * Integration tests for the Store API endpoints.
 */

declare(strict_types=1);

namespace PetstoreClient\Test\Api;

use PetstoreClient\Api\StoreApi;
use PetstoreClient\ApiResult;
use PetstoreClient\Configuration;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Models\Category;
use PetstoreClient\Models\Order;
use PetstoreClient\Models\OrderStatusEnum;

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

test('data methods declare a native return type', function (string $method, string $expected): void {
    $returnType = (new \ReflectionMethod(StoreApi::class, $method))->getReturnType();
    expect($returnType)->toBeInstanceOf(\ReflectionNamedType::class);
    assert($returnType instanceof \ReflectionNamedType);
    expect($returnType->getName())->toBe($expected);
})->with([
    ['getOrderById', Order::class],
    ['getInventory', \Ds\Map::class],
    ['deleteOrder', 'void'],
]);

// -- Integration tests via Chasm --

test('get inventory', function (): void {
    $result = $this->api->getInventory();

    // Phase 2 PHP type-surface: OAS `map` now maps to `\Ds\Map` (was
    // `array<string,int>`). The inventory endpoint declares
    // `additionalProperties: { type: integer }`, so the typed return
    // is a Ds\Map keyed by status string.
    expect($result)->toBeInstanceOf(\Ds\Map::class);
});

test('get inventory with http info', function (): void {
    $result = $this->api->getInventoryWithHttpInfo();

    expect($result)->toBeInstanceOf(ApiResult::class);
    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
    expect($result->data)->not->toBeNull();
});

test('get order by id', function (): void {
    $result = $this->api->getOrderById(1);

    expect($result)->toBeInstanceOf(Order::class);
    expect($result->id)->not->toBeNull();
});

test('get order by id with http info', function (): void {
    $result = $this->api->getOrderByIdWithHttpInfo(1);

    expect($result)->toBeInstanceOf(ApiResult::class);
    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
    expect($result->data)->toBeInstanceOf(Order::class);
});

test('place order with http info', function (): void {
    $order = new Order();
    $order->id = 1;
    $order->petId = 12345;
    $order->quantity = 1;
    $order->shipDate = new \DateTime();
    $order->status = OrderStatusEnum::PLACED;
    $order->complete = false;

    $result = $this->api->placeOrderWithHttpInfo($order);

    expect($result)->toBeInstanceOf(ApiResult::class);
    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
    expect($result->data)->toBeInstanceOf(Order::class);
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

// -- nested-container deserialization --
//
// getGroupedCategories returns a nested generic container: an array of map of
// Category (\Ds\Vector<\Ds\Map<Category>>). The recursive deserializer must
// descend every container level so the innermost leaves are decoded into TYPED
// Category instances, not left as raw maps. Driven against a mock server that
// returns the canonical nested body.

test('get grouped categories deserializes nested leaves to typed models', function (): void {
    $body = '[{"a":{"id":1,"name":"Dogs"}},{"b":{"id":2,"name":"Cats"}}]';
    $api = newStoreApiForMock(200, 'application/json', $body);

    $result = $api->getGroupedCategories();

    expect($result)->toBeInstanceOf(\Ds\Vector::class);
    expect($result->count())->toBe(2);

    $first = $result->get(0);
    expect($first)->toBeInstanceOf(\Ds\Map::class);
    $dogs = $first->get('a');
    expect($dogs)->toBeInstanceOf(Category::class);
    expect($dogs->id)->toBe(1);
    expect($dogs->name)->toBe('Dogs');

    $second = $result->get(1);
    expect($second)->toBeInstanceOf(\Ds\Map::class);
    $cats = $second->get('b');
    expect($cats)->toBeInstanceOf(Category::class);
    expect($cats->id)->toBe(2);
    expect($cats->name)->toBe('Cats');
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

/**
 * Builds a StoreApi backed by a fake client that records the request URL and
 * headers and answers with a canned category.
 *
 * @return array{0: StoreApi, 1: \stdClass}
 */
function newCapturingStoreApi(): array
{
    $captured = new \stdClass();
    $captured->url = '';
    /** @var array<string, string> $hdrs */
    $hdrs = [];
    $captured->headers = $hdrs;

    $client = new readonly class ($captured) implements \PetstoreClient\ApiClient {
        public function __construct(private \stdClass $captured)
        {
        }

        public function close(): void
        {
        }

        public function sendRequest(string $method, string $url, array $headers, mixed $body, bool $noRedirect = false): \PetstoreClient\ApiHttpResponse
        {
            $this->captured->url = $url;
            $this->captured->headers = $headers;
            return new \PetstoreClient\ApiHttpResponse(
                200,
                '{"id":1,"name":"sample"}',
                ['Content-Type' => 'application/json']
            );
        }
    };

    $config = Configuration::builder()
        ->baseUrl('http://localhost:9999')
        ->build();

    return [new StoreApi(apiClient: $client, config: $config), $captured];
}

test('content json query param is the object serializer output', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    try {
        $api->searchStore(new \PetstoreClient\Api\Options\SearchStoreOptions(
            q: new \PetstoreClient\Models\PhotoMetadata(caption: "a/b<\u{00E9}"),
        ));
    } catch (\Throwable) {
        /* The canned reply is not the operation's model; only the URL matters. */
    }

    expect($captured->url)->toContain('q=%7B%22caption%22%3A%22a%2Fb%3C%C3%A9%22%7D');
});

test('content json query param that cannot be serialized sends no request', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    expect(fn (): mixed => $api->searchStore(new \PetstoreClient\Api\Options\SearchStoreOptions(
        q: new \PetstoreClient\Models\PhotoMetadata(
            location: new \PetstoreClient\Models\PhotoMetadataLocation(lat: NAN),
        ),
    )))->toThrow(\PetstoreClient\Errors\SerializationException::class);
    expect($captured->url)->toBe('');
});

test('named enum path, query and header params travel as their wire values', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    $api->getBySwatch(
        \PetstoreClient\Models\Swatch::RED,
        new \PetstoreClient\Api\Options\GetBySwatchOptions(
            querySwatch: \PetstoreClient\Models\Swatch::GREEN,
            preferredSwatch: \PetstoreClient\Models\Swatch::BLUE,
        )
    );

    expect($captured->url)->toBe('http://localhost:9999/store/by-swatch/red?querySwatch=green');
    expect($captured->headers['Preferred-Swatch'] ?? null)->toBe('blue');
});

test('named enum query and header params are omitted when absent', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    $api->getBySwatch(\PetstoreClient\Models\Swatch::BLUE);

    expect($captured->url)->toBe('http://localhost:9999/store/by-swatch/blue');
    expect($captured->headers)->not->toHaveKey('Preferred-Swatch');
});
