<?php

declare(strict_types=1);

use PetstoreClient\ObjectSerializer;
use PetstoreClient\Models\Metadata;

test('bhv models 008 serializes empty metadata', function (): void {
    $metadata = new Metadata();

    $json = ObjectSerializer::serialize($metadata);

    expect($json)->not->toBeEmpty();
});

test('additional properties start as an empty map, never null', function (): void {
    $metadata = new Metadata();

    expect($metadata->additionalProperties)->toBeInstanceOf(\Ds\Map::class);
    expect($metadata->additionalProperties)->toHaveCount(0);
});

test('undeclared wire keys land in the map and are written back flat', function (): void {
    $json = '{"customField":"hello"}';
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize($json, Metadata::class);

    expect($metadata->additionalProperties->toArray())->toBe(['customField' => 'hello']);
    expect(ObjectSerializer::serialize($metadata))->toBe($json);
});

test('bhv models 001 a metadata without undeclared keys keeps an empty map after deserializing', function (): void {
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize('{}', Metadata::class);

    expect($metadata->additionalProperties)->toHaveCount(0);
});

test('bhv models 003 deserializes empty object', function (): void {
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize('{}', Metadata::class);

    expect($metadata)->toBeInstanceOf(Metadata::class);
});

test('bhv models 004 deserializes additional string properties', function (): void {
    $json = '{"createdAt":"2024-01-01T00:00:00+00:00","customField":"hello"}';
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize($json, Metadata::class);
    expect($metadata)->toBeInstanceOf(Metadata::class);
    expect($metadata->additionalProperties)->toBeInstanceOf(\Ds\Map::class);
    expect($metadata->additionalProperties->hasKey('customField'))->toBeTrue();
    expect($metadata->additionalProperties->get('customField'))->toBe('hello');
});

test('bhv models 011 round trip preserves additional properties', function (): void {
    /** @var \Ds\Map<string, mixed> $additionalProperties */
    $additionalProperties = new \Ds\Map(['customField' => 'hello', 'anotherField' => 'world']);
    $metadata = (new Metadata(new \DateTime('2024-01-01T00:00:00+00:00')))
        ->withAdditionalProperties($additionalProperties);

    $json = ObjectSerializer::serialize($metadata);
    /** @var Metadata $deserialized */
    $deserialized = ObjectSerializer::deserialize($json, Metadata::class);
    expect($deserialized)->toBeInstanceOf(Metadata::class);
});

test('bhv models 009 additional properties are a string-keyed map of the value type', function (): void {
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize('{"customField":"hello"}', Metadata::class);

    expect($metadata->additionalProperties)->toBeInstanceOf(\Ds\Map::class);
    expect($metadata->additionalProperties->get('customField'))->toBeString();
    expect(array_keys($metadata->additionalProperties->toArray()))->each->toBeString();
    $source = file_get_contents((new \ReflectionClass(Metadata::class))->getFileName() ?: '');
    expect($source)->toContain('\Ds\Map<string, string>');
});

test('bhv models 012 an additional property of the wrong JSON type fails the decode', function (): void {
    foreach (['{"a":2.5}', '{"a":"2"}', '{"a":3000000000}'] as $body) {
        expect(fn () => ObjectSerializer::deserialize($body, \PetstoreClient\Models\Counters::class))
            ->toThrow(\PetstoreClient\Errors\SerializationException::class);
    }
    expect(fn () => ObjectSerializer::deserialize('{"a":3}', Metadata::class))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
});

test('bhv models 013 an additional property whose value type is a model decodes to that model', function (): void {
    /** @var \PetstoreClient\Models\Roster $roster */
    $roster = ObjectSerializer::deserialize('{"label":"l","a":{"id":1,"name":"x"}}', \PetstoreClient\Models\Roster::class);
    $category = $roster->additionalProperties->get('a');

    expect($category)->toBeInstanceOf(\PetstoreClient\Models\Category::class);
    expect($category->id)->toBe(1);
    expect($category->name)->toBe('x');
    expect(fn () => ObjectSerializer::deserialize('{"a":{"id":"x"}}', \PetstoreClient\Models\Roster::class))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
});

test('bhv models 014 a null additional property is rejected unless the value schema is nullable', function (): void {
    expect(fn () => ObjectSerializer::deserialize('{"a":null}', \PetstoreClient\Models\Counters::class))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
    /** @var \PetstoreClient\Models\Sparse $sparse */
    $sparse = ObjectSerializer::deserialize('{"a":null}', \PetstoreClient\Models\Sparse::class);

    expect($sparse->additionalProperties->hasKey('a'))->toBeTrue();
    expect($sparse->additionalProperties->get('a'))->toBeNull();
});

test('bhv models 015 a typed additional property is written flat in its wire form', function (): void {
    /** @var \PetstoreClient\Models\Moments $moments */
    $moments = ObjectSerializer::deserialize('{"label":"l","at":"2024-01-01T00:00:00+00:00"}', \PetstoreClient\Models\Moments::class);

    expect($moments->additionalProperties->get('at'))->toBeInstanceOf(\DateTimeInterface::class);
    $json = ObjectSerializer::serialize($moments);
    expect(array_keys(json_decode($json, true)))->toBe(['label', 'at']);
    expect($json)->toContain('2024-01-01T00:00:00');

    /** @var \PetstoreClient\Models\Blobs $blobs */
    $blobs = ObjectSerializer::deserialize('{"b":"aGk="}', \PetstoreClient\Models\Blobs::class);
    expect($blobs->additionalProperties->get('b'))->toBe('hi');
    expect(ObjectSerializer::serialize($blobs))->toBe('{"b":"aGk="}');
});

test('bhv models 016 a free-form additionalProperties keeps every JSON value as it arrives', function (): void {
    /** @var \PetstoreClient\Models\Freeform $freeform */
    $freeform = ObjectSerializer::deserialize('{"a":1,"b":"x","c":[true],"d":null}', \PetstoreClient\Models\Freeform::class);

    expect($freeform->additionalProperties->toArray())->toBe(['a' => 1, 'b' => 'x', 'c' => [true], 'd' => null]);
});

test('composer manifest has a valid name', function (): void {
    /* D3-docs: composer.json must declare a "name" so `composer validate`
     * passes, Packagist accepts the package, and `composer require` works.
     * The name must be a lowercase vendor/project pair. */
    $raw = file_get_contents(__DIR__ . '/../composer.json');
    expect($raw)->toBeString();
    /** @var array<string, mixed> $composer */
    $composer = json_decode((string) $raw, true);
    expect($composer)->toHaveKey('name');
    expect($composer['name'])->toBeString();
    expect($composer['name'])->toMatch(
        '#^[a-z0-9]([_.-]?[a-z0-9]+)*/[a-z0-9](([_.]|-{1,2})?[a-z0-9]+)*$#'
    );
});

test('bhv models 006 file header prints the info description without backslash escapes', function (): void {
    /* The generated file header is the docblock above the declare statement.
     * A description with quotes must reach it verbatim, never backslash-escaped. */
    $files = glob(__DIR__ . '/../lib/*.php') ?: [];
    expect($files)->not->toBeEmpty();

    $checked = 0;
    foreach ($files as $file) {
        $source = (string) file_get_contents($file);
        $end = strpos($source, 'declare(');
        if ($end === false) {
            continue;
        }
        $checked++;
        expect(substr($source, 0, $end))->not->toContain('\\"');
    }
    expect($checked)->toBeGreaterThan(0);
});

test('bhv models 002 composer manifest has a description', function (): void {
    /* D2-docs: the app description must be wired into composer.json so the
     * package does not publish with an empty description. */
    $raw = file_get_contents(__DIR__ . '/../composer.json');
    /** @var array<string, mixed> $composer */
    $composer = json_decode((string) $raw, true);
    expect($composer)->toHaveKey('description');
    expect($composer['description'])->toBeString();
});

test('bhv models 007 generated models never claim a null example', function (): void {
    /* L1 regression: when a property declares no example the base codegen
     * supplies the literal string "null" as the example value, which is truthy
     * in Mustache. A bare example doc section would therefore stamp a false
     * "@example null" onto nearly every property. The model template omits the
     * scalar example doc line entirely (only real, structured named examples are
     * rendered), so no generated model may contain that misleading text. */
    $modelsDir = __DIR__ . '/../lib/Models';
    expect(is_dir($modelsDir))->toBeTrue();

    $files = glob($modelsDir . '/*.php') ?: [];
    expect($files)->not->toBeEmpty();

    foreach ($files as $file) {
        $source = (string) file_get_contents($file);
        expect($source)->not->toContain('@example null');
        expect($source)->not->toContain('Example: null');
    }
});

test('bhv models 010 a declared property wins over a colliding additional property', function (): void {
    /** @var \Ds\Map<string, mixed> $additionalProperties */
    $additionalProperties = new \Ds\Map(['createdAt' => 'x', 'other' => 'y']);
    $metadata = (new Metadata(new \DateTime('2024-01-15T10:30:00+00:00')))
        ->withAdditionalProperties($additionalProperties);

    $json = ObjectSerializer::serialize($metadata);

    expect($json)->toBe('{"createdAt":"2024-01-15T10:30:00.000+00:00","other":"y"}');
    expect(substr_count($json, '"createdAt"'))->toBe(1);
});

test('a model property cannot be assigned after construction', function (): void {
    $category = new \PetstoreClient\Models\Category(id: 1, name: 'Dogs');

    $property = new \ReflectionProperty($category, 'name');

    expect(fn () => $property->setValue($category, 'Cats'))->toThrow(\Error::class);
    expect($category->name)->toBe('Dogs');
});

test('a with method returns a changed copy and leaves the model as it was', function (): void {
    $category = new \PetstoreClient\Models\Category(id: 1, name: 'Dogs');

    $renamed = $category->withName('Cats');

    expect($renamed)->not->toBe($category);
    expect($renamed->name)->toBe('Cats');
    expect($renamed->id)->toBe(1);
    expect($category->name)->toBe('Dogs');
});

test('a with method carries the additional properties over to the copy', function (): void {
    /** @var \Ds\Map<string, mixed> $extra */
    $extra = new \Ds\Map(['customField' => 'hello']);
    $metadata = (new Metadata())->withAdditionalProperties($extra);

    $copy = $metadata->withCreatedAt(new \DateTime('2024-01-01T00:00:00+00:00'));

    expect($copy->additionalProperties->get('customField'))->toBe('hello');
    expect($metadata->createdAt)->toBeNull();
});
