<?php

/**
 * Integration tests for the Store API endpoints.
 */

declare(strict_types=1);

namespace PetstoreClient\Test\Api;

use PetstoreClient\Api\Options\GetInventoryOptions;
use PetstoreClient\Api\StoreApi;
use PetstoreClient\ApiResult;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;
use PetstoreClient\TransportOptions;
use PetstoreClient\Errors\BadRequestException;
use PetstoreClient\Errors\ClientException;
use PetstoreClient\Errors\ConflictException;
use PetstoreClient\Errors\ForbiddenException;
use PetstoreClient\Errors\UnauthorizedException;
use PetstoreClient\Errors\UnprocessableEntityException;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Models\Category;
use PetstoreClient\Models\Order;
use PetstoreClient\Models\OrderStatusEnum;
use PetstoreClient\Models\Reservation;
use PetstoreClient\Models\ReservationReceipt;
use PetstoreClient\Models\ReserveStockResponse;

beforeEach(function (): void {
    $config = Configuration::builder()
        ->baseUrl(getenv('API_BASE_URL') ?: 'http://localhost:4010')
        ->defaultHeader('Authorization', 'Bearer test-token')
        ->build();
    $this->api = new StoreApi(config: $config);
});

function newStoreApiForMock(int $statusCode, string $requestContentType, string $body): StoreApi
{
    $client = new StoreMockApiClient($statusCode, $body, $requestContentType);
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

/**
 * Builds a StoreApi backed by a capturing fake client plus the captured header bag, so a test can
 * assert what reached the wire for an operation that has no parameters and no security.
 *
 * @return array{0: StoreApi, 1: \stdClass}
 */
function newHeaderCapturingStoreApi(): array
{
    $captured = new \stdClass();
    /** @var array<string, string> $headers */
    $headers = [];
    $captured->headers = $headers;
    $client = new readonly class ($captured) implements \PetstoreClient\ApiClient {
        public function __construct(private \stdClass $captured)
        {
        }

        public function close(): void
        {
        }

        public function sendRequest(string $method, string $url, array $headers, mixed $body, bool $noRedirect = false, bool $noDefaultHeaders = false): \PetstoreClient\ApiHttpResponse
        {
            $this->captured->headers = $headers;
            return new \PetstoreClient\ApiHttpResponse(200, '{}', ['Content-Type' => 'application/json']);
        }
    };
    $config = Configuration::builder()->baseUrl('http://localhost:9999')->build();

    return [new StoreApi(apiClient: $client, config: $config), $captured];
}

test('bhv operations 096 an operation without parameters or security takes per call headers', function (): void {
    [$api, $captured] = newHeaderCapturingStoreApi();

    $api->getInventory(new GetInventoryOptions(headers: ['X-Tenant' => 't1']));

    expect($captured->headers['X-Tenant'] ?? null)->toBe('t1');
});

test('an operation without parameters or security still runs with no options', function (): void {
    [$api, $captured] = newHeaderCapturingStoreApi();

    $api->getInventory();

    expect($captured->headers)->not->toHaveKey('X-Tenant');
});

test('the options of an operation without parameters hold no auth field', function (): void {
    $options = new \ReflectionClass(GetInventoryOptions::class);

    expect($options->hasProperty('auth'))->toBeFalse();
    expect($options->hasProperty('timeout'))->toBeTrue();
    expect($options->hasProperty('headers'))->toBeTrue();
    expect($options->hasProperty('maxRetries'))->toBeTrue();
});

test('a per call timeout and retry count of an operation without parameters must be valid', function (): void {
    expect(fn () => new GetInventoryOptions(timeout: 0))
        ->toThrow(\InvalidArgumentException::class, 'Invalid timeout: must be a positive number of milliseconds (got 0)');
    expect(fn () => new GetInventoryOptions(maxRetries: -1))
        ->toThrow(\InvalidArgumentException::class, 'Invalid maxRetries: must not be negative (got -1)');
});

// -- Integration tests via Chasm --

test('bhv operations 102 get inventory', function (): void {
    $result = $this->api->getInventory();

    // Phase 2 PHP type-surface: OAS `map` now maps to `\Ds\Map` (was
    // `array<string,int>`). The inventory endpoint declares
    // `additionalProperties: { type: integer }`, so the typed return
    // is a Ds\Map keyed by status string.
    expect($result)->toBeInstanceOf(\Ds\Map::class);
});

test('bhv operations 103 get inventory with http info', function (): void {
    $result = $this->api->getInventoryWithHttpInfo();

    expect($result)->toBeInstanceOf(ApiResult::class);
    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
    expect($result->data)->not->toBeNull();
});

test('bhv operations 104 get order by id', function (): void {
    $result = $this->api->getOrderById(1);

    expect($result)->toBeInstanceOf(Order::class);
    expect($result->id)->not->toBeNull();
});

test('bhv operations 105 get order by id with http info', function (): void {
    $result = $this->api->getOrderByIdWithHttpInfo(1);

    expect($result)->toBeInstanceOf(ApiResult::class);
    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
    expect($result->data)->toBeInstanceOf(Order::class);
});

test('bhv operations 119 place order with http info', function (): void {
    $order = new Order(
        id: 1,
        petId: 12345,
        quantity: 1,
        shipDate: new \DateTime(),
        status: OrderStatusEnum::PLACED,
        complete: false,
    );

    $result = $this->api->placeOrderWithHttpInfo($order);

    expect($result)->toBeInstanceOf(ApiResult::class);
    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
    expect($result->data)->toBeInstanceOf(Order::class);
});

test('bhv operations 117 place order', function (): void {
    $order = new Order(
        id: 1,
        petId: 12345,
        quantity: 1,
        shipDate: new \DateTime(),
        status: OrderStatusEnum::PLACED,
        complete: false,
    );

    $result = $this->api->placeOrder($order);

    expect($result)->toBeInstanceOf(Order::class);
    expect($result->id)->not->toBeNull();
});

test('bhv operations 099 delete order', function (): void {
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

test('bhv operations 101 get grouped categories deserializes nested leaves to typed models', function (): void {
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

test('bhv operations 106 get order not found', function (): void {
    $api = newStoreApiForMock(404, 'application/json', '{"message":"Order not found"}');

    expect(fn () => $api->getOrderById(99999))->toThrow(NotFoundException::class);
});

test('bhv operations 118 place order server error', function (): void {
    $order = new Order(
        id: 1,
        petId: 12345,
        quantity: 1,
        shipDate: new \DateTime(),
        status: OrderStatusEnum::PLACED,
        complete: false,
    );

    $api = newStoreApiForMock(500, 'application/json', '{"message":"Internal server error"}');

    expect(fn () => $api->placeOrder($order))->toThrow(ServerException::class);
});

test('bhv operations 110 get order by id picks the status specific error class', function (int $status, string $expectedClass): void {
    /* The typed mock routes answer getOrderById with each status and a JSON
     * body, so the error is decoded through the real operation. */
    $config = Configuration::builder()
        ->baseUrl((getenv('CHASM_HTTP_URL') ?: '') . '/test/status/' . $status)
        ->build();
    $api = new StoreApi(
        apiClient: new DefaultApiClient(TransportOptions::builder()->maxRetries(0)->build()),
        config: $config
    );

    expect(fn () => $api->getOrderById(1))->toThrow(function (\Exception $e) use ($status, $expectedClass): void {
        expect($e::class)->toBe($expectedClass);
        expect($e->getCode())->toBe($status);
    });
})->with([
    [400, BadRequestException::class],
    [401, UnauthorizedException::class],
    [403, ForbiddenException::class],
    [409, ConflictException::class],
    [418, ClientException::class],
    [422, UnprocessableEntityException::class],
    [502, ServerException::class],
]);

test('bhv operations 120 an operation with several 2xx responses decodes the body as the schema of the actual status', function (
    int $status,
    string $expected
): void {
    /* The body satisfies both schemas, so only the status can pick the variant. */
    $body = '{"reservationId":7,"quantity":2,"receiptCode":"r-1","expiresAt":"2024-01-01T00:00:00.000+00:00"}';
    $api = newStoreApiForMock($status, 'application/json', $body);

    $union = $api->reserveStock();

    expect($union)->toBeInstanceOf(ReserveStockResponse::class);
    assert(class_exists($expected));
    expect($union->getActualInstance())->toBeInstanceOf($expected);
})->with([
    'the 200 body is a Reservation' => [200, Reservation::class],
    'the 201 body is a ReservationReceipt' => [201, ReservationReceipt::class],
]);

test('a multi 2xx result has a typed factory, test and accessor per variant', function (): void {
    $reservation = new Reservation(reservationId: 7, quantity: 2);
    $union = ReserveStockResponse::fromReservation($reservation);

    expect($union->isReservation())->toBeTrue();
    expect($union->isReservationReceipt())->toBeFalse();
    expect($union->asReservation())->toBe($reservation);
    expect($union->asReservationReceipt())->toBeNull();
});

test('bhv operations 100 delete order not found', function (): void {
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
    $captured->body = '';
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

        public function sendRequest(string $method, string $url, array $headers, mixed $body, bool $noRedirect = false, bool $noDefaultHeaders = false): \PetstoreClient\ApiHttpResponse
        {
            $this->captured->url = $url;
            $this->captured->headers = $headers;
            $this->captured->body = is_string($body) ? $body : '';
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

test('bhv operations 097 content json query param is the object serializer output', function (): void {
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

test('bhv operations 121 content json header param is the object serializer output', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    try {
        $api->searchStore(new \PetstoreClient\Api\Options\SearchStoreOptions(
            xFilter: ['k' => 'a/b<'],
        ));
    } catch (\Throwable) {
        /* The canned reply is not the operation's model; only the headers matter. */
    }

    expect($captured->headers['X-Filter'] ?? null)->toBe('{"k":"a/b<"}');
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

test('bhv operations 122 named enum path, query and header params travel as their wire values', function (): void {
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

test('bhv operations 107 named enum query and header params are omitted when absent', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    $api->getBySwatch(\PetstoreClient\Models\Swatch::BLUE);

    expect($captured->url)->toBe('http://localhost:9999/store/by-swatch/blue');
    expect($captured->headers)->not->toHaveKey('Preferred-Swatch');
});

test('bhv operations 124 a uuid path parameter puts the canonical lowercase uuid in the path', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    $api->getOrderByTrackingId(\Symfony\Component\Uid\Uuid::fromString('123E4567-E89B-12D3-A456-426614174000'));

    expect($captured->url)->toBe('http://localhost:9999/store/order/by-tracking/123e4567-e89b-12d3-a456-426614174000');
});

test('bhv operations 111 a malformed uuid is refused before any request is sent', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    expect(fn () => $api->getOrderByTrackingId(\Symfony\Component\Uid\Uuid::fromString('not-a-uuid')))
        ->toThrow(\InvalidArgumentException::class);
    expect($captured->url)->toBe('');
});

test('bhv operations 098 create account sends the writeOnly password as an unauthenticated json body', function (): void {
    [$api, $captured] = newCapturingStoreApi();

    try {
        $api->createAccount(new \PetstoreClient\Models\Account('ada', null, 'hunter2'));
    } catch (\Throwable) {
        // The canned reply has no username, so decoding it may fail; only the request matters.
    }

    expect($captured->url)->toBe('http://localhost:9999/store/account');
    expect($captured->headers['Content-Type'] ?? null)->toBe('application/json');
    expect($captured->headers)->not->toHaveKey('Authorization');
    expect($captured->body)->toContain('"username":"ada"');
    expect($captured->body)->toContain('"password":"hunter2"');
});

// -- response-shape operations: the generated method picks the return type --

test('bhv operations 113 get swatch decodes a bare enum', function (): void {
    $api = newStoreApiForMock(200, 'application/json', '"red"');

    expect($api->getSwatch())->toBe(\PetstoreClient\Models\Swatch::RED);
});

test('bhv operations 114 get swatch groups decodes a list of string keyed enum maps', function (): void {
    $api = newStoreApiForMock(200, 'application/json', '[{"a":"red","b":"blue"},{"c":"green"}]');

    $groups = $api->getSwatchGroups();

    expect($groups)->toHaveCount(2);
    $first = $groups->get(0);
    $second = $groups->get(1);
    expect($first)->toBeInstanceOf(\Ds\Map::class);
    expect($second)->toBeInstanceOf(\Ds\Map::class);
    expect($first->get('a'))->toBe(\PetstoreClient\Models\Swatch::RED);
    expect($first->get('b'))->toBe(\PetstoreClient\Models\Swatch::BLUE);
    expect($second->get('c'))->toBe(\PetstoreClient\Models\Swatch::GREEN);
});

test('bhv operations 115 get timestamp groups decodes a list of string keyed date-time maps', function (): void {
    $api = newStoreApiForMock(200, 'application/json', '[{"at":"2024-01-15T10:00:00Z"}]');

    $groups = $api->getTimestampGroups();

    expect($groups)->toHaveCount(1);
    $group = $groups->get(0);
    expect($group)->toBeInstanceOf(\Ds\Map::class);
    $at = $group->get('at');
    expect($at)->toBeInstanceOf(\DateTime::class);
    expect($at->getTimestamp())->toBe(1705312800);
});

test('bhv operations 108 get department decodes the mutually recursive pair', function (): void {
    $api = newStoreApiForMock(
        200,
        'application/json',
        '{"name":"Research","lead":{"name":"Ada","department":{"name":"Inner"}}}'
    );

    $department = $api->getDepartment();

    expect($department->name)->toBe('Research');
    expect($department->lead)->toBeInstanceOf(\PetstoreClient\Models\Employee::class);
    expect($department->lead?->name)->toBe('Ada');
    expect($department->lead?->department)->toBeInstanceOf(\PetstoreClient\Models\Department::class);
    expect($department->lead?->department?->name)->toBe('Inner');
});

test('bhv operations 116 get tree decodes the self recursive node', function (): void {
    $api = newStoreApiForMock(200, 'application/json', '{"value":"root","child":{"value":"leaf"}}');

    $tree = $api->getTree();

    expect($tree->value)->toBe('root');
    expect($tree->child?->value)->toBe('leaf');
    expect($tree->child?->child)->toBeNull();
});

test('bhv operations 123 get defaults keeps the schema defaults for absent fields', function (): void {
    $api = newStoreApiForMock(200, 'application/json', '{"retries":9}');

    $defaults = $api->getDefaults();

    expect($defaults->retries)->toBe(9);
    expect($defaults->ratio)->toBe(1.5);
    expect($defaults->ceiling)->toBe(9000000000);
    expect($defaults->label)->toBe('untitled');
});

test('bhv operations 109 get matrix decodes an array of integer arrays', function (): void {
    $api = newStoreApiForMock(200, 'application/json', '[[1,2],[3]]');

    $matrix = $api->getMatrix();

    expect($matrix->toArray()[0]->toArray())->toBe([1, 2]);
    expect($matrix->toArray()[1]->toArray())->toBe([3]);
});

test('bhv operations 112 get stock item decodes the integer backed enum and the nested arrays', function (): void {
    $api = newStoreApiForMock(
        200,
        'application/json',
        '{"priority":2,"availability":"on-hold","matrix":[[1,2],[3]]}'
    );

    $item = $api->getStockItem();

    expect($item->priority)->toBe(\PetstoreClient\Models\Priority::NUMBER_2);
    expect($item->availability)->toBe(\PetstoreClient\Models\Availability::ON_HOLD);
    expect($item->matrix?->toArray()[0]->toArray())->toBe([1, 2]);
    expect($item->matrix?->toArray()[1]->toArray())->toBe([3]);
});
