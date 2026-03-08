using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class WetFood
{
    [JsonPropertyName("foodType")]
    public string FoodType { get; set; }

    [JsonPropertyName("volumeMl")]
    public int VolumeMl { get; set; }

    public WetFood() { }

    public WetFood(string FoodType, int VolumeMl)
    {
        this.FoodType = FoodType;
        this.VolumeMl = VolumeMl;
    }
}
