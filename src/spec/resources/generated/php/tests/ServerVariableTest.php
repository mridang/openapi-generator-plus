<?php

declare(strict_types=1);

use PetstoreClient\ServerVariable;

test('default value is exposed', function (): void {
    $variable = new ServerVariable(defaultValue: 'v3');

    expect($variable->defaultValue)->toBe('v3');
});

test('bhv servers 015 description defaults to null', function (): void {
    $variable = new ServerVariable(defaultValue: 'v3');

    expect($variable->description)->toBeNull();
});

test('bhv servers 016 enum values default to empty array', function (): void {
    $variable = new ServerVariable(defaultValue: 'v3');

    expect($variable->enumValues)->toBe([]);
});

test('bhv servers 017 exposes the default value, description and enum values', function (): void {
    $variable = new ServerVariable(
        defaultValue: 'v3',
        description: 'API version',
        enumValues: ['v2', 'v3'],
    );

    expect($variable->defaultValue)->toBe('v3');
    expect($variable->description)->toBe('API version');
    expect($variable->enumValues)->toBe(['v2', 'v3']);
});

test('bhv servers 018 enum values are exposed for validation', function (): void {
    $variable = new ServerVariable(defaultValue: 'v3', enumValues: ['v2', 'v3']);

    expect($variable->enumValues)->toContain('v2');
    expect($variable->enumValues)->toContain('v3');
});

test('bhv servers 019 enum does not contain a value outside the allowed set', function (): void {
    $variable = new ServerVariable(defaultValue: 'v3', enumValues: ['v2', 'v3']);

    expect($variable->enumValues)->not->toContain('v9');
});

test('bhv servers 024 the enum values cannot be mutated', function (): void {
    $variable = new ServerVariable(defaultValue: 'v3', enumValues: ['v2', 'v3']);

    expect(fn (): mixed => $variable->enumValues[] = 'v4')->toThrow(\Error::class);
});

test('bhv servers 026 changing the allowed values list after building leaves them unchanged', function (): void {
    $values = ['v2', 'v3'];
    $variable = new ServerVariable(defaultValue: 'v3', enumValues: $values);

    $values[] = 'v9';

    expect($variable->enumValues)->toBe(['v2', 'v3']);
});

test('bhv servers 027 two variables with the same default, description and values are equal', function (): void {
    $first = new ServerVariable(defaultValue: 'v3', description: 'API version', enumValues: ['v2', 'v3']);
    $second = new ServerVariable(defaultValue: 'v3', description: 'API version', enumValues: ['v2', 'v3']);

    expect($first == $second)->toBeTrue();
});
