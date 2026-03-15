#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class SetPetAvatarRequest(byte[] Data, string MimeType)
{
    /// <summary>
    /// Base64-encoded image data
    /// </summary>
    [JsonPropertyName("data")]
    public byte[] Data { get; set; } = Data;

    [JsonPropertyName("mimeType")]
    public string MimeType { get; set; } = MimeType;
}
