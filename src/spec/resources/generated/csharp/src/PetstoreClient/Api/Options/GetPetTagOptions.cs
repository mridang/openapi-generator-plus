#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI properties should not be strings
#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member


namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the GetPetTag operation.
/// </summary>
public sealed class GetPetTagOptions
{
    /// <summary></summary>
    public List<string>? Colors { get; init; }

    /// <summary></summary>
    public List<string>? Sizes { get; init; }

    /// <summary></summary>
    public string? Filter { get; init; }

}
