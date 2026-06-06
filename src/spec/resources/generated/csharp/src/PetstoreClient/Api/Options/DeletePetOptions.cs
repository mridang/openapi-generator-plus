namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the DeletePet operation.
/// </summary>
public sealed class DeletePetOptions
{
    /// <summary> Session cookie used for authentication</summary>
    public string? ApiKey { get; init; }
}
