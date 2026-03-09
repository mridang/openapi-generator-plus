#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA2227 // Collection properties should be read only

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class Pet
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
    public string Name { get; set; }

    [JsonPropertyName("category")]
    public Category? Category { get; set; }

    [JsonPropertyName("photoUrls")]
    public List<string> PhotoUrls { get; set; }

    [JsonPropertyName("tags")]
    public List<Tag>? Tags { get; set; }

    /// <summary>
    /// pet status in the store
    /// </summary>
    [JsonPropertyName("status")]
    public StatusEnum? Status { get; set; }

    [JsonConstructor]
    public Pet(string Name, List<string> PhotoUrls)
    {
        this.Name = Name;
        this.PhotoUrls = PhotoUrls;
    }
}
