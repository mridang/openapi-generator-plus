using PetstoreClient.Api;
using PetstoreClient.Auth;

namespace PetstoreClient;

/// <summary>
/// Unified entry point for all API services. Takes an <see cref="IAuthenticator"/>
/// and exposes each API group as a typed property.
/// </summary>
public sealed class Client : IDisposable
{
    private readonly DefaultApiClient _apiClient;

    public PetApi Pet { get; }

    public StoreApi Store { get; }

    /// <summary>
    /// Creates a new client with the given authenticator.
    /// </summary>
    /// <param name="authenticator">Provides host URL and auth headers.</param>
    public Client(IAuthenticator authenticator)
    {
        ArgumentNullException.ThrowIfNull(authenticator);
        ConfigurationBuilder configBuilder = Configuration
            .CreateBuilder()
            .BaseUrl(authenticator.GetHost());
        foreach (KeyValuePair<string, string> header in authenticator.GetAuthHeaders())
        {
            configBuilder = configBuilder.DefaultHeader(header.Key, header.Value);
        }
        Configuration config = configBuilder.Build();
        _apiClient = new DefaultApiClient(config);
        Pet = new PetApi(_apiClient, config);
        Store = new StoreApi(_apiClient, config);
    }

    /// <summary>
    /// Creates a client authenticated with a static Bearer token.
    /// </summary>
    /// <param name="host">API base URL.</param>
    /// <param name="accessToken">Bearer token.</param>
    /// <returns>Configured client instance.</returns>
    public static Client WithToken(string host, string accessToken)
    {
        return new Client(new BearerAuthenticator(host, accessToken));
    }

    /// <inheritdoc/>
    public void Dispose()
    {
        _apiClient.Dispose();
    }
}
