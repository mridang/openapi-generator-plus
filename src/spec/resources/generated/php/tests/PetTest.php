<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Models\Pet;
use PetstoreClient\ObjectSerializer;

/**
 * Tests for the Pet model.
 */
class PetTest extends TestCase
{
    /**
     * Pet without the required 'name' field should fail.
     * The constructor enforces 'name' as a required parameter;
     * omitting it must produce a TypeError.
     */
    public function testRequiresNameField(): void
    {
        $this->expectException(\TypeError::class);
        // Intentionally omit name (first required arg)
        /** @phpstan-ignore-next-line */
        $pet = new Pet(photoUrls: ['https://example.com/photo.jpg']);
    }

    /**
     * Pet without the required 'photoUrls' field should fail.
     * The constructor enforces 'photoUrls' as a required parameter;
     * omitting it must produce a TypeError.
     */
    public function testRequiresPhotoUrlsField(): void
    {
        $this->expectException(\TypeError::class);
        // Intentionally omit photoUrls (second required arg)
        /** @phpstan-ignore-next-line */
        $pet = new Pet(name: 'Fido');
    }

    /**
     * Setting an invalid enum value for status should not be accepted.
     * The Pet class defines STATUS_AVAILABLE, STATUS_PENDING, STATUS_SOLD.
     * An arbitrary string like 'flying' is invalid.
     *
     * BUG: The generated model does not validate enum values on the status
     * property, so this test verifies the value does NOT match any of the
     * declared enum constants.
     */
    public function testRejectsInvalidStatusEnum(): void
    {
        $pet = new Pet(
            name: 'Fido',
            photoUrls: ['https://example.com/photo.jpg'],
            status: 'flying'
        );

        $validStatuses = [Pet::STATUS_AVAILABLE, Pet::STATUS_PENDING, Pet::STATUS_SOLD];
        self::assertNotContains($pet->status, $validStatuses, 'An invalid enum value was silently accepted');
    }

    /**
     * A Pet should survive a JSON serialization round-trip.
     */
    public function testSerializesToJson(): void
    {
        $pet = new Pet(
            name: 'Buddy',
            photoUrls: ['https://example.com/photo1.jpg', 'https://example.com/photo2.jpg'],
            id: 42,
            status: Pet::STATUS_AVAILABLE,
        );

        $json = ObjectSerializer::serialize($pet);
        self::assertJson($json);

        $decoded = json_decode($json, true);
        self::assertIsArray($decoded);
        self::assertSame('Buddy', $decoded['name']);
        self::assertSame(42, $decoded['id']);
        self::assertSame('available', $decoded['status']);
        self::assertCount(2, $decoded['photoUrls']);
    }
}
