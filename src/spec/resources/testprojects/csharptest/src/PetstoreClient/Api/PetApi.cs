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
    /// <returns><![CDATA[Pet]]></returns>
    public async Task<Pet> AddPetAsync(Pet pet)
    {
        string path = "/pet";

        Dictionary<string, object?> queryParams = [];

        Dictionary<string, string> headerParams = [];

        Pet? result = await InvokeApiAsync<Pet>(
                "POST",
                path,
                queryParams,
                headerParams,
                pet,
                AddPetAccepts,
                "application/json"
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Deletes a pet
    /// </summary>
    /// <param name="petId">Pet id to delete</param>
    public async Task DeletePetAsync(long petId)
    {
        string path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
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
                "application/json"
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Finds Pets by status
    /// </summary>
    /// <param name="status">Status values that need to be considered for filter</param>
    /// <returns><![CDATA[List<Pet>]]></returns>
    public async Task<List<Pet>> FindPetsByStatusAsync(string? status = default)
    {
        string path = "/pet/findByStatus";

        Dictionary<string, object?> queryParams = [];
        if (status != null)
        {
            queryParams["status"] = ObjectSerializer.ToQueryValue(status, null);
        }

        Dictionary<string, string> headerParams = [];

        List<Pet>? result = await InvokeApiAsync<List<Pet>>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                FindPetsByStatusAccepts,
                "application/json"
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Find pet by ID
    /// </summary>
    /// <param name="petId">ID of pet to return</param>
    /// <returns><![CDATA[Pet]]></returns>
    public async Task<Pet> GetPetByIdAsync(long petId)
    {
        string path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];

        Dictionary<string, string> headerParams = [];

        Pet? result = await InvokeApiAsync<Pet>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetPetByIdAccepts,
                "application/json"
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Update an existing pet
    /// </summary>
    /// <param name="petId">ID of pet to update</param>
    /// <param name="pet">Pet object that needs to be updated</param>
    /// <returns><![CDATA[Pet]]></returns>
    public async Task<Pet> UpdatePetAsync(long petId, Pet pet)
    {
        string path = "/pet/{petId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];

        Dictionary<string, string> headerParams = [];

        Pet? result = await InvokeApiAsync<Pet>(
                "PUT",
                path,
                queryParams,
                headerParams,
                pet,
                UpdatePetAccepts,
                "application/json"
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }
}
