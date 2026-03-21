namespace PetstoreClient;

/// <summary>
/// Generated server configurations from the OpenAPI specification.
///
/// Each constant corresponds to a server entry defined in the spec's
/// <c>servers</c> array. Use these constants with
/// <see cref="ConfigurationBuilder.BaseUrl(string)"/> to select a server:
/// <code>
/// var config = Configuration.CreateBuilder()
///     .BaseUrl(Servers.Server0.GetUrl())
///     .Build();
/// </code>
///
/// For servers with variables, pass overrides via
/// <see cref="ServerConfiguration.GetUrl(Dictionary{string, string})"/>.
/// </summary>
public static class Servers
{
    /// <summary>
    /// Server 0: /api/v3
    /// </summary>
    public static readonly ServerConfiguration Server0 = new("/api/v3", null, []);

    /// <summary>
    /// All server configurations in declaration order.
    /// </summary>
    public static readonly IReadOnlyList<ServerConfiguration> All = [Server0];
}
