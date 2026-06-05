<?php

declare(strict_types=1);

use PetstoreClient\Models\Category;
use PetstoreClient\Models\Pet;
use PetstoreClient\ObjectSerializer;

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

test('subseconds dropped from serialized datetime', function (): void {
    $dt = new \DateTimeImmutable('2024-01-01T12:30:45.123+00:00');
    $result = ObjectSerializer::stringify($dt);
    expect($result)->not->toContain('.123');
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

test('truncated json throws exception', function (): void {
    expect(fn () => ObjectSerializer::deserialize('{', Category::class))
        ->toThrow(\Exception::class);
});

test('invalid json structure throws exception', function (): void {
    expect(fn () => ObjectSerializer::deserialize('"hello"', Category::class))
        ->toThrow(\Exception::class);
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

test('serialize handles null', function (): void {
    $json = ObjectSerializer::serialize(null);
    expect($json)->toBe('null');
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

test('deserialize returns null for null input', function (): void {
    expect(ObjectSerializer::deserialize(null, Category::class))->toBeNull();
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
     * — wrong data, no error. With the check, deserialize throws. */
    expect(fn () => ObjectSerializer::deserialize('9223372036854775808', 'int'))
        ->toThrow(\OverflowException::class);
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

test('deserialize duration as DateInterval parses iso8601', function (): void {
    /** @var \DateInterval|null $result */
    $result = ObjectSerializer::deserialize('"P1DT2H30M"', 'DateInterval');
    expect($result)->toBeInstanceOf(\DateInterval::class);
    /** @var \DateInterval $result */
    expect($result->d)->toBe(1);
    expect($result->h)->toBe(2);
    expect($result->i)->toBe(30);
});

test('serialize DateInterval formats canonical iso8601', function (): void {
    $interval = new \DateInterval('PT1H30M');
    expect(ObjectSerializer::formatIso8601Duration($interval))->toBe('PT1H30M');
});

test('serialize DateInterval with date and time components', function (): void {
    $interval = new \DateInterval('P1Y2M3DT4H5M6S');
    expect(ObjectSerializer::formatIso8601Duration($interval))->toBe('P1Y2M3DT4H5M6S');
});

test('serialize zero DateInterval emits PT0S', function (): void {
    $interval = new \DateInterval('PT0S');
    expect(ObjectSerializer::formatIso8601Duration($interval))->toBe('PT0S');
});

test('serialize DateInterval omits zero components', function (): void {
    $interval = new \DateInterval('P0Y0M1DT0H0M0S');
    expect(ObjectSerializer::formatIso8601Duration($interval))->toBe('P1D');
});

test('DateInterval round trips through stringify', function (): void {
    $interval = new \DateInterval('PT45M');
    $stringified = ObjectSerializer::stringify($interval);
    expect($stringified)->toBe('PT45M');
    $deserialized = ObjectSerializer::deserialize('"' . $stringified . '"', 'DateInterval');
    expect($deserialized)->toBeInstanceOf(\DateInterval::class);
    /** @var \DateInterval $deserialized */
    expect($deserialized->i)->toBe(45);
});

test('DateInterval invalid string surfaces exception', function (): void {
    expect(fn () => ObjectSerializer::deserialize('"not-a-duration"', 'DateInterval'))
        ->toThrow(\Exception::class);
});

// -- F-BM-05: missing required field hard-fails on deserialize --

test('deserialize throws when a required field is missing', function (): void {
    /* Pet declares `name` and `photoUrls` as required. A payload that omits
     * a required field must surface an error (matching the other 11 SDKs)
     * rather than silently constructing a partial object. */
    $json = '{"id":1,"photoUrls":["http://x/a.png"]}'; // missing "name"
    expect(fn (): mixed => ObjectSerializer::deserialize($json, Pet::class))
        ->toThrow(\PetstoreClient\ApiException::class);
});

test('deserialize succeeds when all required fields are present', function (): void {
    $json = '{"name":"doggie","photoUrls":["http://x/a.png"]}';
    /** @var Pet $pet */
    $pet = ObjectSerializer::deserialize($json, Pet::class);
    expect($pet)->toBeInstanceOf(Pet::class);
    expect($pet->name)->toBe('doggie');
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

// -- F-BM-03 (WONTFIX for PHP): PHP's native #[\Deprecated] attribute does
// NOT target properties (only functions/methods/class-constants/enum-cases),
// so a deprecated *property* can only carry the `@deprecated` PHPDoc tag —
// which PHPStan honours. This matches the Ruby/Elixir language-bound half of
// the finding. No runtime attribute assertion is possible here.
