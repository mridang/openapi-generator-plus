<?php

declare(strict_types=1);

use PetstoreClient\Models\DryFood;
use PetstoreClient\Models\Medication;
use PetstoreClient\Models\PetFood;
use PetstoreClient\Models\PetTreatment;
use PetstoreClient\Models\PetWithOwner;
use PetstoreClient\Models\Surgery;
use PetstoreClient\Models\WetFood;
use PetstoreClient\ObjectSerializer;

// -- oneOf with discriminator: PetFood --

test('one of deserializes dry food', function (): void {
    $data = ['foodType' => 'dry', 'weightKg' => 2.5];
    $result = PetFood::build($data);
    expect($result->getActualInstance())->toBeInstanceOf(DryFood::class);
    expect($result->getActualInstance()->foodType)->toBe('dry');
});

test('one of deserializes wet food', function (): void {
    $data = ['foodType' => 'wet', 'volumeMl' => 400];
    $result = PetFood::build($data);
    expect($result->getActualInstance())->toBeInstanceOf(WetFood::class);
    expect($result->getActualInstance()->foodType)->toBe('wet');
});

test('one of missing discriminator throws', function (): void {
    // Gap AU: missing discriminator field must throw, not silently wrap.
    $data = ['weightKg' => 2.5];
    expect(fn () => PetFood::build($data))
        ->toThrow(\InvalidArgumentException::class, 'Missing discriminator');
});

test('one of empty discriminator throws', function (): void {
    // Gap AU: empty discriminator value must throw.
    $data = ['foodType' => '', 'weightKg' => 2.5];
    expect(fn () => PetFood::build($data))->toThrow(\InvalidArgumentException::class);
});

test('one of unknown discriminator throws', function (): void {
    // Gap AU: unknown discriminator value must throw.
    $data = ['foodType' => 'raw', 'calories' => 300];
    expect(fn () => PetFood::build($data))
        ->toThrow(\InvalidArgumentException::class, 'Unknown discriminator');
});

test('one of serializes dry food', function (): void {
    $data = ['foodType' => 'dry', 'weightKg' => 2.5];
    $result = PetFood::build($data);
    $serialized = json_encode(ObjectSerializer::serialize($result->getActualInstance()));
    expect($serialized)->not->toBeFalse();
    expect($serialized)->toContain('dry');
});

// -- anyOf without discriminator: PetTreatment --

test('any of deserializes medication', function (): void {
    $data = ['drugName' => 'Amoxicillin', 'dosage' => '500mg'];
    $result = PetTreatment::build($data);
    expect($result->getActualInstance())->toBeInstanceOf(Medication::class);
    expect($result->getActualInstance()->drugName)->toBe('Amoxicillin');
});

test('any of deserializes surgery', function (): void {
    $data = ['procedureName' => 'Spay', 'durationMinutes' => 45];
    $result = PetTreatment::build($data);
    expect($result->getActualInstance())->toBeInstanceOf(Surgery::class);
});

test('any of serialize round trip', function (): void {
    $data = ['drugName' => 'Amoxicillin', 'dosage' => '500mg'];
    $result = PetTreatment::build($data);
    $serialized = json_encode(ObjectSerializer::serialize($result->getActualInstance()));
    expect($serialized)->not->toBeFalse();
    expect($serialized)->not->toBeEmpty();
});

// -- allOf: PetWithOwner --

test('all of deserializes pet with owner', function (): void {
    $json = '{"name":"doggie","photoUrls":["http://example.com/photo.jpg"],'
        . '"ownerName":"John","ownerEmail":"john@example.com"}';
    /** @var PetWithOwner $result */
    $result = ObjectSerializer::deserialize($json, PetWithOwner::class);
    expect($result)->toBeInstanceOf(PetWithOwner::class);
    expect($result->name)->toBe('doggie');
    expect($result->ownerName)->toBe('John');
    expect($result->ownerEmail)->toBe('john@example.com');
});

test('all of serializes pet with owner', function (): void {
    $json = '{"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"}';
    /** @var PetWithOwner $result */
    $result = ObjectSerializer::deserialize($json, PetWithOwner::class);
    $serialized = json_encode(ObjectSerializer::serialize($result));
    expect($serialized)->not->toBeFalse();
    expect($serialized)->toContain('Fido');
    expect($serialized)->toContain('John Doe');
});

test('all of round trip', function (): void {
    $json = '{"name":"Buddy","photoUrls":["http://example.com/buddy.jpg"],"ownerName":"Jane Smith"}';
    /** @var PetWithOwner $original */
    $original = ObjectSerializer::deserialize($json, PetWithOwner::class);
    $serialized = json_encode(ObjectSerializer::serialize($original));
    /** @var PetWithOwner $restored */
    $restored = ObjectSerializer::deserialize($serialized, PetWithOwner::class);
    expect($restored->name)->toBe($original->name);
    expect($restored->ownerName)->toBe($original->ownerName);
});
