#nullable enable
using PetstoreClient.Models;

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the SearchStore operation.
/// </summary>
public sealed class SearchStoreOptions
{
    /// <summary>JSON-encoded query parameter (OAS `content`, not `schema`)</summary>
    public PhotoMetadata? Q { get; init; }

    public HashSet<string>? Labels { get; init; }

    public TimeOnly? At { get; init; }

    public Dictionary<string, string>? XFilter { get; init; }
}
