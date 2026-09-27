#nullable enable

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
}
