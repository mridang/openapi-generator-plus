#pragma warning disable CA1062 // Validate arguments of public methods
#pragma warning disable CA2000 // Dispose objects before losing scope
#pragma warning disable IDE0046 // Convert to conditional expression

using PetstoreClient.Auth;

namespace PetstoreClient.Api;

/// <summary>
/// Base class for all API classes. Provides the <c>InvokeApiAsync</c> method that
/// handles URL construction, header selection, body serialization, request
/// dispatch, and response deserialization.
/// </summary>
public abstract class BaseApi
{
    /// <summary>The HTTP transport client used for sending requests.</summary>
    protected IApiClient ApiClient { get; set; }

    /// <summary>API-level configuration (base URL and default headers).</summary>
    protected Configuration Config { get; }

    /// <summary>Serializer for request/response body conversion.</summary>
    protected ObjectSerializer Serializer { get; }

    /// <summary>
    /// Create an API instance with the default configuration and default transport.
    /// </summary>
    protected BaseApi()
        : this(Configuration.Default) { }

    /// <summary>
    /// Create an API instance with the given configuration and default transport.
    /// </summary>
    /// <param name="config">API-level configuration (base URL and default headers).</param>
    protected BaseApi(Configuration config)
        : this(new DefaultApiClient(), config) { }

    /// <summary>
    /// Create an API instance with a custom API client and configuration.
    /// </summary>
    /// <param name="apiClient">The HTTP transport client.</param>
    /// <param name="config">API-level configuration (base URL and default headers).</param>
    protected BaseApi(IApiClient apiClient, Configuration config)
    {
        ArgumentNullException.ThrowIfNull(apiClient);
        ArgumentNullException.ThrowIfNull(config);
        ApiClient = apiClient;
        Config = config;
        Serializer = new ObjectSerializer();
    }

    /// <summary>
    /// Invoke an API operation.
    /// </summary>
    protected async Task<T?> InvokeApiAsync<T>(
        string method,
        string path,
        Dictionary<string, object?> queryParams,
        Dictionary<string, string> headerParams,
        object? body,
        string[] accepts,
        string contentType,
        IAuthenticator? auth = null
    )
    {
        ArgumentNullException.ThrowIfNull(queryParams);
        ArgumentNullException.ThrowIfNull(headerParams);
        string url = Config.BaseUrl + path;

        if (auth is not null)
        {
            foreach (KeyValuePair<string, string> param in auth.GetQueryParams())
            {
                queryParams[param.Key] = param.Value;
            }
        }

        string query = BuildQueryString(queryParams);
        if (!string.IsNullOrEmpty(query))
        {
            url += "?" + query;
        }

        bool isMultipart = contentType == "multipart/form-data";
        Dictionary<string, string> headers = HeaderSelector.SelectHeaders(
            accepts,
            contentType,
            isMultipart
        );

        foreach (KeyValuePair<string, string> header in Config.DefaultHeaders)
        {
            headers[header.Key] = header.Value;
        }

        foreach (KeyValuePair<string, string> header in headerParams)
        {
            headers[header.Key] = header.Value;
        }

        if (auth is not null)
        {
            foreach (KeyValuePair<string, string> header in auth.GetAuthHeaders())
            {
                headers[header.Key] = header.Value;
            }

            Dictionary<string, string> cookies = auth.GetCookieParams();
            if (cookies.Count > 0)
            {
                string cookieStr = string.Join("; ", cookies.Select(c => c.Key + "=" + c.Value));
                headers["Cookie"] =
                    headers.TryGetValue("Cookie", out string? existing)
                    && !string.IsNullOrEmpty(existing)
                        ? existing + "; " + cookieStr
                        : cookieStr;
            }
        }

        object? requestBody = null;
        if (body != null)
        {
            bool isBinary =
                contentType.StartsWith("image/", StringComparison.OrdinalIgnoreCase)
                || string.Equals(
                    contentType,
                    "application/octet-stream",
                    StringComparison.OrdinalIgnoreCase
                );

            requestBody = (isBinary || isMultipart) ? body : Serializer.Serialize(body);
        }

        ApiResponse response = await ApiClient
            .SendRequestAsync(method, new Uri(url), headers, requestBody)
            .ConfigureAwait(false);

        if (response.StatusCode is < 200 or >= 300)
        {
            throw new ApiException(
                response.StatusCode,
                $"API returned status code {response.StatusCode}",
                response.Headers,
                response.Body
            );
        }

        if (string.IsNullOrEmpty(response.Body))
        {
            return default;
        }

        string? responseContentType = response
            .Headers.Where(h =>
                string.Equals(h.Key, "Content-Type", StringComparison.OrdinalIgnoreCase)
            )
            .Select(h => h.Value)
            .FirstOrDefault();

        if (responseContentType != null && !HeaderSelector.IsJsonMime(responseContentType))
        {
            if (typeof(T) == typeof(System.IO.Stream))
            {
                return (T)
                    (object)
                        new System.IO.MemoryStream(
                            System.Text.Encoding.UTF8.GetBytes(response.Body)
                        );
            }

            return (T)(object)response.Body;
        }

        return Serializer.Deserialize<T>(response.Body);
    }

    private static string BuildQueryString(Dictionary<string, object?> queryParams)
    {
        List<string> parts = [];
        foreach (KeyValuePair<string, object?> entry in queryParams)
        {
            if (entry.Value != null)
            {
                parts.Add(
                    Uri.EscapeDataString(entry.Key)
                        + "="
                        + Uri.EscapeDataString(entry.Value.ToString()!)
                );
            }
        }
        return string.Join("&", parts);
    }
}
