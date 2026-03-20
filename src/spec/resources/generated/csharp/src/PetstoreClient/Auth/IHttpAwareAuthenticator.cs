namespace PetstoreClient.Auth;

/// <summary>
/// Extension of <see cref="IAuthenticator"/> for authentication schemes that require
/// making HTTP requests (e.g. OAuth2 token exchange, OpenID Connect discovery).
///
/// Implementations receive a shared <see cref="IApiClient"/> instance so that
/// authentication-related HTTP calls (token endpoints, discovery documents)
/// use the same transport configuration (proxy, TLS, timeouts) as regular
/// API calls.
///
/// The <see cref="IApiClient"/> is injected by the <see cref="Client"/> class after
/// construction, via <see cref="SetApiClient"/>. Implementations must not make
/// HTTP calls before the client is injected.
///
/// Only OAuth2 and OpenID Connect authenticators implement this interface.
/// Simple authenticators (Basic, Bearer, API Key) do not need HTTP access
/// and implement <see cref="IAuthenticator"/> directly.
/// </summary>
public interface IHttpAwareAuthenticator : IAuthenticator
{
    /// <summary>
    /// Inject the shared API client for making HTTP requests.
    ///
    /// Called by the <see cref="Client"/> constructor after the <see cref="IApiClient"/>
    /// has been created with the user's <see cref="TransportOptions"/>.
    /// Implementations should store this reference and use it for all
    /// outbound HTTP calls (token exchange, discovery, etc.).
    /// </summary>
    /// <param name="apiClient">The shared API client instance.</param>
    void SetApiClient(IApiClient apiClient);
}
