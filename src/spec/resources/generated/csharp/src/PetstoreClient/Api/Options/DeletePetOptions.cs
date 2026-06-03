#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI properties should not be strings
#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the DeletePet operation.
/// </summary>
public sealed class DeletePetOptions
{
    /// <summary> Session cookie used for authentication</summary>
    public string? ApiKey { get; init; }

}
