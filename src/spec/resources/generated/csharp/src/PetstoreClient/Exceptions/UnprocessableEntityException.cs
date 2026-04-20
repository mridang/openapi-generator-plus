#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

namespace PetstoreClient.Exceptions;

/// <summary>
/// Exception for HTTP 422 Unprocessable Entity.
/// </summary>
public class UnprocessableEntityException : ClientException
{
    public UnprocessableEntityException()
        : this("Unprocessable Entity") { }

    public UnprocessableEntityException(string message)
        : this(message, null, null, null) { }

    public UnprocessableEntityException(string message, Exception innerException)
        : base(message, innerException) { }

    public UnprocessableEntityException(
        string message,
        Dictionary<string, string>? responseHeaders = null,
        string? responseBody = null,
        object? errorBody = null
    )
        : base(422, message, responseHeaders, responseBody, errorBody) { }
}
