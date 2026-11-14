#nullable enable

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the GetPetSummary operation.
/// </summary>
public sealed class GetPetSummaryOptions
{
    /// <summary><![CDATA[Session token: \"a<b>\" & it's required]]></summary>
    public required string Session { get; init; }

    /// <summary>Extra dates to summarise</summary>
    public List<DateOnly>? Days { get; init; }
}
