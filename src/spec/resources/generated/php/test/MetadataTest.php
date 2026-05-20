<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ObjectSerializer;
use PetstoreClient\Models\Metadata;

class MetadataTest extends TestCase
{
    public function testSerializesEmptyMetadata(): void
    {
        $metadata = new Metadata();

        $json = ObjectSerializer::serialize($metadata);

        $this->assertNotEmpty($json);
    }

    public function testDeserializesEmptyObject(): void
    {
        /** @var Metadata $metadata */
        $metadata = ObjectSerializer::deserialize('{}', Metadata::class);

        $this->assertInstanceOf(Metadata::class, $metadata);
    }

    public function testDeserializesAdditionalStringProperties(): void
    {
        $json = '{"createdAt":"2024-01-01T00:00:00+00:00","customField":"hello"}';
        /** @var Metadata $metadata */
        $metadata = ObjectSerializer::deserialize($json, Metadata::class);
        $this->assertInstanceOf(Metadata::class, $metadata);
        $this->assertArrayHasKey('customField', $metadata->additionalProperties);
        $this->assertSame('hello', $metadata->additionalProperties['customField']);
    }

    public function testRoundTripPreservesAdditionalProperties(): void
    {
        $metadata = new Metadata();
        $metadata->createdAt = new \DateTime('2024-01-01T00:00:00+00:00');
        $metadata->additionalProperties = ['customField' => 'hello', 'anotherField' => 'world'];

        $json = ObjectSerializer::serialize($metadata);
        /** @var Metadata $deserialized */
        $deserialized = ObjectSerializer::deserialize($json, Metadata::class);
        $this->assertInstanceOf(Metadata::class, $deserialized);
    }

    public function testAdditionalPropertiesFieldIsStringTyped(): void
    {
        $metadata = new Metadata();
        $metadata->additionalProperties = ['key' => 'value'];
        $this->assertIsArray($metadata->additionalProperties);
        $this->assertSame('value', $metadata->additionalProperties['key']);
    }
}
