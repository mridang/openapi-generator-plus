#pragma warning disable CA1002 // Do not expose generic lists

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
        string path = "/store/order/{orderId}";
        path = path.Replace(
            "{" + "orderId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(orderId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        _ = await InvokeApiAsync<object>(
                "DELETE",
                path,
                queryParams,
                headerParams,
                null,
                [],
                "application/json",
                null
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Returns pet inventories by status
    /// </summary>
    /// <returns><![CDATA[Dictionary<string, int>]]></returns>
    public async Task<Dictionary<string, int>> GetInventoryAsync()
    {
        string path = "/store/inventory";

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        Dictionary<string, int>? result = await InvokeApiAsync<Dictionary<string, int>>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetInventoryAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Find purchase order by ID
    /// </summary>
    /// <param name="orderId">ID of order to return</param>
    /// <returns><![CDATA[Order]]></returns>
    public async Task<Order> GetOrderByIdAsync(long orderId)
    {
        string path = "/store/order/{orderId}";
        path = path.Replace(
            "{" + "orderId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(orderId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        Order? result = await InvokeApiAsync<Order>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetOrderByIdAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Place an order for a pet
    /// </summary>
    /// <param name="order"></param>
    /// <returns><![CDATA[Order]]></returns>
    public async Task<Order> PlaceOrderAsync(Order? order = default)
    {
        string path = "/store/order";

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        Order? result = await InvokeApiAsync<Order>(
                "POST",
                path,
                queryParams,
                headerParams,
                order,
                PlaceOrderAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }
}
