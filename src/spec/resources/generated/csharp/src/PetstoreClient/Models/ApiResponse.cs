#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only
#pragma warning disable CS0618 // Type or member is obsolete

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class ApiResponse
{
    /// <example>null</example>
    [JsonPropertyName("code")]
    public int? Code { get; set; }

    /// <example>null</example>
    [JsonPropertyName("type")]
    public string? Type { get; set; }

    /// <example>null</example>
    [JsonPropertyName("message")]
    public string? Message { get; set; }
}
