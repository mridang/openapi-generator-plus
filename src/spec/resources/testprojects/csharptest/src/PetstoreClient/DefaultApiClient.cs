using System.Text;

namespace PetstoreClient;

/// <summary>
/// Default implementation of <see cref="IApiClient"/> using HttpClient.
/// </summary>
public sealed class DefaultApiClient : IApiClient, IDisposable
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
        ArgumentNullException.ThrowIfNull(config);

        var handler = new HttpClientHandler();

        if (!config.VerifySsl)
        {
            handler.ServerCertificateCustomValidationCallback =
                HttpClientHandler.DangerousAcceptAnyServerCertificateValidator;
        }
        else
        {
            handler.CheckCertificateRevocationList = true;
        }

        _httpClient = new HttpClient(handler, disposeHandler: true);
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
        using var request = new HttpRequestMessage(new HttpMethod(method), url);

        foreach (var header in headers)
        {
            request.Headers.TryAddWithoutValidation(header.Key, header.Value);
        }

        if (body != null)
        {
            request.Content = new StringContent(body, Encoding.UTF8, "application/json");
        }

        using var response = await _httpClient.SendAsync(request).ConfigureAwait(false);
        var responseBody = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
        var responseHeaders = new Dictionary<string, string>();

        foreach (var header in response.Headers)
        {
            responseHeaders[header.Key] = string.Join(",", header.Value);
        }

        return new ApiResponse((int)response.StatusCode, responseBody, responseHeaders);
    }

    /// <inheritdoc/>
    public void Dispose()
    {
        _httpClient.Dispose();
    }
}
