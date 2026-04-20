#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

namespace PetstoreClient.Exceptions;

/// <summary>
/// Exception for HTTP 5xx server errors.
/// </summary>
public class ServerException : ApiException
{
    public ServerException() { }

    public ServerException(string message)
        : base(message) { }

    public ServerException(string message, Exception innerException)
        : base(message, innerException) { }

    public ServerException(
        int statusCode,
        string message,
        Dictionary<string, string>? responseHeaders = null,
        string? responseBody = null,
        object? errorBody = null
    )
        : base(statusCode, message, responseHeaders, responseBody, errorBody) { }
}
