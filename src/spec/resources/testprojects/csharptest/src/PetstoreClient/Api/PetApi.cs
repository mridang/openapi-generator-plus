using PetstoreClient.Models;

namespace PetstoreClient.Api;

/// <summary>
/// PetApi provides methods for the Pet API group.
/// </summary>
public class PetApi : BaseApi
{
    private static readonly string[] AddPetAccepts = ["application/json"];
    private static readonly string[] FindPetsByStatusAccepts = ["application/json"];
    private static readonly string[] GetPetByIdAccepts = ["application/json"];
    private static readonly string[] UpdatePetAccepts = ["application/json"];

    public PetApi()
        : base() { }

    public PetApi(IApiClient apiClient, Configuration config)
        : base(apiClient, config) { }

    /// <summary>
    /// Add a new pet to the store
    /// </summary>
    /// <param name="pet">Create a new pet in the store</param>
    /// <returns>Pet</returns>
    public async Task<Pet> AddPetAsync(Pet pet)
    {
        var path = "/pet";

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        var result = await InvokeApiAsync<Pet>(
            "POST",
            path,
            queryParams,
            headerParams,
            pet,
            AddPetAccepts,
            "application/json"
        ).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Deletes a pet
    /// </summary>
    /// <param name="petId">Pet id to delete</param>
    public async Task DeletePetAsync(long petId)
    {
        var path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
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
    /// Finds Pets by status
    /// </summary>
    /// <param name="status">Status values that need to be considered for filter</param>
    /// <returns>List<Pet></returns>
    public async Task<List<Pet>> FindPetsByStatusAsync(string? status = default)
    {
        var path = "/pet/findByStatus";

        var queryParams = new Dictionary<string, object?>();
        if (status != null)
        {
            queryParams["status"] = ObjectSerializer.ToQueryValue(status, null);
        }

        var headerParams = new Dictionary<string, string>();

        var result = await InvokeApiAsync<List<Pet>>(
            "GET",
            path,
            queryParams,
            headerParams,
            null,
            FindPetsByStatusAccepts,
            "application/json"
        ).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Find pet by ID
    /// </summary>
    /// <param name="petId">ID of pet to return</param>
    /// <returns>Pet</returns>
    public async Task<Pet> GetPetByIdAsync(long petId)
    {
        var path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        var result = await InvokeApiAsync<Pet>(
            "GET",
            path,
            queryParams,
            headerParams,
            null,
            GetPetByIdAccepts,
            "application/json"
        ).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Update an existing pet
    /// </summary>
    /// <param name="petId">ID of pet to update</param>
    /// <param name="pet">Pet object that needs to be updated</param>
    /// <returns>Pet</returns>
    public async Task<Pet> UpdatePetAsync(long petId, Pet pet)
    {
        var path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        var result = await InvokeApiAsync<Pet>(
            "PUT",
            path,
            queryParams,
            headerParams,
            pet,
            UpdatePetAccepts,
            "application/json"
        ).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }
}
