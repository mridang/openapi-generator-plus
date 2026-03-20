#pragma warning disable IDE0290 // Use primary constructor

namespace PetstoreClient.Auth.OAuth;

/// <summary>
/// Authenticator for the OAuth2 Resource Owner Password Credentials flow.
///
/// Implements <see cref="IHttpAwareAuthenticator"/> so that token exchange requests
/// use the shared <see cref="IApiClient"/> with the same transport configuration
/// (proxy, TLS, timeouts) as regular API calls.
/// </summary>
public sealed class OAuth2PasswordAuthenticator : BaseAuthenticator, IHttpAwareAuthenticator
{
    private readonly string _host;
    private readonly string _clientId;
    private readonly string _clientSecret;
    private readonly Uri _tokenUrl;
    private readonly string _username;
    private readonly string _password;
    private readonly string[] _scopes;
    private readonly OAuth2TokenManager _tokenManager = new();

    /// <summary>
    /// Create a new password authenticator.
    /// </summary>
    /// <param name="host">API base URL.</param>
    /// <param name="clientId">OAuth2 client ID.</param>
    /// <param name="clientSecret">OAuth2 client secret.</param>
    /// <param name="tokenUrl">Token endpoint URL.</param>
    /// <param name="username">Resource owner username.</param>
    /// <param name="password">Resource owner password.</param>
    /// <param name="scopes">Requested scopes.</param>
    public OAuth2PasswordAuthenticator(
        string host,
        string clientId,
        string clientSecret,
        Uri tokenUrl,
        string username,
        string password,
        string[] scopes
    )
    {
        _host = host;
        _clientId = clientId;
        _clientSecret = clientSecret;
        _tokenUrl = tokenUrl;
        _username = username;
        _password = password;
        _scopes = [.. scopes];
    }

    /// <inheritdoc/>
    public void SetApiClient(IApiClient apiClient)
    {
        _tokenManager.SetApiClient(apiClient);
    }

    /// <inheritdoc/>
    public override string GetHost()
    {
        return _host;
    }

    /// <inheritdoc/>
    public override Dictionary<string, string> GetAuthHeaders()
    {
        Dictionary<string, string> parameters = new()
        {
            ["grant_type"] = "password",
            ["client_id"] = _clientId,
            ["client_secret"] = _clientSecret,
            ["username"] = _username,
            ["password"] = _password,
        };
        if (_scopes.Length > 0)
        {
            parameters["scope"] = string.Join(" ", _scopes);
        }

        string token = _tokenManager
            .GetAccessTokenAsync(_tokenUrl, parameters)
            .GetAwaiter()
            .GetResult();
        return new() { ["Authorization"] = "Bearer " + token };
    }
}
