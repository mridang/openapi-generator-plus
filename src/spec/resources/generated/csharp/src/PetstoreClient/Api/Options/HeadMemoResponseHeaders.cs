#nullable enable
namespace PetstoreClient.Api.Options;

/// <summary>
/// The response headers the success responses of the HeadMemo operation declare, read in
/// their declared types.
/// </summary>
public sealed class HeadMemoResponseHeaders
{
    private HeadMemoResponseHeaders() { }

    /// <summary>The revision of the memo</summary>
    public int? XRevision { get; private init; }

    /// <summary>
    /// Reads the declared headers from the raw response headers, such as
    /// <see cref="ApiResult{T}.Headers"/>. A header name is matched whatever its case, and a
    /// header the response does not carry stays null.
    /// </summary>
    /// <param name="headers">The response headers.</param>
    /// <returns>The declared headers in their declared types.</returns>
    /// <exception cref="PetstoreClient.Errors.SerializationException">A header value does not parse as its declared type.</exception>
    public static HeadMemoResponseHeaders From(IReadOnlyDictionary<string, string> headers)
    {
        ArgumentNullException.ThrowIfNull(headers);
        return new HeadMemoResponseHeaders
        {
            XRevision = Find(headers, "X-Revision") is string header0
                ? int.TryParse(
                    header0,
                    System.Globalization.NumberStyles.AllowLeadingSign,
                    System.Globalization.CultureInfo.InvariantCulture,
                    out int header0Value
                )
                    ? header0Value
                    : throw Invalid("X-Revision", "integer", header0)
                : null,
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

    /// <summary>The error raised for a header value that does not parse as its declared type.</summary>
    private static PetstoreClient.Errors.SerializationException Invalid(
        string name,
        string kind,
        string value
    )
    {
        return new PetstoreClient.Errors.SerializationException(
            "Response header '" + name + "' is not a valid " + kind + ": '" + value + "'"
        );
    }
}
