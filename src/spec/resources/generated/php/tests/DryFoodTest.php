<?php

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Models\DryFood;
use PetstoreClient\ObjectSerializer;

/**
 * Tests for the DryFood model.
 */
class DryFoodTest extends TestCase
{
    /**
     * DryFood without the required 'foodType' field should fail.
     * The constructor enforces 'foodType' as a required parameter.
     */
    public function testRequiresFoodTypeField(): void
    {
        $this->expectException(\TypeError::class);
        /** @phpstan-ignore-next-line */
        $food = new DryFood(weightKg: 2.5);
    }

    /**
     * DryFood without the required 'weightKg' field should fail.
     * The constructor enforces 'weightKg' as a required parameter.
     */
    public function testRequiresWeightKgField(): void
    {
        $this->expectException(\TypeError::class);
        /** @phpstan-ignore-next-line */
        $food = new DryFood(foodType: 'kibble');
    }

    /**
     * A DryFood should survive a JSON serialization round-trip.
     */
    public function testSerializesToJson(): void
    {
        $food = new DryFood(
            foodType: 'kibble',
            weightKg: 2.5,
        );

        $json = ObjectSerializer::serialize($food);
        self::assertJson($json);

        $decoded = json_decode($json, true);
        self::assertIsArray($decoded);
        self::assertSame('kibble', $decoded['foodType']);
        self::assertSame(2.5, $decoded['weightKg']);
    }
}
