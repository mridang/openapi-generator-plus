namespace PetstoreClient.Exceptions;

/// <summary>
/// Exception for HTTP 403 Forbidden.
/// </summary>
public class ForbiddenException : ClientException
{
    public ForbiddenException()
        : this("Forbidden") { }

    public ForbiddenException(string message)
        : this(message, null, null, null) { }

    public ForbiddenException(string message, Exception innerException)
        : base(message, innerException) { }

    public ForbiddenException(
        string message,
        Dictionary<string, string>? responseHeaders = null,
        string? responseBody = null,
        object? errorBody = null
    )
        : base(403, message, responseHeaders, responseBody, errorBody) { }
}
