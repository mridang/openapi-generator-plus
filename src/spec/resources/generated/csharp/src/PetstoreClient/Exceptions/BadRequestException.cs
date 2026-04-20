#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

namespace PetstoreClient.Exceptions;

/// <summary>
/// Exception for HTTP 400 Bad Request.
/// </summary>
public class BadRequestException : ClientException
{
    public BadRequestException()
        : this("Bad Request") { }

    public BadRequestException(string message)
        : this(message, null, null, null) { }

    public BadRequestException(string message, Exception innerException)
        : base(message, innerException) { }

    public BadRequestException(
        string message,
        Dictionary<string, string>? responseHeaders = null,
        string? responseBody = null,
        object? errorBody = null
    )
        : base(400, message, responseHeaders, responseBody, errorBody) { }
}
