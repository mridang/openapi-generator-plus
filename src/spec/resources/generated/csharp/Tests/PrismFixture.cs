using DotNet.Testcontainers.Builders;
using DotNet.Testcontainers.Configurations;
using DotNet.Testcontainers.Containers;
using Xunit;

namespace Tests;

public class PrismFixture : IAsyncLifetime
{
    private IContainer _container = null!;
    public string BaseUrl { get; private set; } = string.Empty;

    public async Task InitializeAsync()
    {
        var hostAppPath = Environment.GetEnvironmentVariable("HOST_APP_PATH") ?? Directory.GetCurrentDirectory();
        var specPath = Path.Combine(hostAppPath, "specs", "openapi.yaml");

        _container = new ContainerBuilder()
            .WithImage("stoplight/prism:5")
            .WithPortBinding(4010, true)
            .WithBindMount(specPath, "/tmp/openapi.yaml", AccessMode.ReadOnly)
            .WithCommand("mock", "-h", "0.0.0.0", "/tmp/openapi.yaml")
            .WithWaitStrategy(Wait.ForUnixContainer().UntilMessageIsLogged("Prism is listening"))
            .Build();

        await _container.StartAsync();

        BaseUrl = $"http://{_container.Hostname}:{_container.GetMappedPublicPort(4010)}";
        Environment.SetEnvironmentVariable("API_BASE_URL", BaseUrl);
    }

    public async Task DisposeAsync()
    {
        await _container.DisposeAsync();
    }
}

[CollectionDefinition("Prism")]
public class PrismCollection : ICollectionFixture<PrismFixture>
{
}
