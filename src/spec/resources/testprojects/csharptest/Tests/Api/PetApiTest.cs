using PetstoreClient;
using PetstoreClient.Api;
using PetstoreClient.Models;
using Xunit;

namespace Tests.Api;

public class PetApiTest
{
    private readonly PetApi _api;

    public PetApiTest()
    {
        var baseUrl = Environment.GetEnvironmentVariable("API_BASE_URL") ?? "http://localhost:4010";
        var config = new Configuration { BaseUrl = baseUrl };
        config.DefaultHeaders["Authorization"] = "Bearer test-token";
        _api = new PetApi(new DefaultApiClient(), config);
    }

    [Fact]
    public async Task TestAddPet()
    {
        var pet = new Pet("TestDog", new List<string> { "http://example.com/photo.jpg" })
        {
            Id = 12345L,
            Status = Pet.StatusEnum.Available,
        };

        var result = await _api.AddPetAsync(pet);

        Assert.NotNull(result);
        Assert.NotNull(result.Name);
    }

    [Fact]
    public async Task TestFindPetsByStatus()
    {
        var result = await _api.FindPetsByStatusAsync("available");

        Assert.NotNull(result);
        Assert.NotEmpty(result);
        Assert.IsType<Pet>(result[0]);
    }

    [Fact]
    public async Task TestGetPetById()
    {
        var result = await _api.GetPetByIdAsync(1L);

        Assert.NotNull(result);
        Assert.NotNull(result.Id);
        Assert.NotNull(result.Name);
    }

    [Fact]
    public async Task TestUpdatePet()
    {
        var pet = new Pet("UpdatedDog", new List<string> { "http://example.com/updated.jpg" })
        {
            Id = 1L,
            Status = Pet.StatusEnum.Pending,
        };

        var result = await _api.UpdatePetAsync(1L, pet);

        Assert.NotNull(result);
    }

    [Fact]
    public async Task TestDeletePet()
    {
        await _api.DeletePetAsync(1L);
        Assert.True(true);
    }
}
