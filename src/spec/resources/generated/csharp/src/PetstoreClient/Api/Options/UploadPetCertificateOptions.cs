namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the UploadPetCertificate operation.
/// </summary>
public sealed class UploadPetCertificateOptions
{
    /// <summary></summary>
    public required System.IO.Stream File { get; init; }
}
