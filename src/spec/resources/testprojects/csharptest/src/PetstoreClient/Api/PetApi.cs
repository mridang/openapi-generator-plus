#pragma warning disable CA1002 // Do not expose generic lists

using PetstoreClient.Auth;
using PetstoreClient.Models;

namespace PetstoreClient.Api;

/// <summary>
/// PetApi provides methods for the Pet API group.
/// </summary>
public class PetApi : BaseApi
{
    private static readonly string[] AddPetAccepts = ["application/json"];
    private static readonly string[] AddPetPhotosAccepts = ["application/json"];
    private static readonly string[] DownloadPetDocumentAccepts = ["application/octet-stream"];
    private static readonly string[] FindPetsByStatusAccepts = ["application/json"];
    private static readonly string[] GetPetAvatarAccepts = ["image/jpeg", "image/png"];
    private static readonly string[] GetPetAvatarThumbnailAccepts = ["application/json"];
    private static readonly string[] GetPetByIdAccepts = ["application/json"];
    private static readonly string[] GetPetPassportAccepts = ["application/json"];
    private static readonly string[] GetPetPhotoAccepts =
    [
        "image/jpeg",
        "image/png",
        "application/json",
    ];
    private static readonly string[] UpdatePetAccepts = ["application/json"];
    private static readonly string[] UploadPetCertificateAccepts = ["application/json"];
    private static readonly string[] UploadPetDocumentAccepts = ["application/json"];

    public PetApi()
        : base() { }

    public PetApi(IApiClient apiClient, Configuration config)
        : base(apiClient, config) { }

    /// <summary>
    /// Add a new pet to the store
    /// </summary>
    /// <param name="auth">Authenticator for this operation.</param>
    /// <param name="pet">Create a new pet in the store</param>
    /// <returns><![CDATA[Pet]]></returns>
    public async Task<Pet> AddPetAsync(IAuthenticator auth, Pet pet)
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
                "application/json",
                auth
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Add photos to the pet&#39;s gallery
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="files"></param>
    /// <param name="metadata"></param>
    /// <returns><![CDATA[List<Photo>]]></returns>
    public async Task<List<Photo>> AddPetPhotosAsync(
        long petId,
        List<System.IO.Stream> files,
        PhotoMetadata metadata
    )
    {
        string path = "/pet/{petId}/photos";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        Dictionary<string, object> formBody = [];
        formBody["files"] = files;
        formBody["metadata"] = metadata;

        List<Photo>? result = await InvokeApiAsync<List<Photo>>(
                "POST",
                path,
                queryParams,
                headerParams,
                formBody,
                AddPetPhotosAccepts,
                "multipart/form-data",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Deletes a pet
    /// </summary>
    /// <param name="auth">Authenticator for this operation.</param>
    /// <param name="petId">Pet id to delete</param>
    public async Task DeletePetAsync(IAuthenticator auth, long petId)
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
                "application/json",
                auth
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Download a vet document
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="documentId"></param>
    /// <returns><![CDATA[System.IO.Stream]]></returns>
    public async Task<System.IO.Stream> DownloadPetDocumentAsync(long petId, long documentId)
    {
        string path = "/pet/{petId}/documents/{documentId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );
        path = path.Replace(
            "{" + "documentId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(documentId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        System.IO.Stream? result = await InvokeApiAsync<System.IO.Stream>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                DownloadPetDocumentAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
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
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get the pet&#39;s profile photo
    /// </summary>
    /// <param name="petId"></param>
    /// <returns><![CDATA[System.IO.Stream]]></returns>
    public async Task<System.IO.Stream> GetPetAvatarAsync(long petId)
    {
        string path = "/pet/{petId}/avatar";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        System.IO.Stream? result = await InvokeApiAsync<System.IO.Stream>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetPetAvatarAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get the pet&#39;s avatar thumbnail as base64
    /// </summary>
    /// <param name="petId"></param>
    /// <returns><![CDATA[byte[]]]></returns>
    public async Task<byte[]> GetPetAvatarThumbnailAsync(long petId)
    {
        string path = "/pet/{petId}/avatar/thumbnail";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        byte[]? result = await InvokeApiAsync<byte[]>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetPetAvatarThumbnailAccepts,
                "application/json",
                null
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
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get the pet&#39;s passport
    /// </summary>
    /// <param name="petId"></param>
    /// <returns><![CDATA[PetPassport]]></returns>
    public async Task<PetPassport> GetPetPassportAsync(long petId)
    {
        string path = "/pet/{petId}/passport";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        PetPassport? result = await InvokeApiAsync<PetPassport>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetPetPassportAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get a photo or its metadata
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="photoId"></param>
    /// <returns><![CDATA[System.IO.Stream]]></returns>
    public async Task<System.IO.Stream> GetPetPhotoAsync(long petId, long photoId)
    {
        string path = "/pet/{petId}/photos/{photoId}";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );
        path = path.Replace(
            "{" + "photoId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(photoId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        System.IO.Stream? result = await InvokeApiAsync<System.IO.Stream>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetPetPhotoAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Set the pet&#39;s profile photo
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="body"></param>
    public async Task SetPetAvatarAsync(long petId, System.IO.Stream body)
    {
        string path = "/pet/{petId}/avatar";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        _ = await InvokeApiAsync<object>(
                "PUT",
                path,
                queryParams,
                headerParams,
                body,
                [],
                "image/jpeg",
                null
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Set the pet&#39;s avatar thumbnail as base64
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="setPetAvatarThumbnailRequest"></param>
    public async Task SetPetAvatarThumbnailAsync(
        long petId,
        SetPetAvatarThumbnailRequest setPetAvatarThumbnailRequest
    )
    {
        string path = "/pet/{petId}/avatar/thumbnail";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];

        _ = await InvokeApiAsync<object>(
                "PUT",
                path,
                queryParams,
                headerParams,
                setPetAvatarThumbnailRequest,
                [],
                "application/json",
                null
            )
            .ConfigureAwait(false);
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
                "application/json",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Upload the pet&#39;s adoption certificate
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="file"></param>
    /// <returns><![CDATA[ApiResponse]]></returns>
    public async Task<ApiResponse> UploadPetCertificateAsync(long petId, System.IO.Stream file)
    {
        string path = "/pet/{petId}/certificate";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        Dictionary<string, object> formBody = [];
        formBody["file"] = file;

        ApiResponse? result = await InvokeApiAsync<ApiResponse>(
                "POST",
                path,
                queryParams,
                headerParams,
                formBody,
                UploadPetCertificateAccepts,
                "multipart/form-data",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Attach a vet document or health record
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="file"></param>
    /// <param name="documentType"></param>
    /// <param name="notes"></param>
    /// <returns><![CDATA[ApiResponse]]></returns>
    public async Task<ApiResponse> UploadPetDocumentAsync(
        long petId,
        System.IO.Stream file,
        string? documentType = default,
        string? notes = default
    )
    {
        string path = "/pet/{petId}/documents";
        path = path.Replace(
            "{" + "petId" + "}",
            Uri.EscapeDataString(ObjectSerializer.ToPathValue(petId)),
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        Dictionary<string, object> formBody = [];
        formBody["file"] = file;
        if (documentType != null)
        {
            formBody["documentType"] = documentType;
        }
        if (notes != null)
        {
            formBody["notes"] = notes;
        }

        ApiResponse? result = await InvokeApiAsync<ApiResponse>(
                "POST",
                path,
                queryParams,
                headerParams,
                formBody,
                UploadPetDocumentAccepts,
                "multipart/form-data",
                null
            )
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Expected non-null response body");
    }
}
