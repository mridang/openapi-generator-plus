#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only
#pragma warning disable CS0618 // Type or member is obsolete

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

/// <seealso href="https://example.com/docs/pet">Learn more about the Pet model</seealso>
public class Pet(string Name, HashSet<string> PhotoUrls)
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

    /// <example>10</example>
    [JsonPropertyName("id")]
    public long? Id { get; set; }

    /// <example>doggie</example>
    [JsonPropertyName("name")]
    public string Name { get; set; } = Name;

    /// <example>null</example>
    [JsonPropertyName("category")]
    public Category? Category { get; set; }

    /// <example>null</example>
    [JsonPropertyName("photoUrls")]
    public HashSet<string> PhotoUrls { get; set; } = PhotoUrls;

    /// <example>null</example>
    [JsonPropertyName("tags")]
    public List<Tag>? Tags { get; set; }

    /// <summary>
    /// pet status in the store
    /// </summary>
    /// <example>null</example>
    /// <remarks>Deprecated.</remarks>
    [Obsolete("This property is deprecated.")]
    [JsonPropertyName("status")]
    public StatusEnum? Status { get; set; }
}
