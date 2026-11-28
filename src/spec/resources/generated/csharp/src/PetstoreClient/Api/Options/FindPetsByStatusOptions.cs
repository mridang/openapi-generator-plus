#nullable enable

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the FindPetsByStatus operation.
/// </summary>
public sealed class FindPetsByStatusOptions
{
    /// <summary>Status values that need to be considered for filter</summary>
    /// <example>Available only: available</example>
    /// <example>Sold pets: sold</example>
    /// <remarks>This parameter is deprecated.</remarks>
    public string? Status { get; init; }

    /// <summary>Filter criteria as key-value pairs</summary>
    public Dictionary<string, string>? Filter { get; init; }

    /// <summary>Only return pets born on or after this date</summary>
    public DateOnly? BornAfter { get; init; }

    public int? N2ndPage { get; init; }

    /// <summary>Reference date for the report</summary>
    public DateOnly? ReportDate { get; init; }

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
}
