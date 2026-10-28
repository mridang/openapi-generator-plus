#nullable enable
using PetstoreClient.Models;

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the GetPetTag operation.
/// </summary>
public sealed class GetPetTagOptions
{
    public List<string>? Colors { get; init; }

    public List<string>? Sizes { get; init; }

    public string? Filter { get; init; }

    /// <summary>Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.</summary>
    public string? Revision { get; init; }

    /// <summary>Object-valued query parameter, form style exploded (the OAS default for query)</summary>
    public PetCriteria? Criteria { get; init; }

    public PetCriteria? CriteriaCsv { get; init; }

    public PetCriteria? CriteriaDeep { get; init; }

    /// <summary>Opaque bytes, base64 on the wire (OAS format byte)</summary>
    public byte[]? Fingerprint { get; init; }

    public PetCriteria? XCriteria { get; init; }

    public byte[]? XFingerprint { get; init; }
}
