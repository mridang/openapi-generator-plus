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
    public DateTime? ShipDate { get; set; }

    /// <summary>
    /// Order Status
    /// </summary>
    [JsonPropertyName("status")]
    public StatusEnum? Status { get; set; }

    [JsonPropertyName("complete")]
    public bool? Complete { get; set; }
}
