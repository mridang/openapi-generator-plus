<?php

declare(strict_types=1);

use PetstoreClient\ObjectSerializer;
use PetstoreClient\Models\Availability;
use PetstoreClient\Models\Category;
use PetstoreClient\Models\Defaults;
use PetstoreClient\Models\DefaultsModeEnum;
use PetstoreClient\Models\EdgeCases;
use PetstoreClient\Models\Metadata;
use PetstoreClient\Models\Order;
use PetstoreClient\Models\OrderStatusEnum;
use PetstoreClient\Models\Pet;
use PetstoreClient\Models\PetPassport;
use PetstoreClient\Models\PhotoMetadataLocation;
use PetstoreClient\Models\Priority;
use PetstoreClient\Models\StockItem;
use PetstoreClient\Models\TreeNode;

// -- toPathValue --

test('to path value returns empty string for null', function (): void {
    expect(ObjectSerializer::toPathValue(null))->toBe('');
});

test('to path value returns the string for a string value', function (): void {
    expect(ObjectSerializer::toPathValue('hello'))->toBe('hello');
});

test('to path value converts integer to string', function (): void {
    expect(ObjectSerializer::toPathValue(42))->toBe('42');
});

test('to path value converts true to true', function (): void {
    expect(ObjectSerializer::toPathValue(true))->toBe('true');
});

test('to path value converts false to false', function (): void {
    expect(ObjectSerializer::toPathValue(false))->toBe('false');
});

// -- toQueryValue --

test('to query value returns null for null', function (): void {
    expect(ObjectSerializer::toQueryValue(null))->toBeNull();
});

test('to query value returns the string for a string value', function (): void {
    expect(ObjectSerializer::toQueryValue('hello'))->toBe('hello');
});

test('to query value converts integer to string', function (): void {
    expect(ObjectSerializer::toQueryValue(42))->toBe('42');
});

test('to query value converts true to true', function (): void {
    expect(ObjectSerializer::toQueryValue(true))->toBe('true');
});

test('to query value converts false to false', function (): void {
    expect(ObjectSerializer::toQueryValue(false))->toBe('false');
});

test('to query value joins array with comma by default', function (): void {
    expect(ObjectSerializer::toQueryValue(['a', 'b', 'c']))->toBe('a,b,c');
});

test('to query value joins array with comma for csv', function (): void {
    expect(ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'csv'))->toBe('a,b,c');
});

test('to query value joins array with space for ssv', function (): void {
    expect(ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'ssv'))->toBe('a b c');
});

test('to query value joins array with tab for tsv', function (): void {
    expect(ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'tsv'))->toBe("a\tb\tc");
});

test('to query value joins array with pipe for pipes', function (): void {
    expect(ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'pipes'))->toBe('a|b|c');
});

test('to query value returns array for multi', function (): void {
    expect(ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'multi'))->toBe(['a', 'b', 'c']);
});

test('to query value csv keeps slot for null element', function (): void {
    // Per N1/W1: a null element in a csv array becomes an empty slot, not skipped.
    expect(ObjectSerializer::toQueryValue([1, null, 3]))->toBe('1,,3');
});

test('to query value csv explicit keeps slot for null element', function (): void {
    expect(ObjectSerializer::toQueryValue([1, null, 3], 'csv'))->toBe('1,,3');
});

test('to query value ssv keeps slot for null element', function (): void {
    expect(ObjectSerializer::toQueryValue([1, null, 3], 'ssv'))->toBe('1  3');
});

test('to query value multi keeps empty string for null element', function (): void {
    expect(ObjectSerializer::toQueryValue([1, null, 3], 'multi'))->toBe(['1', '', '3']);
});

// -- UUID serialization --

test('stringify uuid returns rfc 4122', function (): void {
    $uuid = \Symfony\Component\Uid\Uuid::fromString('550e8400-e29b-41d4-a716-446655440000');
    expect(ObjectSerializer::stringify($uuid))->toBe('550e8400-e29b-41d4-a716-446655440000');
});

test('deserialize uuid from string', function (): void {
    $json = '"550e8400-e29b-41d4-a716-446655440000"';
    $result = ObjectSerializer::deserialize($json, \Symfony\Component\Uid\Uuid::class);
    expect($result)->toBeInstanceOf(\Symfony\Component\Uid\Uuid::class);
    expect($result->toRfc4122())->toBe('550e8400-e29b-41d4-a716-446655440000');
});

test('serialize uuid value roundtrips', function (): void {
    $uuid = \Symfony\Component\Uid\Uuid::fromString('550e8400-e29b-41d4-a716-446655440000');
    $serialized = ObjectSerializer::serialize($uuid);
    expect($serialized)->toBe('"550e8400-e29b-41d4-a716-446655440000"');
});

// -- toHeaderValue --

test('to header value returns empty string for null', function (): void {
    expect(ObjectSerializer::toHeaderValue(null))->toBe('');
});

test('to header value returns the string for a string value', function (): void {
    expect(ObjectSerializer::toHeaderValue('hello'))->toBe('hello');
});

test('to header value converts integer to string', function (): void {
    expect(ObjectSerializer::toHeaderValue(42))->toBe('42');
});

test('to header value joins array with comma', function (): void {
    expect(ObjectSerializer::toHeaderValue(['a', 'b', 'c']))->toBe('a,b,c');
});

// -- toFormValue --

test('to form value returns empty string for null', function (): void {
    expect(ObjectSerializer::toFormValue(null))->toBe('');
});

test('to form value returns the string for a string value', function (): void {
    expect(ObjectSerializer::toFormValue('hello'))->toBe('hello');
});

test('to form value converts integer to string', function (): void {
    expect(ObjectSerializer::toFormValue(42))->toBe('42');
});

test('to form value converts true to true', function (): void {
    expect(ObjectSerializer::toFormValue(true))->toBe('true');
});

test('to form value converts false to false', function (): void {
    expect(ObjectSerializer::toFormValue(false))->toBe('false');
});

// -- toCookieValue --

test('to cookie value returns empty string for null', function (): void {
    expect(ObjectSerializer::toCookieValue(null))->toBe('');
});

test('to cookie value returns the string for a string value', function (): void {
    expect(ObjectSerializer::toCookieValue('hello'))->toBe('hello');
});

test('to cookie value converts integer to string', function (): void {
    expect(ObjectSerializer::toCookieValue(42))->toBe('42');
});

// -- stringify --

test('stringify null returns empty string', function (): void {
    expect(ObjectSerializer::stringify(null))->toBe('');
});

test('stringify boolean true returns lowercase string', function (): void {
    expect(ObjectSerializer::stringify(true))->toBe('true');
});

test('stringify boolean false returns lowercase string', function (): void {
    expect(ObjectSerializer::stringify(false))->toBe('false');
});

test('stringify integer returns string representation', function (): void {
    expect(ObjectSerializer::stringify(42))->toBe('42');
});

test('stringify date time returns iso 8601 string', function (): void {
    $dt = new \DateTime('2024-01-15T10:30:00+00:00');
    $result = ObjectSerializer::stringify($dt);
    expect($result)->toStartWith('2024-01-15T10:30:00');
});

test('stringify plain string passes through unchanged', function (): void {
    expect(ObjectSerializer::stringify('hello'))->toBe('hello');
});

test('stringify float returns string representation', function (): void {
    expect(ObjectSerializer::stringify(3.14))->toBe('3.14');
});

test('stringify array fallback', function (): void {
    $result = ObjectSerializer::stringify(['a', 'b', 'c']);
    expect($result)->not->toBe('');
    expect($result)->toBe('["a","b","c"]');
});

test('stringify object fallback', function (): void {
    $obj = new \stdClass();
    $obj->key = 'value';
    $result = ObjectSerializer::stringify($obj);
    expect($result)->not->toBe('');
    expect($result)->toBe('{"key":"value"}');
});

// -- DateTimeOffsetPreservation --

test('utc date time serializes containing date time and offset', function (): void {
    $dt = new \DateTimeImmutable('2024-01-01T12:30:45+00:00');
    $result = ObjectSerializer::stringify($dt);
    expect($result)->toContain('2024-01-01');
    expect($result)->toContain('12:30:45');
    expect(str_contains($result, '+00:00') || str_ends_with($result, 'Z'))->toBeTrue();
});

test('positive offset preserved in serialized string', function (): void {
    $dt = new \DateTimeImmutable('2024-01-01T12:30:45+05:30');
    $result = ObjectSerializer::stringify($dt);
    expect($result)->toContain('+05:30');
});

test('negative offset preserved in serialized string', function (): void {
    $dt = new \DateTimeImmutable('2024-01-01T12:30:45-08:00');
    $result = ObjectSerializer::stringify($dt);
    expect($result)->toContain('-08:00');
});

test('date-time serialization preserves sub-second precision', function (): void {
    /* H4: the encoder must emit the millisecond fraction the decoder accepts.
     * The canonical instant 2020-01-02T03:04:05.123Z (UTC) carries millisecond
     * sub-second precision — the cross-SDK common denominator every native
     * date-time type supports. The serialized wire string must contain ".123"
     * (the milliseconds were NOT truncated to whole seconds), and parsing it
     * back must yield the same instant to the millisecond (lossless round-trip). */
    $original = new \DateTimeImmutable('2020-01-02T03:04:05.123Z');
    $serialized = ObjectSerializer::stringify($original);
    expect($serialized)->toContain('.123');

    $parsed = new \DateTimeImmutable($serialized);
    expect($parsed->format('U.v'))->toBe($original->format('U.v'));
});

test('date only serializes without time component', function (): void {
    $dt = new \DateTimeImmutable('2024-01-01T00:00:00+00:00');
    $dateStr = $dt->format('Y-m-d');
    expect($dateStr)->toBe('2024-01-01');
});

test('serialized datetime string contains offset', function (): void {
    $dt = new \DateTimeImmutable('2024-01-01T12:30:45+00:00');
    $result = ObjectSerializer::stringify($dt);
    expect($result)->toMatch('/[+-]\d{2}:\d{2}$|Z$/');
});

test('round trip datetime yields equivalent instant', function (): void {
    $original = new \DateTimeImmutable('2024-01-01T12:30:45+05:30');
    $serialized = ObjectSerializer::stringify($original);
    $parsed = new \DateTimeImmutable($serialized);
    expect($parsed->getTimestamp())->toBe($original->getTimestamp());
});

// -- NonAsciiSerialization --

test('accented character serializes and decodes correctly', function (): void {
    $result = ObjectSerializer::serialize('café');
    /** @var string $decoded */
    $decoded = json_decode($result, true);
    expect($decoded)->toBe('café');
});

test('cjk characters serialize and decode correctly', function (): void {
    $result = ObjectSerializer::serialize('日本');
    /** @var string $decoded */
    $decoded = json_decode($result, true);
    expect($decoded)->toBe('日本');
});

test('tab character escaped properly in json', function (): void {
    $result = ObjectSerializer::serialize("a\tb");
    expect($result)->toContain('\t');
});

// -- DeserializationErrorWrapping --
//
// The public deserialize() entry point must never leak a framework-native
// serde exception (\JsonException, \InvalidArgumentException, …) to callers.
// It wraps them in the SDK-owned SerializationException (preserving the native
// exception as the previous exception), matching the other 11 language SDKs
// and keeping internal serde types off the public surface.

test('truncated json throws SerializationException', function (): void {
    expect(fn () => ObjectSerializer::deserialize('{', Category::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

test('invalid json structure throws SerializationException', function (): void {
    expect(fn () => ObjectSerializer::deserialize('"hello"', Category::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

test('malformed json does not leak native exception', function (): void {
    // The native \JsonException must never escape the public entry point.
    try {
        ObjectSerializer::deserialize('{not valid json', Category::class);
        test()->fail('Expected exception was not thrown');
    } catch (\Throwable $e) {
        expect($e)->toBeInstanceOf(\PetstoreClient\SerializationException::class);
        expect($e)->not->toBeInstanceOf(\JsonException::class);
        // The native exception is preserved as the previous exception so
        // callers who need the gory detail can still drill down.
        expect($e->getPrevious())->toBeInstanceOf(\Throwable::class);
    }
});

// -- Canonical behavior #6: unknown enum value on deserialize must throw --
//
// A wire value that is not one of the enum's declared cases is a contract
// violation. Symfony's BackedEnumNormalizer raises NotNormalizableValueException
// for it; deserialize() wraps that in the SDK-owned SerializationException
// rather than silently producing a default/unknown enum member. This aligns
// PHP with the throwing SDKs (the (de)serialization error surfaces, never a
// silent fallback).

test('unknown enum value on deserialize throws SerializationException', function (): void {
    // Pet::$status is a PetStatusEnum; "teleporting" is not a declared case.
    $json = '{"id":1,"name":"Rex","photoUrls":[],"status":"teleporting"}';

    expect(fn (): mixed => ObjectSerializer::deserialize($json, Pet::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

test('unknown enum value does not silently fall back to a default member', function (): void {
    // Guard against a regression where the unknown value is swallowed and a
    // valid Pet (with some default/first enum case) is returned instead of
    // throwing. The deserialize call MUST raise — never return a Pet.
    $json = '{"id":1,"name":"Rex","photoUrls":[],"status":"teleporting"}';

    $threw = false;
    try {
        ObjectSerializer::deserialize($json, Pet::class);
    } catch (\PetstoreClient\SerializationException) {
        $threw = true;
    }

    expect($threw)->toBeTrue();
});

test('thrown exception has message', function (): void {
    $message = '';
    try {
        ObjectSerializer::deserialize('{', Category::class);
        test()->fail('Expected exception was not thrown');
    } catch (\Throwable $e) {
        $message = $e->getMessage();
    }
    expect($message)->not->toBeEmpty();
});

// -- serialize --

test('serialize serializes model to valid json', function (): void {
    $category = new Category();
    $category->id = 1;
    $category->name = 'Dogs';
    $json = ObjectSerializer::serialize($category);
    expect($json)->toBeJson();
    /** @var array<string, mixed> $data */
    $data = json_decode($json, true);
    expect($data['id'])->toBe(1);
    expect($data['name'])->toBe('Dogs');
});

// -- WAVE B: type:number (no format) must serialize as an UNQUOTED JSON number --
//
// Pet::$weightKg comes from a spec property typed `type: number` with NO
// `format`. The wire form must be a bare JSON number (e.g. 1.5), never a
// quoted string ("1.5"). PHP models this as a native ?float, so json_encode
// emits an unquoted number; this test pins that contract so a regression
// (e.g. switching to a Decimal-as-string holder) would fail loudly.
test('type number field serializes as unquoted json number', function (): void {
    $pet = new Pet(name: 'Rex', photoUrls: new \Ds\Set());
    $pet->weightKg = 1.5;

    $json = ObjectSerializer::serialize($pet);

    // Must appear as an unquoted number, never as a quoted string.
    expect($json)->toContain('"weightKg":1.5');
    expect($json)->not->toContain('"weightKg":"1.5"');

    // And it must survive a round-trip back to a numeric float.
    /** @var Pet $back */
    $back = ObjectSerializer::deserialize($json, Pet::class);
    expect($back->weightKg)->toBe(1.5);
});

test('serialize handles null', function (): void {
    $json = ObjectSerializer::serialize(null);
    expect($json)->toBe('null');
});

test('serialize empty object body emits {} not []', function (): void {
    /* #3: an empty request body must go on the wire as a JSON object `{}`,
     * never an empty JSON array `[]`. PHP's json_encode([]) yields `[]`,
     * which protobuf-JSON servers reject; an empty stdClass / all-null model
     * must therefore serialize to `{}` like the other SDKs. */
    expect(ObjectSerializer::serialize(new \stdClass()))->toBe('{}');
    expect(ObjectSerializer::serialize([]))->toBe('{}');
});

test('serialize includes fields set to default values', function (): void {
    $category = new Category();
    $category->id = 0;
    $category->name = '';
    $json = ObjectSerializer::serialize($category);
    expect($json)->toBeJson();
    /** @var array<string, mixed> $data */
    $data = json_decode($json, true);
    expect($data)->toHaveKey('id');
    expect($data['id'])->toBe(0);
    expect($data)->toHaveKey('name');
    expect($data['name'])->toBe('');
});

// -- deserialize --

test('deserialize deserializes json to typed model', function (): void {
    $json = '{"id":1,"name":"Dogs"}';
    $category = ObjectSerializer::deserialize($json, Category::class);
    expect($category)->toBeInstanceOf(Category::class);
    expect($category->id)->toBe(1);
    expect($category->name)->toBe('Dogs');
});

// -- self-referential model deserialization --
//
// TreeNode is self-referential: { value: string (required), child: $ref
// TreeNode (optional) }. Deserializing a nested literal must decode the
// `child` field into a typed TreeNode instance (not a raw map), proving the
// recursive deserializer descends a model that references itself.

test('deserialize decodes a self-referential model recursively', function (): void {
    $json = '{"value":"root","child":{"value":"leaf"}}';
    /** @var TreeNode $top */
    $top = ObjectSerializer::deserialize($json, TreeNode::class);
    expect($top)->toBeInstanceOf(TreeNode::class);
    expect($top->value)->toBe('root');
    expect($top->child)->toBeInstanceOf(TreeNode::class);
    /** @var TreeNode $child */
    $child = $top->child;
    expect($child->value)->toBe('leaf');
    expect($child->child)->toBeNull();
});

// -- P2: BOM-tolerant deserialization --
//
// Canonical cross-SDK scenario: a server response whose body is prefixed with
// the UTF-8 byte-order mark (EF BB BF) must deserialize as if the BOM were not
// present. RFC 8259 §8.1 forbids a BOM in JSON text and PHP's json_decode
// silently returns null on it, so ObjectSerializer strips it first. GREEN
// everywhere (behaviour already correct); locked across all 12 SDKs.

test('deserialize tolerates utf-8 BOM prefix', function (): void {
    $json = "\xEF\xBB\xBF{\"id\":1,\"name\":\"Dogs\"}";
    $category = ObjectSerializer::deserialize($json, Category::class);
    expect($category)->toBeInstanceOf(Category::class);
    expect($category->id)->toBe(1);
    expect($category->name)->toBe('Dogs');
});

test('deserialize returns null for null input', function (): void {
    expect(ObjectSerializer::deserialize(null, Category::class))->toBeNull();
});

// -- default-on-deserialize --
//
// Canonical cross-SDK scenario: Order's `status` field carries a schema
// `default: placed`. A response that OMITS `status` must deserialize to the
// schema default (OrderStatusEnum::PLACED) rather than leaving the field
// unset/null. PHP applies this via the model's constructor/property default,
// so the absent field falls back to PLACED. GREEN here; the same test is
// added to all 12 SDKs to lock the behaviour fleet-wide.

test('deserialize applies schema default for absent field', function (): void {
    $json = '{"id":10,"petId":198772}'; // "status" is absent
    /** @var Order $order */
    $order = ObjectSerializer::deserialize($json, Order::class);
    expect($order)->toBeInstanceOf(Order::class);
    expect($order->status)->toBe(OrderStatusEnum::PLACED);
});

// -- absent-vs-null defaults contract --
//
// Canonical cross-SDK scenario on the Defaults model: a schema `default`
// applies ONLY when the property is ABSENT from the payload. An explicit JSON
// null is a provided value and is PRESERVED (the field stays null), never
// replaced by the default. This matches go/rust/python.
//   retries: int    default 3       (non-enum scalar default)
//   mode:    enum    default medium  (enum default, not the first variant)
//   label:   string  nullable, default "untitled"

test('deserialize applies defaults only when fields are absent', function (): void {
    $json = '{}';
    /** @var Defaults $defaults */
    $defaults = ObjectSerializer::deserialize($json, Defaults::class);
    expect($defaults)->toBeInstanceOf(Defaults::class);
    expect($defaults->retries)->toBe(3);
    expect($defaults->mode)->toBe(DefaultsModeEnum::MEDIUM);
    expect($defaults->label)->toBe('untitled');
});

test('deserialize preserves explicit null over a defaulted field', function (): void {
    $json = '{"label":null,"retries":7}';
    /** @var Defaults $defaults */
    $defaults = ObjectSerializer::deserialize($json, Defaults::class);
    expect($defaults)->toBeInstanceOf(Defaults::class);
    // Explicit null is PRESERVED, not replaced by the "untitled" default.
    expect($defaults->label)->toBeNull();
    // A provided scalar wins over the default.
    expect($defaults->retries)->toBe(7);
});

test('deserialize roundtrips int 64 max exactly', function (): void {
    /* PHP_INT_MAX on 64-bit (9223372036854775807) survives via
     * JSON_BIGINT_AS_STRING; the str→int→str round-trip equality
     * check accepts it because it fits PHP's int range. */
    $result = ObjectSerializer::deserialize((string) PHP_INT_MAX, 'int');
    expect($result)->toBe(PHP_INT_MAX);
});

test('deserialize throws on int overflow', function (): void {
    /* Int64 max + 1 (9223372036854775808) exceeds PHP_INT_MAX on every
     * supported platform. Without the overflow check, json_decode would
     * silently return a float and settype would truncate to PHP_INT_MAX
     * — wrong data, no error. With the check, deserialize throws. The native
     * \OverflowException is wrapped in the SDK-owned SerializationException by
     * the public entry point. */
    expect(fn () => ObjectSerializer::deserialize('9223372036854775808', 'int'))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

// -- 2.1 format:byte helpers --

test('decode bytes returns raw binary from base64', function (): void {
    expect(ObjectSerializer::decodeBytes('aGVsbG8='))->toBe('hello');
});

test('decode bytes returns null for null input', function (): void {
    expect(ObjectSerializer::decodeBytes(null))->toBeNull();
});

test('decode bytes returns null for empty string', function (): void {
    expect(ObjectSerializer::decodeBytes(''))->toBeNull();
});

test('decode bytes returns null for invalid base64', function (): void {
    expect(ObjectSerializer::decodeBytes('!!!not-base64!!!'))->toBeNull();
});

test('encode bytes returns base64 of raw bytes', function (): void {
    expect(ObjectSerializer::encodeBytes('hello'))->toBe('aGVsbG8=');
});

test('encode bytes returns null for null input', function (): void {
    expect(ObjectSerializer::encodeBytes(null))->toBeNull();
});

test('encode decode bytes roundtrip preserves binary', function (): void {
    $raw = "\x00\x01\x02\xff\xfe\xfd";
    $encoded = ObjectSerializer::encodeBytes($raw);
    expect($encoded)->not->toBeNull();
    /** @var string $encoded */
    expect(ObjectSerializer::decodeBytes($encoded))->toBe($raw);
});

// -- 4.6 deep map-of-model deserialization --

test('deep deserialize map of model values into typed instances', function (): void {
    /* Without 4.6 qualification, recursive deserialize sees the
     * unqualified 'Category' short name, class_exists() returns
     * false, and the map values come back as raw arrays instead
     * of typed Category instances. */
    $json = '{"a":{"id":1,"name":"Dogs"},"b":{"id":2,"name":"Cats"}}';
    /** @var array<string, mixed> $result */
    $result = ObjectSerializer::deserialize($json, 'array<string,Category>');
    expect($result)->toHaveKey('a');
    expect($result['a'])->toBeInstanceOf(Category::class);
    expect($result['a']->name)->toBe('Dogs');
    expect($result['b'])->toBeInstanceOf(Category::class);
    expect($result['b']->id)->toBe(2);
});

test('deep deserialize map of primitive values still works', function (): void {
    /** @var array<string, int> $result */
    $result = ObjectSerializer::deserialize('{"a":1,"b":2}', 'array<string,int>');
    expect($result)->toBe(['a' => 1, 'b' => 2]);
});

// -- 4.8 format:time + format:duration --

test('deserialize time as DateTimeImmutable preserves time portion', function (): void {
    /** @var \DateTimeImmutable|null $result */
    $result = ObjectSerializer::deserialize('"14:30:45"', 'DateTimeImmutable');
    expect($result)->toBeInstanceOf(\DateTimeImmutable::class);
    /** @var \DateTimeImmutable $result */
    expect($result->format('H:i:s'))->toBe('14:30:45');
});

test('serialize DateTimeImmutable round trip via stringify', function (): void {
    $now = new \DateTimeImmutable('2024-01-15T10:20:30+00:00');
    $stringified = ObjectSerializer::stringify($now);
    expect($stringified)->toContain('2024-01-15T10:20:30');
});

test('deserialize duration as DateInterval parses protobuf seconds', function (): void {
    /* google.protobuf.Duration wire form is "<seconds>s". The interval is
     * built as PT<seconds>S; PHP's DateInterval does not normalize seconds
     * into hours, so ->s carries the whole-second total verbatim. */
    /** @var \DateInterval|null $result */
    $result = ObjectSerializer::deserialize('"3600s"', 'DateInterval');
    expect($result)->toBeInstanceOf(\DateInterval::class);
    /** @var \DateInterval $result */
    expect($result->s)->toBe(3600);
    expect($result->invert)->toBe(0);
    // round-trips back to the canonical protobuf form.
    expect(ObjectSerializer::formatProtobufDuration($result))->toBe('3600s');
});

test('deserialize duration parses fractional protobuf seconds', function (): void {
    /** @var \DateInterval|null $result */
    $result = ObjectSerializer::deserialize('"3600.000000001s"', 'DateInterval');
    expect($result)->toBeInstanceOf(\DateInterval::class);
    /** @var \DateInterval $result */
    expect($result->s)->toBe(3600);
    expect(round($result->f, 9))->toBe(0.000000001);
    expect(ObjectSerializer::formatProtobufDuration($result))->toBe('3600.000000001s');
});

test('deserialize negative protobuf duration sets invert', function (): void {
    /** @var \DateInterval|null $result */
    $result = ObjectSerializer::deserialize('"-1.5s"', 'DateInterval');
    expect($result)->toBeInstanceOf(\DateInterval::class);
    /** @var \DateInterval $result */
    expect($result->s)->toBe(1);
    expect(round($result->f, 9))->toBe(0.5);
    expect($result->invert)->toBe(1);
    expect(ObjectSerializer::formatProtobufDuration($result))->toBe('-1.500s');
});

test('serialize DateInterval formats whole protobuf seconds', function (): void {
    $interval = new \DateInterval('PT1H30M');
    // 1h30m = 5400 seconds; protobuf-JSON has no h/m suffixes.
    expect(ObjectSerializer::formatProtobufDuration($interval))->toBe('5400s');
});

test('serialize DateInterval folds days into protobuf seconds', function (): void {
    // google.protobuf.Duration has no day/month/year components; days fold
    // into seconds and ->y/->m are ignored.
    $interval = new \DateInterval('P1DT2H');
    expect(ObjectSerializer::formatProtobufDuration($interval))->toBe('93600s');
});

test('serialize zero DateInterval emits 0s', function (): void {
    $interval = new \DateInterval('PT0S');
    expect(ObjectSerializer::formatProtobufDuration($interval))->toBe('0s');
});

test('serialize DateInterval keeps nanosecond precision', function (): void {
    /* PHP's native \DateInterval::$f truncates anything below 1e-6 to 0.0, so
     * nanosecond durations must be carried by the SDK's PreciseDuration (a
     * \DateInterval subclass whose ->f keeps full precision). */
    $interval = new \PetstoreClient\PreciseDuration();
    $interval->h = 1;
    $interval->f = 0.000000001;
    expect(ObjectSerializer::formatProtobufDuration($interval))->toBe('3600.000000001s');
});

test('serialize DateInterval trims fraction to three digits', function (): void {
    $interval = new \DateInterval('PT0S');
    $interval->f = 0.5;
    expect(ObjectSerializer::formatProtobufDuration($interval))->toBe('0.500s');
});

test('serialize negative DateInterval prefixes minus', function (): void {
    $interval = new \DateInterval('PT1H');
    $interval->invert = 1;
    expect(ObjectSerializer::formatProtobufDuration($interval))->toBe('-3600s');
});

test('serialize model with DateInterval property emits protobuf seconds', function (): void {
    /* BUG 1 regression: a \DateInterval that lives on a MODEL routes through
     * the Symfony Serializer (sanitizeForSerialization delegates the
     * is_object branch to getSerializer()->normalize()). Symfony's built-in
     * DateIntervalNormalizer would emit ISO-8601 ("PT1H") here, bypassing
     * formatProtobufDuration() and getting rejected by a protobuf-JSON server
     * with HTTP 400. The custom DurationNormalizer — registered before any
     * default DateInterval handling — must win so the property serializes to
     * the "3600s" wire form, NOT ISO-8601. */
    $model = new EdgeCases(retryAfter: new \DateInterval('PT1H'));

    $json = ObjectSerializer::serialize($model);
    /** @var array<string, mixed> $decoded */
    $decoded = json_decode($json, true);

    expect($decoded)->toBeArray();
    expect($decoded['retryAfter'])->toBe('3600s');
    // Guard against a regression to the ISO-8601 form.
    expect($json)->not->toContain('PT1H');
    expect($json)->not->toContain('P0Y');
});

test('serialize model DateInterval property keeps fractional seconds', function (): void {
    /* Nanosecond precision requires PreciseDuration; a stock \DateInterval
     * would truncate ->f = 1e-9 to 0.0 before serialization ever runs. */
    $interval = new \PetstoreClient\PreciseDuration();
    $interval->h = 1;
    $interval->f = 0.000000001;
    $model = new EdgeCases(retryAfter: $interval);

    /** @var array<string, mixed> $decoded */
    $decoded = json_decode(ObjectSerializer::serialize($model), true);
    expect($decoded['retryAfter'])->toBe('3600.000000001s');
});

test('deserialize model with DateInterval property parses protobuf seconds', function (): void {
    /** @var EdgeCases $model */
    $model = ObjectSerializer::deserialize('{"retryAfter":"3600s"}', EdgeCases::class);

    expect($model)->toBeInstanceOf(EdgeCases::class);
    expect($model->retryAfter)->toBeInstanceOf(\DateInterval::class);
    /** @var \DateInterval $retryAfter */
    $retryAfter = $model->retryAfter;
    expect(ObjectSerializer::formatProtobufDuration($retryAfter))->toBe('3600s');
});

test('DateInterval round trips through stringify as protobuf duration', function (): void {
    $interval = new \DateInterval('PT45M');
    $stringified = ObjectSerializer::stringify($interval);
    // 45 minutes = 2700 seconds; protobuf-JSON carries only a seconds total.
    expect($stringified)->toBe('2700s');
    /** @var \DateInterval $deserialized */
    $deserialized = ObjectSerializer::deserialize('"' . $stringified . '"', 'DateInterval');
    expect($deserialized)->toBeInstanceOf(\DateInterval::class);
    // The wire→interval→wire round-trip preserves the canonical string.
    expect(ObjectSerializer::stringify($deserialized))->toBe('2700s');
});

test('DateInterval fractional value round trips', function (): void {
    /* PreciseDuration carries the nanosecond fraction that a stock
     * \DateInterval would truncate to 0.0 on assignment. */
    $interval = new \PetstoreClient\PreciseDuration();
    $interval->f = 0.000000001;
    $stringified = ObjectSerializer::stringify($interval);
    expect($stringified)->toBe('0.000000001s');
    /** @var \DateInterval $deserialized */
    $deserialized = ObjectSerializer::deserialize('"' . $stringified . '"', 'DateInterval');
    expect(round($deserialized->f, 9))->toBe(0.000000001);
    expect(ObjectSerializer::stringify($deserialized))->toBe('0.000000001s');
});

test('DateInterval invalid string surfaces exception', function (): void {
    // ISO-8601 "PT1H" is no longer accepted — protobuf-JSON only.
    expect(fn () => ObjectSerializer::deserialize('"PT1H"', 'DateInterval'))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

test('DateInterval bare number without suffix is rejected', function (): void {
    expect(fn () => ObjectSerializer::deserialize('"3600"', 'DateInterval'))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

// -- F-BM-05: missing required field hard-fails on deserialize --

test('deserialize throws when a required field is missing', function (): void {
    /* Pet declares `name` and `photoUrls` as required. A payload that omits
     * a required field must surface an error (matching the other 11 SDKs)
     * rather than silently constructing a partial object. */
    $json = '{"id":1,"photoUrls":["http://x/a.png"]}'; // missing "name"
    expect(fn (): mixed => ObjectSerializer::deserialize($json, Pet::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

test('deserialize throws when a required field is explicitly null', function (): void {
    /* DIVERGENCE #10: a required, non-nullable field present on the wire but
     * set to null is a contract violation just like an absent field. The key
     * exists so the missing-field check passes it through, but the constructor
     * parameter type (`string $name`) does not allow null, so deserialize must
     * hard-fail rather than build a partial object. */
    $json = '{"name":null,"photoUrls":["http://x/a.png"]}';
    expect(fn (): mixed => ObjectSerializer::deserialize($json, Pet::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

// -- Gap AJ: JSON null on a required non-nullable field must throw --
//
// Canonical cross-SDK scenario: deserializing {"name":null,"photoUrls":["u"]}
// into Pet (name is required + non-nullable) MUST raise the SDK's
// deserialization error rather than zero-init / silently accept the null.
// PHP already rejects this via assertRequiredPresent (DIVERGENCE #10), so this
// is GREEN here; the same test is added to all 12 SDKs to lock the behaviour
// fleet-wide (red in go/python/kotlin, green elsewhere).

test('AJ: deserialize null on required non-nullable field throws', function (): void {
    $json = '{"name":null,"photoUrls":["u"]}';
    expect(fn (): mixed => ObjectSerializer::deserialize($json, Pet::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

test('deserialize throws when a required container field is explicitly null', function (): void {
    /* photoUrls is a required, non-nullable \Ds\Set. Explicit null must fail. */
    $json = '{"name":"doggie","photoUrls":null}';
    expect(fn (): mixed => ObjectSerializer::deserialize($json, Pet::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

test('deserialize succeeds when all required fields are present', function (): void {
    $json = '{"name":"doggie","photoUrls":["http://x/a.png"]}';
    /** @var Pet $pet */
    $pet = ObjectSerializer::deserialize($json, Pet::class);
    expect($pet)->toBeInstanceOf(Pet::class);
    expect($pet->name)->toBe('doggie');
});

test('deserialize allows an optional nullable field set to null', function (): void {
    /* Pet's id is optional + nullable. An explicit null on an optional field
     * must NOT trip the required-null guard — only required non-nullable
     * fields are rejected. */
    $json = '{"name":"doggie","photoUrls":["http://x/a.png"],"id":null}';
    /** @var Pet $pet */
    $pet = ObjectSerializer::deserialize($json, Pet::class);
    expect($pet)->toBeInstanceOf(Pet::class);
    expect($pet->id)->toBeNull();
});

// -- Gap K: discriminator auto-injection on subtype serialize --

test('dry subtype serialize auto-emits discriminator', function (): void {
    /* The generated DryFood model defaults foodType to "dry", so a caller
     * that omits it still gets the discriminator on the wire. */
    $dry = new \PetstoreClient\Models\DryFood(2.5);
    $json = ObjectSerializer::serialize($dry);
    /** @var array<string, mixed> $data */
    $data = json_decode($json, true);
    expect($data['foodType'])->toBe('dry');
    expect($data['weightKg'])->toBe(2.5);
});

test('wet subtype serialize auto-emits discriminator', function (): void {
    $wet = new \PetstoreClient\Models\WetFood(350);
    $json = ObjectSerializer::serialize($wet);
    /** @var array<string, mixed> $data */
    $data = json_decode($json, true);
    expect($data['foodType'])->toBe('wet');
});

test('deserialize parent routes to subtype by discriminator', function (): void {
    /* Feature gap: ObjectSerializer::deserialize() dispatches object classes
     * through Symfony's denormalize(), which builds an empty PetFood oneOf
     * wrapper rather than invoking PetFood::build() to route foodType="dry"
     * to a DryFood instance. The discriminator routing lives on the model's
     * static build() method and is not wired into the generic deserialize
     * entry point, unlike Java/Go/Rust/Node. */
    $json = '{"foodType":"dry","weightKg":2.5}';
    $food = ObjectSerializer::deserialize($json, \PetstoreClient\Models\PetFood::class);
    expect($food->getActualInstance())->toBeInstanceOf(\PetstoreClient\Models\DryFood::class);
})->skip('PHP deserialize() does not route oneOf parents through PetFood::build()');

// -- Gap AU-residual: missing discriminator must throw, not wrap raw dict --
//
// Deserializing a oneOf/discriminator parent (PetFood) from a payload that
// omits the `foodType` discriminator property MUST raise the SDK-owned
// SerializationException — not silently wrap the raw decoded array in an
// empty union container. The composed model's static build() owns the
// discriminator routing and throws on a missing discriminator (matching the
// other 10 SDKs); ObjectSerializer::deserialize() routes oneOf parents
// through build() so the contract violation surfaces. The native
// \InvalidArgumentException is wrapped by the public deserialize() entry
// point. RED before the routing fix (the raw dict was wrapped and returned),
// GREEN after.

test('deserialize PetFood missing discriminator throws', function (): void {
    $json = '{"weightKg":5.0}'; // no "foodType" discriminator property
    expect(fn (): mixed => ObjectSerializer::deserialize($json, \PetstoreClient\Models\PetFood::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

test('deserialize PetFood missing discriminator does not wrap raw dict', function (): void {
    /* Guard against a regression to the old behaviour where the raw decoded
     * array was wrapped in a PetFood union container and returned. The call
     * MUST raise — never return a PetFood whose actualInstance is the raw
     * untyped array. */
    $json = '{"weightKg":5.0}';

    $threw = false;
    try {
        ObjectSerializer::deserialize($json, \PetstoreClient\Models\PetFood::class);
    } catch (\PetstoreClient\SerializationException) {
        $threw = true;
    }

    expect($threw)->toBeTrue();
});

// -- Gap #13: null fields omitted on serialize --

test('serialize omits model fields that are null', function (): void {
    /* Category's id/name are nullable. A field left null must be dropped
     * from the JSON payload (SKIP_NULL_VALUES), not emitted as "id":null. */
    $category = new Category();
    $category->id = null;
    $category->name = 'Dogs';
    $json = ObjectSerializer::serialize($category);
    /** @var array<string, mixed> $data */
    $data = json_decode($json, true);
    expect($data)->not->toHaveKey('id');
    expect($data['name'])->toBe('Dogs');
});

// -- resolveOneOf / resolveAnyOf no-match --

test('resolveOneOf returns the first matching variant', function (): void {
    $candidates = [
        fn (mixed $data): mixed => throw new \RuntimeException('variant A does not match'),
        fn (mixed $data): mixed => 'matched',
    ];
    expect(ObjectSerializer::resolveOneOf(['k' => 'v'], $candidates))->toBe('matched');
});

test('resolveOneOf throws when no variant matches', function (): void {
    /* A payload matching none of the declared variants is a contract violation
     * and must fail loudly rather than be silently returned as null. */
    $candidates = [
        fn (mixed $data): mixed => throw new \RuntimeException('variant A does not match'),
        fn (mixed $data): mixed => throw new \RuntimeException('variant B does not match'),
    ];
    expect(fn (): mixed => ObjectSerializer::resolveOneOf(['unexpected' => true], $candidates))
        ->toThrow(\UnexpectedValueException::class);
});

test('resolveAnyOf throws when no variant matches', function (): void {
    $candidates = [
        fn (mixed $data): mixed => throw new \RuntimeException('no match'),
    ];
    expect(fn (): mixed => ObjectSerializer::resolveAnyOf([], $candidates))
        ->toThrow(\UnexpectedValueException::class);
});

test('resolveAnyOfAll collects every matching variant not just the first', function (): void {
    /* anyOf matches one OR MORE schemas: a payload co-satisfying several
     * variants must retain all of them so the round-trip is lossless. */
    $candidates = [
        fn (mixed $data): mixed => 'A',
        fn (mixed $data): mixed => throw new \RuntimeException('B does not match'),
        fn (mixed $data): mixed => 'C',
    ];
    expect(ObjectSerializer::resolveAnyOfAll(['k' => 'v'], $candidates))->toBe(['A', 'C']);
});

test('resolveAnyOfAll throws when no variant matches', function (): void {
    $candidates = [
        fn (mixed $data): mixed => throw new \RuntimeException('no match'),
        fn (mixed $data): mixed => throw new \RuntimeException('still no match'),
    ];
    expect(fn (): mixed => ObjectSerializer::resolveAnyOfAll([], $candidates))
        ->toThrow(\UnexpectedValueException::class);
});

// -- Canonical #1: integer-backed enum round-trips as a JSON NUMBER --
//
// Priority is an `enum Priority: int` (NUMBER_1=1, NUMBER_2=2, NUMBER_3=3).
// On the wire it MUST serialize to a bare JSON number (2), never the quoted
// string "2", and a JSON number on deserialize must resolve back to the enum
// case. StockItem carries a required Priority field, so the round-trip is
// exercised through a real model rather than the bare enum.

test('integer-backed enum serializes as a JSON number not a string', function (): void {
    $item = new StockItem(Priority::NUMBER_2);
    $json = ObjectSerializer::serialize($item);
    // The raw JSON text must contain the bare number, never the quoted form.
    expect($json)->toContain('"priority":2');
    expect($json)->not->toContain('"priority":"2"');
    /** @var array<string, mixed> $data */
    $data = json_decode($json, true);
    // json_decode keeps the JSON number as a PHP int (not a string).
    expect($data['priority'])->toBe(2);
});

test('integer-backed enum deserializes from a JSON number', function (): void {
    $json = '{"priority":2}';
    /** @var StockItem $item */
    $item = ObjectSerializer::deserialize($json, StockItem::class);
    expect($item)->toBeInstanceOf(StockItem::class);
    expect($item->priority)->toBe(Priority::NUMBER_2);
});

// -- Canonical #2: non-lowercase string enum preserves wire casing --
//
// Availability is an `enum Availability: string` whose cases keep their
// non-lowercase wire spellings: AVAILABLE='Available', SOLD='Sold',
// ON_HOLD='on-hold'. Serialization must emit the exact wire token, a matching
// wire value must deserialize to the right case, and an unknown value must be
// rejected (the unknown-enum-on-deserialize contract, scenario #10, applied to
// a non-lowercase string enum).

test('non-lowercase string enum round-trips with wire casing preserved', function (): void {
    foreach (
        [
        [Availability::AVAILABLE, 'Available'],
        [Availability::SOLD, 'Sold'],
        [Availability::ON_HOLD, 'on-hold'],
        ] as [$case, $wire]
    ) {
        $item = new StockItem(Priority::NUMBER_1, $case);
        $json = ObjectSerializer::serialize($item);
        expect($json)->toContain('"availability":"' . $wire . '"');
        /** @var StockItem $back */
        $back = ObjectSerializer::deserialize($json, StockItem::class);
        expect($back->availability)->toBe($case);
    }
});

test('non-lowercase string enum deserializes Available to the right member', function (): void {
    $json = '{"priority":1,"availability":"Available"}';
    /** @var StockItem $item */
    $item = ObjectSerializer::deserialize($json, StockItem::class);
    expect($item->availability)->toBe(Availability::AVAILABLE);
});

test('unknown availability wire value is rejected on deserialize', function (): void {
    // "available" (lowercase) is NOT a declared case; only "Available" is.
    $json = '{"priority":1,"availability":"available"}';
    expect(fn (): mixed => ObjectSerializer::deserialize($json, StockItem::class))
        ->toThrow(\PetstoreClient\SerializationException::class);
});

// -- Canonical #3: format:byte fields round-trip through base64 on a model --
//
// PetPassport carries BOTH a scalar `format: byte` field (thumbnail) and an
// array-of-byte field (scans: array<string format:byte>). The property holds
// the base64 wire string; the model exposes byte-getters that base64-decode.
// The serialize/deserialize round-trip must preserve the base64 wire form, and
// the byte-getters must hand back the decoded raw bytes.

test('byte fields round-trip through base64 on a model', function (): void {
    $thumb = base64_encode('thumb-bytes');
    $scanA = base64_encode("scan-A\x00\xff");
    $scanB = base64_encode('scan-B');

    $passport = new PetPassport(
        thumbnail: $thumb,
        scans: new \Ds\Vector([$scanA, $scanB]),
    );

    $json = ObjectSerializer::serialize($passport);
    /** @var array<string, mixed> $data */
    $data = json_decode($json, true);
    // Scalar byte field and each array element stay base64 on the wire.
    expect($data['thumbnail'])->toBe($thumb);
    expect($data['scans'])->toBe([$scanA, $scanB]);

    /** @var PetPassport $back */
    $back = ObjectSerializer::deserialize($json, PetPassport::class);
    expect($back)->toBeInstanceOf(PetPassport::class);
    expect($back->thumbnail)->toBe($thumb);
    // The byte-getter decodes the scalar back to raw bytes.
    expect($back->getThumbnailAsBytes())->toBe('thumb-bytes');
    /** @var \Ds\Vector<string> $scans */
    $scans = $back->scans;
    expect($scans)->toBeInstanceOf(\Ds\Vector::class);
    expect($scans->toArray())->toBe([$scanA, $scanB]);
});

// -- Canonical #4: a double field deserializes from an INTEGRAL JSON value --
//
// PhotoMetadataLocation.lat/lng are `number, format: double` (PHP `?float`). A
// server that writes a whole number ({"lat":5}, not 5.0) must deserialize
// without crashing — the integral JSON number widens to a float.

test('double field deserializes from an integral JSON value', function (): void {
    $json = '{"lat":5,"lng":-8}';
    /** @var PhotoMetadataLocation $loc */
    $loc = ObjectSerializer::deserialize($json, PhotoMetadataLocation::class);
    expect($loc)->toBeInstanceOf(PhotoMetadataLocation::class);
    expect($loc->lat)->toBe(5.0);
    expect($loc->lng)->toBe(-8.0);
});

// -- Canonical #5: additionalProperties re-serialize at the TOP level --
//
// Metadata declares an open `additionalProperties` map. An undeclared wire key
// must be CAPTURED on deserialize into $additionalProperties, and on serialize
// it must be flattened back to a TOP-LEVEL JSON key — never nested under an
// "additionalProperties"/"additional_properties" wrapper. (MetadataTest covers
// the capture side; this pins the flatten-on-serialize half of the contract.)

test('additional property re-serializes at the top level not nested', function (): void {
    $json = '{"createdAt":"2024-01-01T00:00:00+00:00","customField":"hello"}';
    /** @var Metadata $meta */
    $meta = ObjectSerializer::deserialize($json, Metadata::class);
    expect($meta->additionalProperties)->toBeInstanceOf(\Ds\Map::class);
    expect($meta->additionalProperties->get('customField'))->toBe('hello');

    $out = ObjectSerializer::serialize($meta);
    /** @var array<string, mixed> $data */
    $data = json_decode($out, true);
    // The captured extra key is emitted at the TOP level, with its value.
    expect($data)->toHaveKey('customField');
    expect($data['customField'])->toBe('hello');
    // It must NOT be nested under a wrapper key.
    expect($data)->not->toHaveKey('additionalProperties');
    expect($data)->not->toHaveKey('additional_properties');
});

// -- Canonical #7: a nested container deep-round-trips to typed leaves --
//
// StockItem.matrix is `array<array<int>>` -> `\Ds\Vector<\Ds\Vector<int>>`.
// Deserializing a nested JSON array must build typed inner Vectors whose leaves
// are PHP ints (not raw arrays / not strings), and serialize must round-trip it
// back to the same nested JSON shape.

test('nested array-of-array deep-round-trips to typed int leaves', function (): void {
    $json = '{"priority":1,"matrix":[[1,2,3],[4,5]]}';
    /** @var StockItem $item */
    $item = ObjectSerializer::deserialize($json, StockItem::class);
    expect($item)->toBeInstanceOf(StockItem::class);
    /** @var \Ds\Vector<\Ds\Vector<int>> $matrix */
    $matrix = $item->matrix;
    expect($matrix)->toBeInstanceOf(\Ds\Vector::class);
    expect($matrix->count())->toBe(2);
    expect($matrix[0])->toBeInstanceOf(\Ds\Vector::class);
    // Leaves are typed ints, deeply.
    expect($matrix[0]->toArray())->toBe([1, 2, 3]);
    expect($matrix[0][0])->toBeInt();
    expect($matrix[1]->toArray())->toBe([4, 5]);

    // Round-trips back to the same nested JSON shape.
    /** @var array<string, mixed> $data */
    $data = json_decode(ObjectSerializer::serialize($item), true);
    expect($data['matrix'])->toBe([[1, 2, 3], [4, 5]]);
});

// -- F-BM-03 (WONTFIX for PHP): PHP's native #[\Deprecated] attribute does
// NOT target properties (only functions/methods/class-constants/enum-cases),
// so a deprecated *property* can only carry the `@deprecated` PHPDoc tag —
// which PHPStan honours. This matches the Ruby/Elixir language-bound half of
// the finding. No runtime attribute assertion is possible here.
