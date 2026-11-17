#nullable enable

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the GetPetSummary operation.
/// </summary>
public sealed class GetPetSummaryOptions
{
    /// <summary><![CDATA[Session token: \"a<b>\" & it's required]]></summary>
    public required string Session { get; init; }

    /// <summary>Extra dates to summarise</summary>
    public List<DateOnly>? Days { get; init; }

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
    public IReadOnlyDictionary<string, string>? Headers { get; init; }

    /// <summary>
    /// Maximum number of retries for this call only: it replaces
    /// <see cref="TransportOptions.MaxRetries"/>, and 0 turns retries off.
    /// When null the transport's setting applies.
    /// </summary>
    public int? MaxRetries { get; init; }
}
