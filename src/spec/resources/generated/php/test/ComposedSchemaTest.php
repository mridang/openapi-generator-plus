<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ObjectSerializer;
use PetstoreClient\Models\DryFood;
use PetstoreClient\Models\Medication;
use PetstoreClient\Models\PetFood;
use PetstoreClient\Models\PetTreatment;
use PetstoreClient\Models\PetWithOwner;

class ComposedSchemaTest extends TestCase
{
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

    public function testOneOfWithDiscriminatorDeserializesDryFood(): void
    {
        $data = ['foodType' => 'dry', 'weightKg' => 2.5];
        $result = PetFood::build($data);
        $this->assertInstanceOf(DryFood::class, $result->getActualInstance());
        $this->assertSame('dry', $result->getActualInstance()->foodType);
    }

    public function testAnyOfDeserializesMedication(): void
    {
        $data = ['drugName' => 'Amoxicillin', 'dosage' => '500mg'];
        $result = PetTreatment::build($data);
        $this->assertInstanceOf(Medication::class, $result->getActualInstance());
        $this->assertSame('Amoxicillin', $result->getActualInstance()->drugName);
    }
}
