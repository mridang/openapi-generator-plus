#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class Pet(string Name, List<string> PhotoUrls)
{
    [JsonConverter(typeof(JsonStringEnumConverter))]
    public enum StatusEnum
    {
        [JsonStringEnumMemberName("available")]
        Available,

        [JsonStringEnumMemberName("pending")]
        Pending,

        [JsonStringEnumMemberName("sold")]
        Sold,
    }

    [JsonPropertyName("id")]
    public long? Id { get; set; }

    [JsonPropertyName("name")]
    public string Name { get; set; } = Name;

    [JsonPropertyName("category")]
    public Category? Category { get; set; }

    [JsonPropertyName("photoUrls")]
    public List<string> PhotoUrls { get; set; } = PhotoUrls;

    [JsonPropertyName("tags")]
    public List<Tag>? Tags { get; set; }

    /// <summary>
    /// pet status in the store
    /// </summary>
    [JsonPropertyName("status")]
    public StatusEnum? Status { get; set; }
}
