<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ObjectSerializer;
use PetstoreClient\Models\DryFood;
use PetstoreClient\Models\WetFood;
use PetstoreClient\Models\Medication;
use PetstoreClient\Models\Surgery;
use PetstoreClient\Models\PetFood;
use PetstoreClient\Models\PetTreatment;
use PetstoreClient\Models\PetWithOwner;

class ComposedSchemaTest extends TestCase
{
    // -- oneOf with discriminator: PetFood --

    public function testOneOfDeserializesDryFood(): void
    {
        $data = ['foodType' => 'dry', 'weightKg' => 2.5];
        $result = PetFood::build($data);
        $this->assertInstanceOf(DryFood::class, $result->getActualInstance());
        $this->assertSame('dry', $result->getActualInstance()->foodType);
    }

    public function testOneOfDeserializesWetFood(): void
    {
        $data = ['foodType' => 'wet', 'volumeMl' => 400];
        $result = PetFood::build($data);
        $this->assertInstanceOf(WetFood::class, $result->getActualInstance());
        $this->assertSame('wet', $result->getActualInstance()->foodType);
    }

    public function testOneOfMissingDiscriminatorThrows(): void
    {
        // Gap AU: missing discriminator field must throw, not silently wrap.
        $data = ['weightKg' => 2.5];
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessageMatches('/Missing discriminator/');
        PetFood::build($data);
    }

    public function testOneOfEmptyDiscriminatorThrows(): void
    {
        // Gap AU: empty discriminator value must throw.
        $data = ['foodType' => '', 'weightKg' => 2.5];
        $this->expectException(\InvalidArgumentException::class);
        PetFood::build($data);
    }

    public function testOneOfUnknownDiscriminatorThrows(): void
    {
        // Gap AU: unknown discriminator value must throw.
        $data = ['foodType' => 'raw', 'calories' => 300];
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessageMatches('/Unknown discriminator/');
        PetFood::build($data);
    }

    public function testOneOfSerializesDryFood(): void
    {
        $data = ['foodType' => 'dry', 'weightKg' => 2.5];
        $result = PetFood::build($data);
        $serialized = json_encode(ObjectSerializer::serialize($result->getActualInstance()));
        $this->assertNotFalse($serialized);
        $this->assertStringContainsString('dry', $serialized);
    }

    // -- anyOf without discriminator: PetTreatment --

    public function testAnyOfDeserializesMedication(): void
    {
        $data = ['drugName' => 'Amoxicillin', 'dosage' => '500mg'];
        $result = PetTreatment::build($data);
        $this->assertInstanceOf(Medication::class, $result->getActualInstance());
        $this->assertSame('Amoxicillin', $result->getActualInstance()->drugName);
    }

    public function testAnyOfDeserializesSurgery(): void
    {
        $data = ['procedureName' => 'Spay', 'durationMinutes' => 45];
        $result = PetTreatment::build($data);
        $this->assertInstanceOf(Surgery::class, $result->getActualInstance());
    }

    public function testAnyOfSerializeRoundTrip(): void
    {
        $data = ['drugName' => 'Amoxicillin', 'dosage' => '500mg'];
        $result = PetTreatment::build($data);
        $serialized = json_encode(ObjectSerializer::serialize($result->getActualInstance()));
        $this->assertNotFalse($serialized);
        $this->assertNotEmpty($serialized);
    }

    // -- allOf: PetWithOwner --

    public function testAllOfDeserializesPetWithOwner(): void
    {
        $json = '{"name":"doggie","photoUrls":["http://example.com/photo.jpg"],'
            . '"ownerName":"John","ownerEmail":"john@example.com"}';
        /** @var PetWithOwner $result */
        $result = ObjectSerializer::deserialize($json, PetWithOwner::class);
        $this->assertInstanceOf(PetWithOwner::class, $result);
        $this->assertSame('doggie', $result->name);
        $this->assertSame('John', $result->ownerName);
        $this->assertSame('john@example.com', $result->ownerEmail);
    }

    public function testAllOfSerializesPetWithOwner(): void
    {
        $json = '{"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"}';
        /** @var PetWithOwner $result */
        $result = ObjectSerializer::deserialize($json, PetWithOwner::class);
        $serialized = json_encode(ObjectSerializer::serialize($result));
        $this->assertNotFalse($serialized);
        $this->assertStringContainsString('Fido', $serialized);
        $this->assertStringContainsString('John Doe', $serialized);
    }

    public function testAllOfRoundTrip(): void
    {
        $json = '{"name":"Buddy","photoUrls":["http://example.com/buddy.jpg"],"ownerName":"Jane Smith"}';
        /** @var PetWithOwner $original */
        $original = ObjectSerializer::deserialize($json, PetWithOwner::class);
        $serialized = json_encode(ObjectSerializer::serialize($original));
        /** @var PetWithOwner $restored */
        $restored = ObjectSerializer::deserialize($serialized, PetWithOwner::class);
        $this->assertSame($original->name, $restored->name);
        $this->assertSame($original->ownerName, $restored->ownerName);
    }
}
