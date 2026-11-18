<?php

declare(strict_types=1);

use PetstoreClient\ServerConfiguration;
use PetstoreClient\Servers;
use PetstoreClient\ServerVariable;

test('bhv servers 013 the third global server keeps its hostile text unchanged', function (): void {
    $server = Servers::all()[2];

    expect($server->urlTemplate)->toBe('https://{tenant}.example.com/api');
    expect($server->description)->toBe('Bob\'s "sandbox" & co: $5 #{x} a*/b');
    $tenant = $server->variables['tenant'];
    expect($tenant->defaultValue)->toBe('o\'neil');
    expect($tenant->description)->toBe('Tenant\'s "id"');
    expect($tenant->enumValues)->toBe(['o\'neil', 'a&b']);
});

test('bhv servers 021 the third global server resolves to the raw characters', function (): void {
    $server = Servers::all()[2];

    expect($server->getUrl())->toBe('https://o\'neil.example.com/api');
    expect($server->getUrl(['tenant' => 'a&b']))->toBe('https://a&b.example.com/api');
});

test('bhv servers 011 plain url template resolves unchanged', function (): void {
    $server = new ServerConfiguration(urlTemplate: 'https://api.example.com');

    expect($server->getUrl())->toBe('https://api.example.com');
});

test('bhv servers 010 variables resolve to their default values', function (): void {
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

test('bhv servers 001 partial override keeps remaining defaults', function (): void {
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

test('bhv servers 008 override not in enum is rejected', function (): void {
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

test('bhv servers 003 unconstrained variable accepts any override', function (): void {
    // An empty enum array means any value is accepted.
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api'),
        ],
    );

    expect($server->getUrl(['env' => 'anything']))->toBe('https://anything.example.com');
});

test('bhv servers 006 exposes constructor fields', function (): void {
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

test('bhv servers 012 rejected override message names the value, the variable and the allowed values', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api', enumValues: ['api', 'staging']),
        ],
    );

    expect(fn () => $server->getUrl(['env' => 'invalid']))
        ->toThrow(
            \InvalidArgumentException::class,
            "Invalid value 'invalid' for server variable 'env'. Allowed values: [api, staging]"
        );
});

test('bhv servers 004 default value outside the enum is rejected', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(defaultValue: 'dev', enumValues: ['api', 'staging']),
        ],
    );

    expect(fn () => $server->getUrl())
        ->toThrow(
            \InvalidArgumentException::class,
            "Invalid value 'dev' for server variable 'env'. Allowed values: [api, staging]"
        );
});

test('bhv servers 002 a variable repeated in the template is replaced everywhere', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{region}.example.com/{region}/v1',
        variables: [
            'region' => new ServerVariable(defaultValue: 'us', enumValues: ['us', 'eu']),
        ],
    );

    expect($server->getUrl())->toBe('https://us.example.com/us/v1');
    expect($server->getUrl(['region' => 'eu']))->toBe('https://eu.example.com/eu/v1');
});

test('bhv servers 005 a placeholder introduced by a substituted value is not expanded again', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com/{version}',
        variables: [
            'env' => new ServerVariable(defaultValue: '{version}'),
            'version' => new ServerVariable(defaultValue: 'v2'),
        ],
    );

    expect($server->getUrl())->toBe('https://{version}.example.com/v2');
});

test('bhv servers 023 a server variable value containing replacement patterns is substituted literally', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(defaultValue: 'api'),
        ],
    );

    expect($server->getUrl(['env' => 'a$1\\0b']))->toBe('https://a$1\\0b.example.com');
});

test('bhv servers 007 leaves an unknown placeholder in the template untouched', function (): void {
    $server = new ServerConfiguration(urlTemplate: 'https://api.example.com/{unknown}');

    expect($server->getUrl())->toBe('https://api.example.com/{unknown}');
});

test('bhv servers 009 resolves the url template with default values for variables absent from the overrides', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.api.example.com/v{version}',
        variables: [
            'env' => new ServerVariable(defaultValue: 'prod'),
            'version' => new ServerVariable(defaultValue: '2'),
        ],
    );

    expect($server->getUrl())->toBe('https://prod.api.example.com/v2');
});

test('bhv servers 014 a variable without an enum accepts any override', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.api.example.com',
        variables: ['env' => new ServerVariable(defaultValue: 'prod')],
    );

    expect($server->getUrl(['env' => 'anything-goes']))->toBe('https://anything-goes.api.example.com');
});

test('bhv servers 025 changing the variables map after building the configuration leaves it unchanged', function (): void {
    $variables = ['env' => new ServerVariable(defaultValue: 'api')];
    $server = new ServerConfiguration(urlTemplate: 'https://{env}.example.com', variables: $variables);

    $variables['env'] = new ServerVariable(defaultValue: 'other');
    $variables['extra'] = new ServerVariable(defaultValue: 'x');

    expect(array_keys($server->variables))->toBe(['env']);
    expect($server->getUrl())->toBe('https://api.example.com');
});

test('bhv servers 028 a generated server and its variables cannot be changed after creation', function (): void {
    $server = Servers::all()[2];
    expect(function () use ($server): void {
        (new \ReflectionProperty(ServerConfiguration::class, 'urlTemplate'))->setValue($server, 'https://evil.example.com');
    })->toThrow(\Error::class);
    expect(function () use ($server): void {
        $variables = (new \ReflectionProperty(ServerConfiguration::class, 'variables'))->getValue($server);
        \assert(is_array($variables));
        $variables['tenant'] = new ServerVariable(defaultValue: 'x');
        (new \ReflectionProperty(ServerConfiguration::class, 'variables'))->setValue($server, $variables);
    })->toThrow(\Error::class);
});
