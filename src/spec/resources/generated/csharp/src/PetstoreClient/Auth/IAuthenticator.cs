namespace PetstoreClient.Auth;

/// <summary>
/// Interface for providing authentication credentials to the API client.
/// Implementations supply the API host URL, authorization headers,
/// query parameters, and cookie parameters.
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

    /// <summary>
    /// Returns query parameters to include for authentication.
    /// </summary>
    Dictionary<string, string> GetQueryParams()
    {
        return [];
    }

    /// <summary>
    /// Returns cookie parameters to include for authentication.
    /// </summary>
    Dictionary<string, string> GetCookieParams()
    {
        return [];
    }
}
