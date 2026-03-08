using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class DryFood
{
    [JsonPropertyName("foodType")]
    public string FoodType { get; set; }

    [JsonPropertyName("weightKg")]
    public double WeightKg { get; set; }

    public DryFood() { }

    public DryFood(string FoodType, double WeightKg)
    {
        this.FoodType = FoodType;
        this.WeightKg = WeightKg;
    }
}
