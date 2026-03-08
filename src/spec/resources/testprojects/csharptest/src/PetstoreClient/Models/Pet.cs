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

    [JsonPropertyName("name")]
    public string Name { get; set; }

    [JsonPropertyName("photoUrls")]
    public List<string> PhotoUrls { get; set; }

    [JsonPropertyName("id")]
    public long? Id { get; set; }

    [JsonPropertyName("category")]
    public Category? Category { get; set; }

    [JsonPropertyName("tags")]
    public List<Tag>? Tags { get; set; }

    /// <summary>
    /// pet status in the store
    /// </summary>
    [JsonPropertyName("status")]
    public StatusEnum? Status { get; set; }

    public Pet() { }

    public Pet(string Name, List<string> PhotoUrls)
    {
        this.Name = Name;
        this.PhotoUrls = PhotoUrls;
    }
}
