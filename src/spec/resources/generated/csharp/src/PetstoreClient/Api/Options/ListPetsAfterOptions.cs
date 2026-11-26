#nullable enable

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the ListPetsAfter operation.
/// </summary>
public sealed class ListPetsAfterOptions
{
    /// <summary>The id of the last pet of the previous page</summary>
    public string? StartingAfter { get; init; }

    /// <summary>The number of pets on a page</summary>
    public int? Limit { get; init; }

    /// <summary>
    /// End-to-end timeout in milliseconds for this call only: it replaces
    /// <see cref="TransportOptions.Timeout"/> and must be greater than zero.
    /// When null the transport's timeout applies.
    /// </summary>
    public int? Timeout { get; init; }

    /// <summary>
    /// Extra request headers for this call only. They are sent after the
    /// default and authentication headers and replace a header of the same
    /// name, whatever its casing.
    /// </summary>
    public IReadOnlyDictionary<string, string>? Headers
    {
        get => _headers;
        init => _headers = value is null ? null : new Dictionary<string, string>(value);
    }

    private readonly IReadOnlyDictionary<string, string>? _headers;

    /// <summary>
    /// Maximum number of retries for this call only: it replaces
    /// <see cref="TransportOptions.MaxRetries"/>, and 0 turns retries off.
    /// When null the transport's setting applies.
    /// </summary>
    public int? MaxRetries { get; init; }

    /// <summary>
    /// Returns a copy of these options that starts at another cursor: the same value for every
    /// other parameter, the same timeout, retry count and headers. A paged list uses it to send
    /// each page with its own cursor.
    /// </summary>
    /// <param name="cursor">The cursor of the copy.</param>
    internal ListPetsAfterOptions WithCursor(string? cursor)
    {
        return new ListPetsAfterOptions
        {
            StartingAfter = cursor,
            Limit = this.Limit,
            Timeout = this.Timeout,
            Headers = this.Headers,
            MaxRetries = this.MaxRetries,
        };
    }
}
