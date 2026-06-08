namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the GetPetByName operation.
/// </summary>
public sealed class GetPetByNameOptions
{
    /// <summary></summary>
    public required string Category { get; init; }
}
