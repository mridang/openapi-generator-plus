#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only
#pragma warning disable CS0618 // Type or member is obsolete

using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

public class DryFood
{
    /// <example>null</example>
    [JsonRequired]
    [JsonPropertyName("foodType")]
    public string FoodType { get; set; }

    /// <example>null</example>
    [JsonRequired]
    [JsonPropertyName("weightKg")]
    public double WeightKg { get; set; }

    [System.Text.Json.Serialization.JsonConstructor]
    public DryFood(string FoodType, double WeightKg)
    {
        ArgumentNullException.ThrowIfNull(FoodType, nameof(FoodType));
        this.FoodType = FoodType;
        this.WeightKg = WeightKg;
    }
}
