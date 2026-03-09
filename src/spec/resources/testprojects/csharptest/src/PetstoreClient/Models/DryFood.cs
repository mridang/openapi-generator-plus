#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA2227 // Collection properties should be read only

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class DryFood
{
    [JsonPropertyName("foodType")]
    public string FoodType { get; set; }

    [JsonPropertyName("weightKg")]
    public double WeightKg { get; set; }

    [JsonConstructor]
    public DryFood(string FoodType, double WeightKg)
    {
        this.FoodType = FoodType;
        this.WeightKg = WeightKg;
    }
}
