#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1724 // Type names should not match namespaces
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only
#pragma warning disable CS0618 // Type or member is obsolete

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class Order
{
    [JsonConverter(typeof(JsonStringEnumConverter))]
    public enum StatusEnum
    {
        [JsonStringEnumMemberName("placed")]
        Placed,

        [JsonStringEnumMemberName("approved")]
        Approved,

        [JsonStringEnumMemberName("delivered")]
        Delivered,
    }

    /// <example>10</example>
    [JsonPropertyName("id")]
    public long? Id { get; set; }

    /// <example>198772</example>
    [JsonPropertyName("petId")]
    public long? PetId { get; set; }

    /// <example>7</example>
    [JsonPropertyName("quantity")]
    public int? Quantity { get; set; }

    /// <example>null</example>
    [JsonPropertyName("shipDate")]
    public DateTimeOffset? ShipDate { get; set; }

    /// <summary>
    /// Order Status
    /// </summary>
    /// <example>approved</example>
    [JsonPropertyName("status")]
    public StatusEnum? Status { get; set; }

    /// <example>null</example>
    [JsonPropertyName("complete")]
    public bool? Complete { get; set; }
}
