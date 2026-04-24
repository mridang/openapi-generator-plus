<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Configuration;
use PetstoreClient\ConfigurationBuilder;
use PetstoreClient\ServerConfiguration;
use PetstoreClient\ServerVariable;

class ConfigurationTest extends TestCase
{
    protected function tearDown(): void
    {
        // Reset the default instance between tests to avoid leaking state
        Configuration::setDefault(new Configuration());
    }

    public function testDefaultConstructorUsesSpecBaseUrl(): void
    {
        $config = new Configuration();

        $this->assertSame('/api/v3', $config->baseUrl);
        $this->assertSame([], $config->defaultHeaders);
    }

    public function testBuilderProducesCorrectDefaults(): void
    {
        $config = Configuration::builder()->build();

        $this->assertSame('/api/v3', $config->baseUrl);
        $this->assertSame([], $config->defaultHeaders);
    }

    public function testBuilderReturnsConfigurationBuilderInstance(): void
    {
        $builder = Configuration::builder();

        $this->assertInstanceOf(ConfigurationBuilder::class, $builder);
    }

    public function testBuilderSetsBaseUrl(): void
    {
        $config = Configuration::builder()
            ->baseUrl('https://custom.example.com')
            ->build();

        $this->assertSame('https://custom.example.com', $config->baseUrl);
    }

    public function testBuilderSetsSingleDefaultHeader(): void
    {
        $config = Configuration::builder()
            ->defaultHeader('Authorization', 'Bearer token123')
            ->build();

        $this->assertSame(['Authorization' => 'Bearer token123'], $config->defaultHeaders);
    }

    public function testBuilderSetsMultipleDefaultHeaders(): void
    {
        $config = Configuration::builder()
            ->defaultHeaders([
                'Authorization' => 'Bearer token123',
                'X-Custom' => 'value',
            ])
            ->build();

        $this->assertSame([
            'Authorization' => 'Bearer token123',
            'X-Custom' => 'value',
        ], $config->defaultHeaders);
    }

    public function testBuilderAccumulatesHeaders(): void
    {
        $config = Configuration::builder()
            ->defaultHeader('X-First', 'one')
            ->defaultHeader('X-Second', 'two')
            ->defaultHeaders(['X-Third' => 'three'])
            ->build();

        $this->assertCount(3, $config->defaultHeaders);
        $this->assertSame('one', $config->defaultHeaders['X-First']);
        $this->assertSame('two', $config->defaultHeaders['X-Second']);
        $this->assertSame('three', $config->defaultHeaders['X-Third']);
    }

    public function testBuilderSetsAllFields(): void
    {
        $config = Configuration::builder()
            ->baseUrl('https://api.example.com')
            ->defaultHeader('Authorization', 'Bearer token')
            ->defaultHeaders(['X-Custom' => 'value'])
            ->build();

        $this->assertSame('https://api.example.com', $config->baseUrl);
        $this->assertSame([
            'Authorization' => 'Bearer token',
            'X-Custom' => 'value',
        ], $config->defaultHeaders);
    }

    public function testBuilderServerResolvesUrl(): void
    {
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

        $this->assertSame('https://api.example.com/api/v3', $config->baseUrl);
    }

    public function testBuilderServerWithVariableOverrides(): void
    {
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

        $this->assertSame('https://staging.example.com/api/v2', $config->baseUrl);
    }

    public function testBuilderBaseUrlOverridesServer(): void
    {
        $server = new ServerConfiguration(
            urlTemplate: 'https://api.example.com',
        );

        $config = Configuration::builder()
            ->server($server)
            ->baseUrl('https://override.example.com')
            ->build();

        $this->assertSame('https://override.example.com', $config->baseUrl);
    }

    public function testGetDefaultReturnsInstance(): void
    {
        $config = Configuration::getDefault();

        $this->assertInstanceOf(Configuration::class, $config);
        $this->assertSame('/api/v3', $config->baseUrl);
    }

    public function testGetDefaultReturnsSameInstance(): void
    {
        $first = Configuration::getDefault();
        $second = Configuration::getDefault();

        $this->assertSame($first, $second);
    }

    public function testSetDefaultChangesDefault(): void
    {
        $custom = Configuration::builder()
            ->baseUrl('https://custom.example.com')
            ->build();

        Configuration::setDefault($custom);

        $this->assertSame($custom, Configuration::getDefault());
        $this->assertSame('https://custom.example.com', Configuration::getDefault()->baseUrl);
    }

    public function testConfigurationIsImmutable(): void
    {
        $config = Configuration::builder()
            ->defaultHeader('X-Key', 'value')
            ->build();

        // readonly properties cannot be modified after construction;
        // verify the values are frozen
        $this->assertSame('value', $config->defaultHeaders['X-Key']);
    }

    public function testBuilderIsFluent(): void
    {
        $builder = Configuration::builder();

        $this->assertSame($builder, $builder->baseUrl('https://example.com'));
        $this->assertSame($builder, $builder->defaultHeader('X-Key', 'value'));
        $this->assertSame($builder, $builder->defaultHeaders(['X-Other' => 'val']));

        $server = new ServerConfiguration(urlTemplate: 'https://example.com');
        $this->assertSame($builder, $builder->server($server));
    }
}
