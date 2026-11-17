#nullable enable
using PetstoreClient.Models;

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the AddPetPhotos operation.
/// </summary>
public sealed class AddPetPhotosOptions
{
    public required List<System.IO.Stream> Files { get; init; }

    public required PhotoMetadata Metadata { get; init; }

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
    public IReadOnlyDictionary<string, string>? Headers { get; init; }

    /// <summary>
    /// Maximum number of retries for this call only: it replaces
    /// <see cref="TransportOptions.MaxRetries"/>, and 0 turns retries off.
    /// When null the transport's setting applies.
    /// </summary>
    public int? MaxRetries { get; init; }
}
