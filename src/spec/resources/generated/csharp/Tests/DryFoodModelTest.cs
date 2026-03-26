using System.Text.Json;
using PetstoreClient.Models;
using Xunit;

namespace Tests;

public class DryFoodModelTest
{
    [Fact]
    public void RequiresFoodTypeField()
    {
        Assert.Throws<ArgumentNullException>(() => new DryFood(null!, 2.5));
    }

    [Fact]
    public void RequiresWeightKgField()
    {
        var ex = Assert.ThrowsAny<JsonException>(
            () => JsonSerializer.Deserialize<DryFood>("{\"foodType\":\"kibble\"}")
        );
    }

    [Fact]
    public void SerializesToJson()
    {
        var food = new DryFood("kibble", 2.5);

        var json = JsonSerializer.Serialize(food);
        var restored = JsonSerializer.Deserialize<DryFood>(json);

        Assert.NotNull(restored);
        Assert.Equal("kibble", restored!.FoodType);
        Assert.Equal(2.5, restored.WeightKg);
    }
}
