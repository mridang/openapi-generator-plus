<?php

declare(strict_types=1);

use PetstoreClient\Configuration;
use PetstoreClient\ConfigurationBuilder;
use PetstoreClient\ServerConfiguration;
use PetstoreClient\ServerVariable;

test('default constructor uses spec base url', function (): void {
    $config = new Configuration();

    expect($config->baseUrl)->toBe('/api/v3');
    expect($config->defaultHeaders)->toBe([]);
});

test('bhv config 005 builder produces correct defaults', function (): void {
    $config = Configuration::builder()->build();

    expect($config->baseUrl)->toBe('/api/v3');
    expect($config->defaultHeaders)->toBe([]);
});

test('bhv config 007 builder returns configuration builder instance', function (): void {
    $builder = Configuration::builder();

    expect($builder)->toBeInstanceOf(ConfigurationBuilder::class);
});

test('bhv config 011 builder sets base url', function (): void {
    $config = Configuration::builder()
        ->baseUrl('https://custom.example.com')
        ->build();

    expect($config->baseUrl)->toBe('https://custom.example.com');
});

test('bhv config 013 builder sets single default header', function (): void {
    $config = Configuration::builder()
        ->defaultHeader('Authorization', 'Bearer token123')
        ->build();

    expect($config->defaultHeaders)->toBe(['Authorization' => 'Bearer token123']);
});

test('bhv config 012 builder sets multiple default headers', function (): void {
    $config = Configuration::builder()
        ->defaultHeaders([
            'Authorization' => 'Bearer token123',
            'X-Custom' => 'value',
        ])
        ->build();

    expect($config->defaultHeaders)->toBe([
        'Authorization' => 'Bearer token123',
        'X-Custom' => 'value',
    ]);
});

test('bhv config 002 builder accumulates headers', function (): void {
    $config = Configuration::builder()
        ->defaultHeader('X-First', 'one')
        ->defaultHeader('X-Second', 'two')
        ->defaultHeaders(['X-Third' => 'three'])
        ->build();

    expect($config->defaultHeaders)->toHaveCount(3);
    expect($config->defaultHeaders['X-First'])->toBe('one');
    expect($config->defaultHeaders['X-Second'])->toBe('two');
    expect($config->defaultHeaders['X-Third'])->toBe('three');
});

test('bhv config 010 builder sets all fields', function (): void {
    $config = Configuration::builder()
        ->baseUrl('https://api.example.com')
        ->defaultHeader('Authorization', 'Bearer token')
        ->defaultHeaders(['X-Custom' => 'value'])
        ->build();

    expect($config->baseUrl)->toBe('https://api.example.com');
    expect($config->defaultHeaders)->toBe([
        'Authorization' => 'Bearer token',
        'X-Custom' => 'value',
    ]);
});

test('bhv config 008 builder server resolves url', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com/api/{version}',
        description: 'Test server',
        variables: [
            'env' => new ServerVariable(
                defaultValue: 'api',
                enumValues: ['api', 'staging'],
            ),
            'version' => new ServerVariable(
                defaultValue: 'v3',
                enumValues: ['v2', 'v3'],
            ),
        ],
    );

    $config = Configuration::builder()
        ->server($server)
        ->build();

    expect($config->baseUrl)->toBe('https://api.example.com/api/v3');
});

test('bhv config 009 builder server with variable overrides', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com/api/{version}',
        variables: [
            'env' => new ServerVariable(
                defaultValue: 'api',
                enumValues: ['api', 'staging'],
            ),
            'version' => new ServerVariable(
                defaultValue: 'v3',
                enumValues: ['v2', 'v3'],
            ),
        ],
    );

    $config = Configuration::builder()
        ->server($server, ['env' => 'staging', 'version' => 'v2'])
        ->build();

    expect($config->baseUrl)->toBe('https://staging.example.com/api/v2');
});

test('bhv config 016 invalid server variable enum value throws', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://{env}.example.com',
        variables: [
            'env' => new ServerVariable(
                defaultValue: 'api',
                enumValues: ['api', 'staging'],
            ),
        ],
    );

    expect(fn () => Configuration::builder()
        ->server($server, ['env' => 'invalid'])
        ->build())
        ->toThrow(InvalidArgumentException::class);
});

test('bhv config 006 builder produces independent instances', function (): void {
    $builder = Configuration::builder()->baseUrl('https://example.com');
    $first = $builder->build();
    $second = $builder->build();

    expect($first)->not->toBe($second);
    expect($first->baseUrl)->toBe($second->baseUrl);
});

test('bhv config 001 builder base url overrides server', function (): void {
    $server = new ServerConfiguration(
        urlTemplate: 'https://api.example.com',
    );

    $config = Configuration::builder()
        ->server($server)
        ->baseUrl('https://override.example.com')
        ->build();

    expect($config->baseUrl)->toBe('https://override.example.com');
});

test('default configuration uses spec base url', function (): void {
    $config = Configuration::defaultConfiguration();

    expect($config)->toBeInstanceOf(Configuration::class);
    expect($config->baseUrl)->toBe('/api/v3');
    expect($config->defaultHeaders)->toBe([]);
});

test('bhv config 014 default configuration is stateless', function (): void {
    // There is no settable process-wide default: every call hands back a
    // fresh instance, so one caller cannot change what another gets.
    $first = Configuration::defaultConfiguration();
    $second = Configuration::defaultConfiguration();

    expect($second)->not->toBe($first);
    expect($second->baseUrl)->toBe($first->baseUrl);
});

test('bhv config 017 configuration is immutable', function (): void {
    $config = Configuration::builder()
        ->defaultHeader('X-Key', 'value')
        ->build();

    // readonly properties cannot be modified after construction;
    // verify the values are frozen
    expect($config->defaultHeaders['X-Key'])->toBe('value');
    expect(fn () => (new \ReflectionProperty(Configuration::class, 'baseUrl'))
        ->setValue($config, 'https://other.example.com'))->toThrow(\Error::class);
    expect(fn () => (new \ReflectionProperty(Configuration::class, 'defaultHeaders'))
        ->setValue($config, ['X-Other' => 'value']))->toThrow(\Error::class);
});

test('bhv options 044 the README names the builder setter that rejects a SOCKS proxy', function (): void {
    $readme = (string) file_get_contents(__DIR__ . '/../README.md');
    $section = strpos($readme, '### SOCKS proxies');

    expect($section)->not->toBeFalse();
    expect(substr($readme, (int) $section, 400))->toContain('`TransportOptions::builder()->proxy()`');
});

test('bhv config 015 debug output masks credential default headers', function (): void {
    $config = Configuration::builder()
        ->defaultHeader('Authorization', 'Bearer s3cr3t')
        ->defaultHeader('Proxy-Authorization', 'Basic s3cr3t')
        ->defaultHeader('Cookie', 'session=s3cr3t')
        ->defaultHeader('X-Api-Key', 's3cr3t')
        ->defaultHeader('X-Auth-Token', 's3cr3t')
        ->defaultHeader('X-Client-Secret', 's3cr3t')
        ->defaultHeader('X-Trace', 'visible')
        ->build();

    $dump = print_r($config, true);

    expect($dump)->not->toContain('s3cr3t');
    expect($dump)->toContain('visible');
    expect($config->defaultHeaders['Authorization'])->toBe('Bearer s3cr3t');
});

test('bhv config 003 builder is fluent', function (): void {
    $builder = Configuration::builder();

    expect($builder->baseUrl('https://example.com'))->toBe($builder);
    expect($builder->defaultHeader('X-Key', 'value'))->toBe($builder);
    expect($builder->defaultHeaders(['X-Other' => 'val']))->toBe($builder);

    $server = new ServerConfiguration(urlTemplate: 'https://example.com');
    expect($builder->server($server))->toBe($builder);
});

test('every SDK class autoloads cold from its own file', function (): void {
    /* PSR-4 loads a class only from a file named after it. A class declared
     * in another class's file (an inline enum next to its model, a server
     * variant next to its API group) works only once that other file happens
     * to have been loaded, so each class is referenced FIRST, in a fresh
     * process, through the composer autoloader. */
    $root = dirname(__DIR__);
    /** @var array{autoload: array{psr-4: array<string, string>}} $composer */
    $composer = json_decode((string) file_get_contents($root . '/composer.json'), true);
    $autoload = var_export($root . '/vendor/autoload.php', true);
    $checked = 0;
    foreach ($composer['autoload']['psr-4'] as $prefix => $dir) {
        $base = (string) realpath($root . '/' . $dir);
        $files = new \RecursiveIteratorIterator(
            new \RecursiveDirectoryIterator($base, \FilesystemIterator::SKIP_DOTS)
        );
        foreach ($files as $file) {
            if (!$file instanceof \SplFileInfo || $file->getExtension() !== 'php') {
                continue;
            }
            $relative = substr($file->getPathname(), strlen($base) + 1, -4);
            $class = var_export($prefix . str_replace('/', '\\', $relative), true);
            $code = "require $autoload; exit(class_exists($class) || interface_exists($class)"
                . " || enum_exists($class) || trait_exists($class) ? 0 : 1);";
            $output = [];
            exec(escapeshellarg(PHP_BINARY) . ' -r ' . escapeshellarg($code) . ' 2>&1', $output, $status);
            expect($status)->toBe(0, "$class did not autoload cold: " . implode("\n", $output));
            $checked++;
        }
    }
    expect($checked)->toBeGreaterThan(0);
});

test('the README names the composer package and the real proxy setter', function (): void {
    $root = dirname(__DIR__);
    /** @var array{name: string} $composer */
    $composer = json_decode((string) file_get_contents($root . '/composer.json'), true);
    $readme = (string) file_get_contents($root . '/README.md');

    expect($readme)->toContain('- Name: `' . $composer['name'] . '`');
    expect($readme)->toContain('- Namespace: `' . substr(Configuration::class, 0, -strlen('\\Configuration')) . '`');
    expect($readme)->toContain('`TransportOptions::builder()->proxy()` accepts only');
    expect($readme)->not->toContain('dead code');
});

test('SKILLS types file upload parameters as the SplFileObject the options require', function (): void {
    $skills = (string) file_get_contents(__DIR__ . '/../SKILLS.md');

    expect($skills)->toContain('A multipart file field is a `\SplFileObject`');
    expect($skills)->not->toContain('SplFileInfo');
});

test('phpunit.xml runs the hand written spec directory as well as tests', function (): void {
    $root = dirname(__DIR__);
    if (!is_file($root . '/phpunit.xml') || !is_dir($root . '/spec')) {
        test()->markTestSkipped('No phpunit.xml or no spec directory in this project');
    }
    $xml = simplexml_load_file($root . '/phpunit.xml');
    assert($xml instanceof \SimpleXMLElement);
    $directories = array_map(strval(...), $xml->xpath('//testsuite/directory') ?: []);

    expect($directories)->toContain('tests');
    expect($directories)->toContain('spec');
});

test('bhv skills 001 SKILLS names the root exception and the error types that are siblings of ApiException', function (): void {
    $skills = (string) file_get_contents(__DIR__ . '/../SKILLS.md');

    expect(preg_match('/- `[A-Za-z0-9]+Exception` \(root\)/', $skills))->toBe(1);
    foreach (['SerializationException', 'OAuth2ServerException', 'OAuth2TokenException'] as $name) {
        expect($skills)->toContain('  - `' . $name . '`');
    }
    expect($skills)->toContain('  - `ApiException`');
    expect($skills)->toContain('Every failure the SDK raises derives from');
});

test('bhv skills 002 SKILLS documents the retries in the configuration section', function (): void {
    $skills = (string) file_get_contents(__DIR__ . '/../SKILLS.md');
    $start = strpos($skills, "\n## Configuration\n");
    $end = strpos($skills, "\n## API Methods\n");
    expect($start)->toBeInt();
    expect($end)->toBeInt();
    assert(is_int($start) && is_int($end));
    $section = substr($skills, $start, $end - $start);

    expect($section)->toContain("\n### Retries\n");
    expect($section)->toContain('`Retry-After`');
});

test('bhv config 004 builder merges single and bulk default headers', function (): void {
    $config = Configuration::builder()
        ->defaultHeader('X-First', 'one')
        ->defaultHeaders(['X-Second' => 'two'])
        ->defaultHeader('X-Third', 'three')
        ->build();
    expect($config->defaultHeaders)->toBe(['X-First' => 'one', 'X-Second' => 'two', 'X-Third' => 'three']);
});
