#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI properties should not be strings
#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

using PetstoreClient.Models;

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the AddPetPhotos operation.
/// </summary>
public sealed class AddPetPhotosOptions
{
    /// <summary></summary>
    public required List<System.IO.Stream> Files { get; init; }

    /// <summary></summary>
    public required PhotoMetadata Metadata { get; init; }
}
