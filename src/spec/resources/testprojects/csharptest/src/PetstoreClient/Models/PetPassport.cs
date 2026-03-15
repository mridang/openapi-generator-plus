#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class PetPassport
{
    [JsonPropertyName("pet")]
    public Pet? Pet { get; set; }

    /// <summary>
    /// Base64-encoded primary thumbnail
    /// </summary>
    [JsonPropertyName("thumbnail")]
    public byte[]? Thumbnail { get; set; }

    /// <summary>
    /// Base64-encoded scans of each passport page
    /// </summary>
    [JsonPropertyName("scans")]
    public List<byte[]>? Scans { get; set; }

    [JsonPropertyName("issuedAt")]
    public DateTimeOffset? IssuedAt { get; set; }
}
