#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1724 // Type names should not match namespaces
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only
#pragma warning disable CS0618 // Type or member is obsolete

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class SetPetAvatarRequest
{
    /// <summary>
    /// Base64-encoded image data
    /// </summary>
    /// <example>null</example>
    [JsonRequired]
    [JsonPropertyName("data")]
    public byte[] Data { get; set; }

    /// <example>image/jpeg</example>
    [JsonRequired]
    [JsonPropertyName("mimeType")]
    public string MimeType { get; set; }

    [System.Text.Json.Serialization.JsonConstructor]
    public SetPetAvatarRequest(byte[] Data, string MimeType)
    {
        this.Data = Data;
        ArgumentNullException.ThrowIfNull(MimeType, nameof(MimeType));
        this.MimeType = MimeType;
    }
}
