<?php

declare(strict_types=1);

use PetstoreClient\ServerConfiguration;
use PetstoreClient\ServerVariable;

test('plain url template resolves unchanged', function (): void {
    $server = new ServerConfiguration(urlTemplate: 'https://api.example.com');

    expect($server->getUrl())->toBe('https://api.example.com');
});

test('variables resolve to their default values', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com/api/{version}',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api', enumValues: ['api', 'staging']),
            'version' => new ServerVariable(defaultValue: 'v3', enumValues: ['v2', 'v3']),
        ],
    );

    expect($server->getUrl())->toBe('https://api.example.com/api/v3');
});

test('overrides substitute matching variables', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com/api/{version}',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api', enumValues: ['api', 'staging']),
            'version' => new ServerVariable(defaultValue: 'v3', enumValues: ['v2', 'v3']),
        ],
    );

    expect($server->getUrl(['env' => 'staging', 'version' => 'v2']))
        ->toBe('https://staging.example.com/api/v2');
});

test('partial override keeps remaining defaults', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com/api/{version}',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api', enumValues: ['api', 'staging']),
            'version' => new ServerVariable(defaultValue: 'v3', enumValues: ['v2', 'v3']),
        ],
    );

    expect($server->getUrl(['env' => 'staging']))
        ->toBe('https://staging.example.com/api/v3');
});

test('override not in enum is rejected', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api', enumValues: ['api', 'staging']),
        ],
    );

    // A server variable outside its enum is a caller mistake, not an SDK error.
    expect(fn () => $server->getUrl(['env' => 'invalid']))
        ->toThrow(function (\Exception $e): void {
            expect($e::class)->toBe(\InvalidArgumentException::class);
            expect($e)->not->toBeInstanceOf(\PetstoreClient\Errors\OpenAPIException::class);
        });
});

test('unconstrained variable accepts any override', function (): void {
    // An empty enum array means any value is accepted.
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api'),
        ],
    );

    expect($server->getUrl(['env' => 'anything']))->toBe('https://anything.example.com');
});

test('exposes constructor fields', function (): void {
    $variables = ['env' => new ServerVariable(defaultValue: 'api')];
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        description: 'Primary server',
        variables: $variables,
    );

    expect($server->urlTemplate)->toBe('https://{env}.example.com');
    expect($server->description)->toBe('Primary server');
    expect($server->variables)->toBe($variables);
});

test('rejected override message names the value, the variable and the allowed values', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api', enumValues: ['api', 'staging']),
        ],
    );

    expect(fn () => $server->getUrl(['env' => 'invalid']))
        ->toThrow(
            \InvalidArgumentException::class,
            "Invalid value 'invalid' for server variable 'env'. Allowed values: api, staging"
        );
});

test('default value outside the enum is rejected', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(defaultValue: 'dev', enumValues: ['api', 'staging']),
        ],
    );

    expect(fn () => $server->getUrl())
        ->toThrow(
            \InvalidArgumentException::class,
            "Invalid value 'dev' for server variable 'env'. Allowed values: api, staging"
        );
});

test('a variable repeated in the template is replaced everywhere', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{region}.example.com/{region}/v1',
        variables: [
            'region' => new ServerVariable(defaultValue: 'us', enumValues: ['us', 'eu']),
        ],
    );

    expect($server->getUrl())->toBe('https://us.example.com/us/v1');
    expect($server->getUrl(['region' => 'eu']))->toBe('https://eu.example.com/eu/v1');
});
