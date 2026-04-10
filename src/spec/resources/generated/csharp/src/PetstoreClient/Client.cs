using PetstoreClient.Api;
using PetstoreClient.Auth;

namespace PetstoreClient;

/// <summary>
/// Unified entry point for all API services.
///
/// Takes an <see cref="IAuthenticator"/> and optionally <see cref="TransportOptions"/>,
/// then exposes each API group as a typed property. If the authenticator
/// implements <see cref="IHttpAwareAuthenticator"/>, the shared <see cref="IApiClient"/>
/// is injected so that authentication HTTP calls (token exchange, discovery)
/// use the same transport configuration as regular API calls.
///
/// Usage:
/// <code>
/// // Default transport
/// var client = new Client(authenticator);
///
/// // Custom transport (proxy, timeouts, etc.)
/// var transport = TransportOptions.Builder()
///     .Proxy("http://proxy:3128")
///     .Timeout(5000)
///     .Build();
/// var client = new Client(authenticator, transport);
/// </code>
/// </summary>
public sealed class Client : IDisposable
{
    private readonly DefaultApiClient _apiClient;

    /// <summary>API operations for the PetApi group.</summary>
    public PetApi Pet { get; }

    /// <summary>API operations for the StoreApi group.</summary>
    public StoreApi Store { get; }

    /// <summary>
    /// Creates a new client with the given authenticator and default transport settings.
    /// </summary>
    /// <param name="authenticator">Provides host URL and auth credentials.</param>
    public Client(IAuthenticator authenticator)
        : this(authenticator, TransportOptions.Builder().Build()) { }

    /// <summary>
    /// Creates a new client with the given authenticator and transport options.
    ///
    /// If the authenticator implements <see cref="IHttpAwareAuthenticator"/>, the
    /// shared <see cref="IApiClient"/> is injected so that token exchange and
    /// discovery requests use the same proxy, TLS, and timeout settings.
    /// </summary>
    /// <param name="authenticator">Provides host URL and auth credentials.</param>
    /// <param name="transportOptions">HTTP transport configuration (proxy, TLS, timeouts, etc.).</param>
    public Client(IAuthenticator authenticator, TransportOptions transportOptions)
    {
        ArgumentNullException.ThrowIfNull(authenticator);
        ArgumentNullException.ThrowIfNull(transportOptions);

        _apiClient = new DefaultApiClient(transportOptions);

        if (authenticator is IHttpAwareAuthenticator httpAware)
        {
            httpAware.SetApiClient(_apiClient);
        }

        ConfigurationBuilder configBuilder = Configuration
            .Builder()
            .BaseUrl(authenticator.GetHost());
        foreach (KeyValuePair<string, string> header in authenticator.GetAuthHeaders())
        {
            configBuilder = configBuilder.DefaultHeader(header.Key, header.Value);
        }
        Configuration config = configBuilder.Build();
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
