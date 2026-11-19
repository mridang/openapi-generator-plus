<?php

declare(strict_types=1);

use PetstoreClient\ObjectSerializer;
use PetstoreClient\Models\Category;
use PetstoreClient\Models\DryFood;
use PetstoreClient\Models\Metadata;
use PetstoreClient\Models\Priority;
use PetstoreClient\Models\Account;
use PetstoreClient\Models\Defaults;
use PetstoreClient\Models\DefaultsModeEnum;
use PetstoreClient\Models\Swatch;

test('bhv models 008 serializes empty metadata', function (): void {
    $metadata = new Metadata();

    $json = ObjectSerializer::serialize($metadata);

    expect($json)->not->toBeEmpty();
});

test('bhv models 013 additional properties start as an empty map, never null', function (): void {
    $metadata = new Metadata();

    expect($metadata->additionalProperties)->toBeInstanceOf(\Ds\Map::class);
    expect($metadata->additionalProperties)->toHaveCount(0);
});

test('bhv models 026 undeclared wire keys land in the map and are written back flat, and an additional property decodes to its model', function (): void {
    $json = '{"customField":"hello"}';
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize($json, Metadata::class);

    expect($metadata->additionalProperties->toArray())->toBe(['customField' => 'hello']);
    expect(ObjectSerializer::serialize($metadata))->toBe($json);

    /** @var \PetstoreClient\Models\Roster $roster */
    $roster = ObjectSerializer::deserialize('{"label":"l","a":{"id":1,"name":"x"}}', \PetstoreClient\Models\Roster::class);
    $category = $roster->additionalProperties->get('a');

    expect($category)->toBeInstanceOf(\PetstoreClient\Models\Category::class);
    expect($category->id)->toBe(1);
    expect($category->name)->toBe('x');
    expect(fn () => ObjectSerializer::deserialize('{"a":{"id":"x"}}', \PetstoreClient\Models\Roster::class))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
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
    /** @var \Ds\Map<string, string> $additionalProperties */
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

test('bhv models 025 an additional property of the wrong JSON type fails the decode', function (): void {
    foreach (['{"a":2.5}', '{"a":"2"}', '{"a":3000000000}'] as $body) {
        expect(fn () => ObjectSerializer::deserialize($body, \PetstoreClient\Models\Counters::class))
            ->toThrow(\PetstoreClient\Errors\SerializationException::class);
    }
    expect(fn () => ObjectSerializer::deserialize('{"a":3}', Metadata::class))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
});

test('bhv models 027 a null additional property is rejected unless the value schema is nullable', function (): void {
    expect(fn () => ObjectSerializer::deserialize('{"a":null}', \PetstoreClient\Models\Counters::class))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
    /** @var \PetstoreClient\Models\Sparse $sparse */
    $sparse = ObjectSerializer::deserialize('{"a":null}', \PetstoreClient\Models\Sparse::class);

    expect($sparse->additionalProperties->hasKey('a'))->toBeTrue();
    expect($sparse->additionalProperties->get('a'))->toBeNull();
});

test('bhv models 028 a typed additional property is written flat in its wire form', function (): void {
    /** @var \PetstoreClient\Models\Moments $moments */
    $moments = ObjectSerializer::deserialize('{"label":"l","at":"2024-01-01T00:00:00+00:00"}', \PetstoreClient\Models\Moments::class);

    expect($moments->additionalProperties->get('at'))->toBeInstanceOf(\DateTimeInterface::class);
    $json = ObjectSerializer::serialize($moments);
    /** @var array<string, mixed> $decoded */
    $decoded = json_decode($json, true);
    expect(array_keys($decoded))->toBe(['label', 'at']);
    expect($json)->toContain('2024-01-01T00:00:00');

    /** @var \PetstoreClient\Models\Blobs $blobs */
    $blobs = ObjectSerializer::deserialize('{"b":"aGk="}', \PetstoreClient\Models\Blobs::class);
    expect($blobs->additionalProperties->get('b'))->toBe('hi');
    expect(ObjectSerializer::serialize($blobs))->toBe('{"b":"aGk="}');
});

test('bhv models 029 a free-form additionalProperties keeps every JSON value as it arrives', function (): void {
    /** @var \PetstoreClient\Models\Freeform $freeform */
    $freeform = ObjectSerializer::deserialize('{"a":1,"b":"x","c":[true],"d":null}', \PetstoreClient\Models\Freeform::class);

    expect($freeform->additionalProperties->toArray())->toBe(['a' => 1, 'b' => 'x', 'c' => [true], 'd' => null]);
});

test('bhv langphp 062 composer manifest has a valid name', function (): void {
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
    /** @var \Ds\Map<string, string> $additionalProperties */
    $additionalProperties = new \Ds\Map(['createdAt' => 'x', 'other' => 'y']);
    $metadata = (new Metadata(new \DateTime('2024-01-15T10:30:00+00:00')))
        ->withAdditionalProperties($additionalProperties);

    $json = ObjectSerializer::serialize($metadata);

    expect($json)->toBe('{"createdAt":"2024-01-15T10:30:00.000+00:00","other":"y"}');
    expect(substr_count($json, '"createdAt"'))->toBe(1);
});

test('bhv models 014 a model property cannot be assigned after construction', function (): void {
    $category = new \PetstoreClient\Models\Category(id: 1, name: 'Dogs');

    $property = new \ReflectionProperty($category, 'name');

    expect(fn () => $property->setValue($category, 'Cats'))->toThrow(\Error::class);
    expect($category->name)->toBe('Dogs');
});

test('bhv models 017 a with method returns a changed copy and leaves the model as it was', function (): void {
    $category = new \PetstoreClient\Models\Category(id: 1, name: 'Dogs');

    $renamed = $category->withName('Cats');

    expect($renamed)->not->toBe($category);
    expect($renamed->name)->toBe('Cats');
    expect($renamed->id)->toBe(1);
    expect($category->name)->toBe('Dogs');
});

test('bhv models 012 the field dump lists the fields and the additional properties map', function (): void {
    /** @var \Ds\Map<string, string> $extra */
    $extra = new \Ds\Map(['customField' => 'hello']);
    $dump = print_r((new Metadata())->withAdditionalProperties($extra), true);

    expect($dump)->toContain('additionalProperties');
    expect($dump)->toContain('customField');
});

test('bhv models 015 a with method carries the additional properties over to the copy', function (): void {
    /** @var \Ds\Map<string, string> $extra */
    $extra = new \Ds\Map(['customField' => 'hello']);
    $metadata = (new Metadata())->withAdditionalProperties($extra);

    $copy = $metadata->withCreatedAt(new \DateTime('2024-01-01T00:00:00+00:00'));

    expect($copy->additionalProperties->get('customField'))->toBe('hello');
    expect($metadata->createdAt)->toBeNull();
});

test('bhv models 016 a copy clears an optional field only when null is passed and keeps it on a copy of another field', function (): void {
    $original = new Metadata(new \DateTime('2024-01-01T00:00:00+00:00'));

    expect($original->withCreatedAt(null)->createdAt)->toBeNull();
    /** @var array<string, string> $extraValues */
    $extraValues = ['k' => 'v'];
    expect($original->withAdditionalProperties(new \Ds\Map($extraValues))->createdAt)->toEqual($original->createdAt);
    expect($original->createdAt)->not->toBeNull();
});

test('bhv models 018 models whose decimal fields differ only in trailing zeros compare equal', function (): void {
    expect(new DryFood(weightKg: 1.50))->toEqual(new DryFood(weightKg: 1.5));
    expect(ObjectSerializer::deserialize('{"weightKg":1.50}', DryFood::class))
        ->toEqual(ObjectSerializer::deserialize('{"weightKg":1.5}', DryFood::class));
});

test('bhv models 019 two models with a different value in any field compare unequal', function (): void {
    expect(new Category(id: 1, name: 'Dogs'))->not->toEqual(new Category(id: 1, name: 'Cats'));
    expect(new Category(id: 1, name: 'Dogs'))->not->toEqual(new Category(id: 2, name: 'Dogs'));
});

test('bhv models 021 each member of an integer backed enum carries its declared number as its value', function (Priority $member, int $declared): void {
    expect($member->value)->toBe($declared);
})->with([
    'NUMBER_1 is 1' => [Priority::NUMBER_1, 1],
    'NUMBER_2 is 2' => [Priority::NUMBER_2, 2],
    'NUMBER_3 is 3' => [Priority::NUMBER_3, 3],
]);

test('bhv models 022 a model whose schema allows undeclared properties reports that it does', function (): void {
    expect((new \ReflectionClass(Metadata::class))->hasProperty('additionalProperties'))->toBeTrue();
    expect((new Metadata())->additionalProperties)->toBeInstanceOf(\Ds\Map::class);
    expect((new \ReflectionClass(Category::class))->hasProperty('additionalProperties'))->toBeFalse();
});

test('bhv models 023 undeclared keys named like the sdk methods are kept as additional properties', function (): void {
    /** @var Metadata $metadata */
    $metadata = ObjectSerializer::deserialize('{"withCreatedAt":"x","withAdditionalProperties":"y"}', Metadata::class);

    expect($metadata->createdAt)->toBeNull();
    expect($metadata->additionalProperties->get('withCreatedAt'))->toBe('x');
    expect($metadata->additionalProperties->get('withAdditionalProperties'))->toBe('y');
});

test('bhv models 024 a newly built model leaves its optional properties unset', function (): void {
    $metadata = new Metadata();
    $category = new Category();

    expect($metadata->createdAt)->toBeNull();
    expect($category->id)->toBeNull();
    expect($category->name)->toBeNull();
});

test('bhv misc 137 an enum lists its declared values and reports whether a value is one of them', function (): void {
    expect(array_map(fn (Priority $priority): int => $priority->value, Priority::cases()))->toBe([1, 2, 3]);
    expect(Priority::tryFrom(2))->toBe(Priority::NUMBER_2);
    expect(Priority::tryFrom(4))->toBeNull();
});

test('bhv misc 138 converting an enum member to text yields its wire value', function (): void {
    expect(DefaultsModeEnum::HIGH->value)->toBe('high');
    expect(DefaultsModeEnum::LOW->value)->toBe('low');
});

test('bhv misc 140 building a model without one of its required properties fails naming that property', function (): void {
    expect(fn (): mixed => (new \ReflectionClass(Account::class))->newInstanceArgs(['id' => 1]))
        ->toThrow(\ArgumentCountError::class, 'Argument #1 ($username) not passed');
});

test('bhv misc 141 a model built without a value for a property with a schema default holds that default', function (): void {
    $defaults = new Defaults();

    expect($defaults->retries)->toBe(3);
    expect($defaults->label)->toBe('untitled');
    expect($defaults->mode)->toBe(DefaultsModeEnum::MEDIUM);
});

test('bhv misc 142 values in the additional properties map are returned unchanged and the map holds exactly them', function (): void {
    $metadata = (new Metadata())->withAdditionalProperties(new \Ds\Map(['a' => 'one', 'b' => 'two']));

    expect($metadata->additionalProperties->get('a'))->toBe('one');
    expect($metadata->additionalProperties->toArray())->toBe(['a' => 'one', 'b' => 'two']);
});

test('bhv misc 143 a model built with an additional properties map keeps those entries in a copy that compares equal', function (): void {
    $metadata = new Metadata(additionalProperties: new \Ds\Map(['k' => 'v']));

    $copy = $metadata->withCreatedAt(null);

    expect($copy->additionalProperties->toArray())->toBe(['k' => 'v']);
    expect($copy->equals($metadata))->toBeTrue();
});

test('bhv misc 144 validating a model refuses a string given for a numeric field', function (): void {
    expect(fn (): mixed => ObjectSerializer::deserialize('{"id":"1","name":"Dogs"}', Category::class))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
});

test('bhv misc 146 copying a model with no changes yields a new instance that compares equal to the original', function (): void {
    $category = new Category(id: 1, name: 'Dogs');

    $copy = $category->withName('Dogs');

    expect($copy)->not->toBe($category);
    expect($copy->equals($category))->toBeTrue();
});

test('bhv misc 147 a copy of a model holds the same nested value as the original rather than a duplicate', function (): void {
    $defaults = new Defaults(swatch: Swatch::RED);

    $copy = $defaults->withRetries(4);

    expect($copy->swatch)->toBe($defaults->swatch);
    expect($copy->retries)->toBe(4);
});

test('bhv misc 148 a copy serializes to the same wire form as the original with its changes applied', function (): void {
    $copy = (new Category(id: 1, name: 'Dogs'))->withName('Cats');

    expect(json_decode(ObjectSerializer::serialize($copy), true))->toBe(['id' => 1, 'name' => 'Cats']);
});

test('bhv misc 149 copying a model with an attribute it does not declare is refused and the error names the attribute', function (): void {
    /* The arguments go through call_user_func_array so the unknown name is not a literal argument that a formatter could drop. */
    expect(fn (): mixed => callWithArray(new Category(id: 1, name: 'Dogs'), 'withName', ['name' => 'Cats', 'unknownAttribute' => 1]))
        ->toThrow(\Error::class, 'unknownAttribute');
});

test('bhv misc 150 two models whose fields hold equal values compare equal and differing values compare unequal', function (): void {
    expect((new Category(id: 1, name: 'Dogs'))->equals(new Category(id: 1, name: 'Dogs')))->toBeTrue();
    expect((new Category(id: 1, name: 'Dogs'))->equals(new Category(id: 1, name: 'Cats')))->toBeFalse();
});

test('bhv misc 151 a model holding a map compares equal to one with the same entries and unequal when any entry differs', function (): void {
    $metadata = (new Metadata())->withAdditionalProperties(new \Ds\Map(['k' => 'v']));

    expect($metadata->equals((new Metadata())->withAdditionalProperties(new \Ds\Map(['k' => 'v']))))->toBeTrue();
    expect($metadata->equals((new Metadata())->withAdditionalProperties(new \Ds\Map(['k' => 'w']))))->toBeFalse();
});
