namespace PetstoreClient.Auth;

/// <summary>
/// Authenticator for HTTP Bearer token authentication.
/// </summary>
public sealed class BearerAuthenticator(string host, string token) : BaseAuthenticator
{
    /// <inheritdoc/>
    public override string GetHost()
    {
        return host;
    }

    /// <inheritdoc/>
    public override Dictionary<string, string> GetAuthHeaders()
    {
        return new() { ["Authorization"] = "Bearer " + token };
    }
}
