using PetstoreClient.Api;
using PetstoreClient.Auth;

namespace PetstoreClient;

/// <summary>
/// Unified entry point for all API services. Takes an <see cref="IAuthenticator"/>
/// and exposes each API group as a typed property.
/// </summary>
public class Client
{
    public PetApi Pet { get; }

    public StoreApi Store { get; }

    /// <summary>
    /// Creates a new client with the given authenticator.
    /// </summary>
    /// <param name="authenticator">Provides host URL and auth headers.</param>
    public Client(IAuthenticator authenticator)
    {
        var config = new Configuration { BaseUrl = authenticator.GetHost() };
        foreach (var header in authenticator.GetAuthHeaders())
        {
            config.DefaultHeaders[header.Key] = header.Value;
        }
        var apiClient = new DefaultApiClient(config);
        Pet = new PetApi(apiClient, config);
        Store = new StoreApi(apiClient, config);
    }

    /// <summary>
    /// Creates a client authenticated with a static Bearer token.
    /// </summary>
    /// <param name="host">API base URL.</param>
    /// <param name="accessToken">Bearer token.</param>
    /// <returns>Configured client instance.</returns>
    public static Client WithToken(string host, string accessToken) =>
        new(new TokenAuthenticator(host, accessToken));

    private sealed class TokenAuthenticator(string host, string accessToken) : IAuthenticator
    {
        public string GetHost() => host;

        public Dictionary<string, string> GetAuthHeaders() =>
            new() { ["Authorization"] = $"Bearer {accessToken}" };
    }
}
