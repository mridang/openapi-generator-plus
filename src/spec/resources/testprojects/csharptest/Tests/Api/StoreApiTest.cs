using PetstoreClient;
using PetstoreClient.Api;
using PetstoreClient.Models;
using Xunit;

namespace Tests.Api;

[Collection("Prism")]
public class StoreApiTest
{
    private readonly StoreApi _api;

    public StoreApiTest(Tests.PrismFixture prism)
    {
        var baseUrl = prism.BaseUrl;
        var config = new Configuration { BaseUrl = baseUrl };
        config.DefaultHeaders["Authorization"] = "Bearer test-token";
        _api = new StoreApi(new DefaultApiClient(), config);
    }

    [Fact]
    public async Task TestGetInventory()
    {
        var result = await _api.GetInventoryAsync();

        Assert.NotNull(result);
    }

    [Fact]
    public async Task TestPlaceOrder()
    {
        var order = new Order
        {
            Id = 1L,
            PetId = 12345L,
            Quantity = 1,
            ShipDate = DateTime.UtcNow,
            Status = Order.StatusEnum.Placed,
            Complete = false,
        };

        var result = await _api.PlaceOrderAsync(order);

        Assert.NotNull(result);
        Assert.NotNull(result.Id);
    }

    [Fact]
    public async Task TestGetOrderById()
    {
        var result = await _api.GetOrderByIdAsync(1L);

        Assert.NotNull(result);
        Assert.NotNull(result.Id);
    }

    [Fact]
    public async Task TestDeleteOrder()
    {
        await _api.DeleteOrderAsync(1L);
        Assert.True(true);
    }
}
