#pragma warning disable CA1002 // Do not expose generic lists

using PetstoreClient.Models;

namespace PetstoreClient.Api;

/// <summary>
/// StoreApi provides methods for the Store API group.
/// Access to Petstore orders
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
        Task<ApiResult<object?>> task = DeleteOrderWithHttpInfoAsync(orderId);
        _ = await task.ConfigureAwait(false);
    }

    /// <summary>
    /// Delete purchase order by ID (with HTTP info)
    /// </summary>
    public async Task<ApiResult<object?>> DeleteOrderWithHttpInfoAsync(long orderId)
    {
        string path = "/store/order/{orderId}";
        path = path.Replace(
            "{" + nameof(orderId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(orderId),
                    orderId,
                    "path",
                    "long",
                    null,
                    "simple",
                    false
                )!,
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<object?>(
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
        Task<ApiResult<Dictionary<string, int>>> task = GetInventoryWithHttpInfoAsync();
        ApiResult<Dictionary<string, int>> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Returns pet inventories by status (with HTTP info)
    /// </summary>
    public async Task<ApiResult<Dictionary<string, int>>> GetInventoryWithHttpInfoAsync()
    {
        string path = "/store/inventory";

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<Dictionary<string, int>>(
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
    }

    /// <summary>
    /// Find purchase order by ID
    /// </summary>
    /// <param name="orderId">ID of order to return</param>
    /// <returns><![CDATA[Order]]></returns>
    public async Task<Order> GetOrderByIdAsync(long orderId)
    {
        Task<ApiResult<Order>> task = GetOrderByIdWithHttpInfoAsync(orderId);
        ApiResult<Order> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Find purchase order by ID (with HTTP info)
    /// </summary>
    public async Task<ApiResult<Order>> GetOrderByIdWithHttpInfoAsync(long orderId)
    {
        string path = "/store/order/{orderId}";
        path = path.Replace(
            "{" + nameof(orderId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(orderId),
                    orderId,
                    "path",
                    "long",
                    null,
                    "simple",
                    false
                )!,
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<Order>(
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
    }

    /// <summary>
    /// Place an order for a pet
    /// </summary>
    /// <param name="order"></param>
    /// <returns><![CDATA[Order]]></returns>
    public async Task<Order> PlaceOrderAsync(Order? order)
    {
        Task<ApiResult<Order>> task = PlaceOrderWithHttpInfoAsync(order);
        ApiResult<Order> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Place an order for a pet (with HTTP info)
    /// </summary>
    public async Task<ApiResult<Order>> PlaceOrderWithHttpInfoAsync(Order? order)
    {
        string path = "/store/order";

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<Order>(
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
    }
}
