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
