#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

namespace PetstoreClient.Exceptions;

/// <summary>
/// Exception for HTTP 401 Unauthorized.
/// </summary>
public class UnauthorizedException : ClientException
{
    public UnauthorizedException()
        : this("Unauthorized") { }

    public UnauthorizedException(string message)
        : this(message, null, null, null) { }

    public UnauthorizedException(string message, Exception innerException)
        : base(message, innerException) { }

    public UnauthorizedException(
        string message,
        Dictionary<string, string>? responseHeaders = null,
        string? responseBody = null,
        object? errorBody = null
    )
        : base(401, message, responseHeaders, responseBody, errorBody) { }
}
