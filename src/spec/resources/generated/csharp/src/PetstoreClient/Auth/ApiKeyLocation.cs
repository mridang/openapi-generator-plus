namespace PetstoreClient.Auth;

/// <summary>
/// Specifies the location where an API key should be sent.
/// </summary>
public enum ApiKeyLocation
{
    /// <summary>API key sent as a request header.</summary>
    Header,

    /// <summary>API key sent as a query parameter.</summary>
    Query,

    /// <summary>API key sent as a cookie.</summary>
    Cookie,
}
