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
