using PetstoreClient;
using PetstoreClient.Models;
using Xunit;

namespace Test;

public class ComposedSchemaTest
{
    private readonly ObjectSerializer _serializer = new();

    [Fact]
    public void AllOfDeserializesPetWithOwner()
    {
        var json =
            "{\"name\":\"doggie\",\"photoUrls\":[\"http://example.com/photo.jpg\"],\"ownerName\":\"John\",\"ownerEmail\":\"john@example.com\"}";
        var result = _serializer.Deserialize<PetWithOwner>(json);
        Assert.NotNull(result);
        Assert.Equal("doggie", result!.Name);
        Assert.Equal("John", result.OwnerName);
        Assert.Equal("john@example.com", result.OwnerEmail);
    }

    [Fact]
    public void OneOfWithDiscriminatorDeserializesDryFood()
    {
        var json = "{\"foodType\":\"dry\",\"weightKg\":2.5}";
        var result = _serializer.Deserialize<PetFood>(json);
        Assert.NotNull(result);
        Assert.IsType<DryFood>(result);
        Assert.Equal("dry", ((DryFood)result!).FoodType);
    }

    [Fact]
    public void AnyOfDeserializesMedication()
    {
        var json = "{\"drugName\":\"Amoxicillin\",\"dosage\":\"500mg\"}";
        var result = _serializer.Deserialize<PetTreatment>(json);
        Assert.NotNull(result);
        Assert.NotNull(result!.ActualInstance);
        Assert.IsType<Medication>(result.ActualInstance);
        Assert.Equal("Amoxicillin", ((Medication)result.ActualInstance!).DrugName);
    }
}
