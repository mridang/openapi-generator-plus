namespace PetstoreClient;

/// <summary>
/// Exception thrown when an API call fails.
/// </summary>
public class ApiException : Exception
{
    public int StatusCode { get; }
    public string? ResponseBody { get; }
    public Dictionary<string, string>? ResponseHeaders { get; }

    public ApiException() { }

    public ApiException(string message)
        : base(message) { }

    public ApiException(string message, Exception innerException)
        : base(message, innerException) { }

    public ApiException(
        int statusCode,
        string message,
        Dictionary<string, string>? responseHeaders = null,
        string? responseBody = null
    )
        : base(message)
    {
        StatusCode = statusCode;
        ResponseHeaders = responseHeaders;
        ResponseBody = responseBody;
    }

    public override string ToString()
    {
        return $"ApiException{{StatusCode={StatusCode}, ResponseBody='{ResponseBody}'}}";
    }
}
