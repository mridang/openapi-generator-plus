namespace PetstoreClient.Auth;

/// <summary>
/// Authenticator for HTTP Basic authentication.
/// </summary>
public sealed class BasicAuthenticator(string host, string username, string password)
    : BaseAuthenticator
{
    private readonly string _authHeader =
        "Basic "
        + Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes(username + ":" + password));

    /// <inheritdoc/>
    public override string GetHost()
    {
        return host;
    }

    /// <inheritdoc/>
    public override Dictionary<string, string> GetAuthHeaders()
    {
        return new() { ["Authorization"] = _authHeader };
    }
}
