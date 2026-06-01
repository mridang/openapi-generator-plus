<?php

declare(strict_types=1);

use PetstoreClient\Models\Metadata;
use PetstoreClient\ObjectSerializer;

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
