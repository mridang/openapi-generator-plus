namespace PetstoreClient;

/// <summary>
/// Represents an HTTP API response.
/// </summary>
public class ApiResponse
{
    public int StatusCode { get; }
    public string Body { get; }
    public Dictionary<string, string> Headers { get; }

    public ApiResponse(int statusCode, string body, Dictionary<string, string> headers)
    {
        StatusCode = statusCode;
        Body = body;
        Headers = headers;
    }
}
