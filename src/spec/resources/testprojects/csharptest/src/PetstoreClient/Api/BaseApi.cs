using System.Text.Json;
using System.Web;

namespace PetstoreClient.Api;

/// <summary>
/// Base class for all API classes.
/// </summary>
public abstract class BaseApi
{
    protected IApiClient ApiClient { get; set; }
    protected Configuration Config { get; }
    protected ObjectSerializer Serializer { get; }
    protected HeaderSelector HeaderSelector { get; }

    protected BaseApi()
        : this(Configuration.Default) { }

    protected BaseApi(Configuration config)
        : this(new DefaultApiClient(config), config) { }

    protected BaseApi(IApiClient apiClient, Configuration config)
    {
        ApiClient = apiClient;
        Config = config;
        Serializer = new ObjectSerializer();
        HeaderSelector = new HeaderSelector();
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
        string contentType
    )
    {
        var url = Config.BaseUrl + path;
        var query = BuildQueryString(queryParams);
        if (!string.IsNullOrEmpty(query))
        {
            url += "?" + query;
        }

        var isMultipart = contentType == "multipart/form-data";
        var headers = HeaderSelector.SelectHeaders(accepts, contentType, isMultipart);

        foreach (var header in Config.DefaultHeaders)
        {
            headers[header.Key] = header.Value;
        }

        foreach (var header in headerParams)
        {
            headers[header.Key] = header.Value;
        }

        string? serializedBody = null;
        if (body != null)
        {
            serializedBody = Serializer.Serialize(body);
        }

        var response = await ApiClient.SendRequestAsync(method, url, headers, serializedBody);

        if (response.StatusCode < 200 || response.StatusCode >= 300)
        {
            throw new ApiException(
                response.StatusCode,
                $"API returned status code {response.StatusCode}",
                response.Headers,
                response.Body
            );
        }

        if (!string.IsNullOrEmpty(response.Body))
        {
            return Serializer.Deserialize<T>(response.Body);
        }

        return default;
    }

    private static string BuildQueryString(Dictionary<string, object?> queryParams)
    {
        var parts = new List<string>();
        foreach (var entry in queryParams)
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
