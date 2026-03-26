using System.Text.Json;
using PetstoreClient.Models;
using Xunit;

namespace Tests;

#pragma warning disable CS0618 // Type or member is obsolete

public class PetModelTest
{
    [Fact]
    public void RequiresNameField()
    {
        Assert.Throws<ArgumentNullException>(() => new Pet(null!, new HashSet<string>()));
    }

    [Fact]
    public void RequiresPhotoUrlsField()
    {
        var ex = Assert.ThrowsAny<JsonException>(
            () => JsonSerializer.Deserialize<Pet>("{\"name\":\"doggie\"}")
        );
    }

    [Fact]
    public void RejectsInvalidStatusEnum()
    {
        var ex = Assert.ThrowsAny<JsonException>(
            () =>
                JsonSerializer.Deserialize<Pet>(
                    "{\"name\":\"doggie\",\"photoUrls\":[],\"status\":\"invalid\"}"
                )
        );
    }

    [Fact]
    public void SerializesToJson()
    {
        var pet = new Pet("doggie", new HashSet<string> { "http://photo.jpg" }) { Id = 1 };

        var json = JsonSerializer.Serialize(pet);
        var restored = JsonSerializer.Deserialize<Pet>(json);

        Assert.NotNull(restored);
        Assert.Equal("doggie", restored!.Name);
        Assert.Equal(1, restored.Id);
        Assert.Contains("http://photo.jpg", restored.PhotoUrls);
    }
}
