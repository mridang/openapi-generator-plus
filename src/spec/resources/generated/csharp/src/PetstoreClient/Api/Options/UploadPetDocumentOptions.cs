#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI properties should not be strings
#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the UploadPetDocument operation.
/// </summary>
public sealed class UploadPetDocumentOptions
{
    /// <summary></summary>
    public required System.IO.Stream File { get; init; }

    /// <summary></summary>
    public string? DocumentType { get; init; }

    /// <summary></summary>
    public string? Notes { get; init; }

}
