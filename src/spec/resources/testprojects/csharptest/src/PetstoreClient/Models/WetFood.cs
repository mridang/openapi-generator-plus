#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA2227 // Collection properties should be read only

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class WetFood(string FoodType, int VolumeMl)
{
    [JsonPropertyName("foodType")]
    public string FoodType { get; set; } = FoodType;

    [JsonPropertyName("volumeMl")]
    public int VolumeMl { get; set; } = VolumeMl;
}
