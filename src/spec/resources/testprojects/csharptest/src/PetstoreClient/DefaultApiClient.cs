using System.Text;

namespace PetstoreClient;

/// <summary>
/// Default implementation of <see cref="IApiClient"/> using HttpClient.
/// </summary>
public class DefaultApiClient : IApiClient
{
    private readonly HttpClient _httpClient;

    /// <summary>
    /// Create a client with default settings.
    /// </summary>
    public DefaultApiClient()
    {
        _httpClient = new HttpClient();
    }

    /// <summary>
    /// Create a client configured from the given configuration.
    /// </summary>
    public DefaultApiClient(Configuration config)
    {
        var handler = new HttpClientHandler();

        if (!config.VerifySsl)
        {
            handler.ServerCertificateCustomValidationCallback =
                HttpClientHandler.DangerousAcceptAnyServerCertificateValidator;
        }

        _httpClient = new HttpClient(handler);
    }

    /// <summary>
    /// Create a client with a pre-configured HttpClient.
    /// </summary>
    public DefaultApiClient(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    /// <inheritdoc/>
    public async Task<ApiResponse> SendRequestAsync(
        string method,
        string url,
        Dictionary<string, string> headers,
        string? body
    )
    {
        var request = new HttpRequestMessage(new HttpMethod(method), url);

        foreach (var header in headers)
        {
            request.Headers.TryAddWithoutValidation(header.Key, header.Value);
        }

        if (body != null)
        {
            request.Content = new StringContent(body, Encoding.UTF8, "application/json");
        }

        var response = await _httpClient.SendAsync(request);
        var responseBody = await response.Content.ReadAsStringAsync();
        var responseHeaders = new Dictionary<string, string>();

        foreach (var header in response.Headers)
        {
            responseHeaders[header.Key] = string.Join(",", header.Value);
        }

        return new ApiResponse((int)response.StatusCode, responseBody, responseHeaders);
    }
}
