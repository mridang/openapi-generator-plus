#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA2227 // Collection properties should be read only

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class Category
{
    [JsonPropertyName("id")]
    public long? Id { get; set; }

    [JsonPropertyName("name")]
    public string? Name { get; set; }
}
