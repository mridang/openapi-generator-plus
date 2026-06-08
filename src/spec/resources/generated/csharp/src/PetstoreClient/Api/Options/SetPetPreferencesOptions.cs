namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the SetPetPreferences operation.
/// </summary>
public sealed class SetPetPreferencesOptions
{
    /// <summary></summary>
    public required string Nickname { get; init; }

    /// <summary></summary>
    public List<string>? Tags { get; init; }

    /// <summary></summary>
    public string? Note { get; init; }
}
