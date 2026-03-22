namespace PetstoreClient.Exceptions;

/// <summary>
/// Exception for HTTP 409 Conflict.
/// </summary>
public class ConflictException : ClientException
{
    public ConflictException()
        : this("Conflict") { }

    public ConflictException(string message)
        : this(message, null, null, null) { }

    public ConflictException(string message, Exception innerException)
        : base(message, innerException) { }

    public ConflictException(
        string message,
        Dictionary<string, string>? responseHeaders = null,
        string? responseBody = null,
        object? errorBody = null
    )
        : base(409, message, responseHeaders, responseBody, errorBody) { }
}
