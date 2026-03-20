#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
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

    [JsonPropertyName("id")]
    public long? Id { get; set; }

    [JsonPropertyName("petId")]
    public long? PetId { get; set; }

    [JsonPropertyName("quantity")]
    public int? Quantity { get; set; }

    [JsonPropertyName("shipDate")]
    public DateTimeOffset? ShipDate { get; set; }

    /// <summary>
    /// Order Status
    /// </summary>
    [JsonPropertyName("status")]
    public StatusEnum? Status { get; set; }

    [JsonPropertyName("complete")]
    public bool? Complete { get; set; }
}
