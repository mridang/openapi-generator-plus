#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only
#pragma warning disable CS0618 // Type or member is obsolete

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class PetPassport
{
    /// <example>null</example>
    [JsonPropertyName("pet")]
    public Pet? Pet { get; set; }

    /// <summary>
    /// Base64-encoded primary thumbnail
    /// </summary>
    /// <example>[B@75148a5b</example>
    [JsonPropertyName("thumbnail")]
    public byte[]? Thumbnail { get; set; }

    /// <summary>
    /// Base64-encoded scans of each passport page
    /// </summary>
    /// <example>null</example>
    [JsonPropertyName("scans")]
    public List<byte[]>? Scans { get; set; }

    /// <example>null</example>
    [JsonPropertyName("issuedAt")]
    public DateTimeOffset? IssuedAt { get; set; }
}
