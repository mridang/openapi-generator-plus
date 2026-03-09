namespace PetstoreClient.Auth;

/// <summary>
/// Interface for providing authentication credentials to the API client.
/// Implementations supply the API host URL and authorization headers.
/// </summary>
public interface IAuthenticator
{
    /// <summary>
    /// Returns the base URL of the API.
    /// </summary>
    string GetHost();

    /// <summary>
    /// Returns the authentication headers to include in every request.
    /// </summary>
    Dictionary<string, string> GetAuthHeaders();
}
