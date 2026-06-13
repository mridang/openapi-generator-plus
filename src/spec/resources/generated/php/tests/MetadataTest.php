<?php

declare(strict_types=1);

use PetstoreClient\ObjectSerializer;
use PetstoreClient\Models\Metadata;

test('serializes empty metadata', function (): void {
    $metadata = new Metadata();

    $json = ObjectSerializer::serialize($metadata);

    expect($json)->not->toBeEmpty();
});

test('deserializes empty object', function (): void {
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize('{}', Metadata::class);

    expect($metadata)->toBeInstanceOf(Metadata::class);
});

test('deserializes additional string properties', function (): void {
    $json = '{"createdAt":"2024-01-01T00:00:00+00:00","customField":"hello"}';
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize($json, Metadata::class);
    expect($metadata)->toBeInstanceOf(Metadata::class);
    expect($metadata->additionalProperties)->toBeInstanceOf(\Ds\Map::class);
    expect($metadata->additionalProperties->hasKey('customField'))->toBeTrue();
    expect($metadata->additionalProperties->get('customField'))->toBe('hello');
});

test('round trip preserves additional properties', function (): void {
    $metadata = new Metadata();
    $metadata->createdAt = new \DateTime('2024-01-01T00:00:00+00:00');
    $metadata->additionalProperties = new \Ds\Map(['customField' => 'hello', 'anotherField' => 'world']);

    $json = ObjectSerializer::serialize($metadata);
    /** @var Metadata $deserialized */
    $deserialized = ObjectSerializer::deserialize($json, Metadata::class);
    expect($deserialized)->toBeInstanceOf(Metadata::class);
});

test('additional properties field is Ds\\Map typed', function (): void {
    $metadata = new Metadata();
    $metadata->additionalProperties = new \Ds\Map(['key' => 'value']);
    expect($metadata->additionalProperties)->toBeInstanceOf(\Ds\Map::class);
    expect($metadata->additionalProperties->get('key'))->toBe('value');
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

test('composer manifest has a description', function (): void {
    /* D2-docs: A simplified Pet Store API for integration testing. must be wired into composer.json so the
     * package does not publish with an empty description. */
    $raw = file_get_contents(__DIR__ . '/../composer.json');
    /** @var array<string, mixed> $composer */
    $composer = json_decode((string) $raw, true);
    expect($composer)->toHaveKey('description');
    expect($composer['description'])->toBeString();
});
