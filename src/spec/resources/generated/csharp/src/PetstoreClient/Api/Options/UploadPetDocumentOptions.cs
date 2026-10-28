#nullable enable
using PetstoreClient.Models;

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the UploadPetDocument operation.
/// </summary>
public sealed class UploadPetDocumentOptions
{
    public required System.IO.Stream File { get; init; }

    public string? DocumentType { get; init; }

    public string? Notes { get; init; }

    public bool? IsPrimary { get; init; }

    public long? PageCount { get; init; }

    public DateTimeOffset? TakenAt { get; init; }

    public Swatch? Swatch { get; init; }
}
