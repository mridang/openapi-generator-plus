#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI properties should not be strings
#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member


namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the FindPetsByStatus operation.
/// </summary>
public sealed class FindPetsByStatusOptions
{
    /// <summary> Status values that need to be considered for filter</summary>
    public string? Status { get; init; }

    /// <summary> Filter criteria as key-value pairs</summary>
    public Dictionary<string, string>? Filter { get; init; }

}
