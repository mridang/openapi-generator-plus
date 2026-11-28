#nullable enable
namespace PetstoreClient.Api.Options;

/// <summary>
/// The response headers the success responses of the OptionsMemo operation declare, read in
/// their declared types.
/// </summary>
public sealed class OptionsMemoResponseHeaders
{
    private OptionsMemoResponseHeaders() { }

    /// <summary>The Allow response header, or null when the response does not carry it.</summary>
    public string? Allow { get; private init; }

    /// <summary>
    /// Reads the declared headers from the raw response headers, such as
    /// <see cref="ApiResult{T}.Headers"/>. A header name is matched whatever its case, and a
    /// header the response does not carry stays null.
    /// </summary>
    /// <param name="headers">The response headers.</param>
    /// <returns>The declared headers in their declared types.</returns>
    /// <exception cref="PetstoreClient.Errors.SerializationException">A header value does not parse as its declared type.</exception>
    public static OptionsMemoResponseHeaders From(IReadOnlyDictionary<string, string> headers)
    {
        ArgumentNullException.ThrowIfNull(headers);
        return new OptionsMemoResponseHeaders
        {
            Allow = Find(headers, "Allow") is string header0 ? header0 : null,
        };
    }

    /// <summary>The value of the header with the given name, compared without case, or null.</summary>
    private static string? Find(IReadOnlyDictionary<string, string> headers, string name)
    {
        foreach (KeyValuePair<string, string> header in headers)
        {
            if (string.Equals(header.Key, name, StringComparison.OrdinalIgnoreCase))
            {
                return header.Value;
            }
        }
        return null;
    }
}
