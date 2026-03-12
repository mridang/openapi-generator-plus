#pragma warning disable IDE0290 // Use primary constructor

namespace PetstoreClient.Auth.OAuth;

/// <summary>
/// Authenticator for the OAuth2 Client Credentials flow.
/// </summary>
public sealed class OAuth2ClientCredentialsAuthenticator : BaseAuthenticator
{
    private readonly string _host;
    private readonly string _clientId;
    private readonly string _clientSecret;
    private readonly Uri _tokenUrl;
    private readonly string[] _scopes;
    private readonly OAuth2TokenManager _tokenManager = new();

    public OAuth2ClientCredentialsAuthenticator(
        string host,
        string clientId,
        string clientSecret,
        Uri tokenUrl,
        string[] scopes
    )
    {
        _host = host;
        _clientId = clientId;
        _clientSecret = clientSecret;
        _tokenUrl = tokenUrl;
        _scopes = [.. scopes];
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
            ["grant_type"] = "client_credentials",
            ["client_id"] = _clientId,
            ["client_secret"] = _clientSecret,
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
