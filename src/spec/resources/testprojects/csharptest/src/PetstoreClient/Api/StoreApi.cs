using PetstoreClient.Models;

namespace PetstoreClient.Api;

/// <summary>
/// StoreApi provides methods for the Store API group.
/// </summary>
public class StoreApi : BaseApi
{
    private static readonly string[] GetInventoryAccepts = ["application/json"];
    private static readonly string[] GetOrderByIdAccepts = ["application/json"];
    private static readonly string[] PlaceOrderAccepts = ["application/json"];

    public StoreApi()
        : base() { }

    public StoreApi(IApiClient apiClient, Configuration config)
        : base(apiClient, config) { }

    /// <summary>
    /// Delete purchase order by ID
    /// </summary>
    /// <param name="orderId">ID of the order to delete</param>
    public async Task DeleteOrderAsync(long orderId)
    {
        var path = "/store/order/{orderId}";
        path = path.Replace(
            "{" + "orderId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(orderId)),
            StringComparison.Ordinal
        );

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        await InvokeApiAsync<object>(
            "DELETE",
            path,
            queryParams,
            headerParams,
            null,
            Array.Empty<string>(),
            "application/json"
        ).ConfigureAwait(false);
    }

    /// <summary>
    /// Returns pet inventories by status
    /// </summary>
    /// <returns>Dictionary<string, int></returns>
    public async Task<Dictionary<string, int>> GetInventoryAsync()
    {
        var path = "/store/inventory";

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        var result = await InvokeApiAsync<Dictionary<string, int>>(
            "GET",
            path,
            queryParams,
            headerParams,
            null,
            GetInventoryAccepts,
            "application/json"
        ).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Find purchase order by ID
    /// </summary>
    /// <param name="orderId">ID of order to return</param>
    /// <returns>Order</returns>
    public async Task<Order> GetOrderByIdAsync(long orderId)
    {
        var path = "/store/order/{orderId}";
        path = path.Replace(
            "{" + "orderId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(orderId)),
            StringComparison.Ordinal
        );

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        var result = await InvokeApiAsync<Order>(
            "GET",
            path,
            queryParams,
            headerParams,
            null,
            GetOrderByIdAccepts,
            "application/json"
        ).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Place an order for a pet
    /// </summary>
    /// <param name="order"></param>
    /// <returns>Order</returns>
    public async Task<Order> PlaceOrderAsync(Order? order = default)
    {
        var path = "/store/order";

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        var result = await InvokeApiAsync<Order>(
            "POST",
            path,
            queryParams,
            headerParams,
            order,
            PlaceOrderAccepts,
            "application/json"
        ).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }
}
