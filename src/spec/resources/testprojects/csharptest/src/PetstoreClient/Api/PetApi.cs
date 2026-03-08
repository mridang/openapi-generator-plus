using System.Text.Json;
using PetstoreClient.Models;

namespace PetstoreClient.Api;

/// <summary>
/// PetApi provides methods for the Pet API group.
/// </summary>
public class PetApi : BaseApi
{
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
        ArgumentNullException.ThrowIfNull(pet, nameof(pet));
        var path = "/pet";

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        return await InvokeApiAsync<Pet>(
            "POST",
            path,
            queryParams,
            headerParams,
            pet,
            new[] { "application/json" },
            "application/json"
        );
    }

    /// <summary>
    /// Deletes a pet
    /// </summary>
    /// <param name="petId">Pet id to delete</param>
    public async Task<object?> DeletePetAsync(long petId)
    {
        ArgumentNullException.ThrowIfNull(petId, nameof(petId));
        var path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId))
        );

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        return await InvokeApiAsync<object>(
            "DELETE",
            path,
            queryParams,
            headerParams,
            null,
            Array.Empty<string>(),
            "application/json"
        );
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

        return await InvokeApiAsync<List<Pet>>(
            "GET",
            path,
            queryParams,
            headerParams,
            null,
            new[] { "application/json" },
            "application/json"
        );
    }

    /// <summary>
    /// Find pet by ID
    /// </summary>
    /// <param name="petId">ID of pet to return</param>
    /// <returns>Pet</returns>
    public async Task<Pet> GetPetByIdAsync(long petId)
    {
        ArgumentNullException.ThrowIfNull(petId, nameof(petId));
        var path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId))
        );

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        return await InvokeApiAsync<Pet>(
            "GET",
            path,
            queryParams,
            headerParams,
            null,
            new[] { "application/json" },
            "application/json"
        );
    }

    /// <summary>
    /// Update an existing pet
    /// </summary>
    /// <param name="petId">ID of pet to update</param>
    /// <param name="pet">Pet object that needs to be updated</param>
    /// <returns>Pet</returns>
    public async Task<Pet> UpdatePetAsync(long petId, Pet pet)
    {
        ArgumentNullException.ThrowIfNull(petId, nameof(petId));
        ArgumentNullException.ThrowIfNull(pet, nameof(pet));
        var path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId))
        );

        var queryParams = new Dictionary<string, object?>();

        var headerParams = new Dictionary<string, string>();

        return await InvokeApiAsync<Pet>(
            "PUT",
            path,
            queryParams,
            headerParams,
            pet,
            new[] { "application/json" },
            "application/json"
        );
    }
}
