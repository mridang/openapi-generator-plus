#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI properties should not be strings
#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

using PetstoreClient.Auth;
using PetstoreClient.Models;

namespace PetstoreClient.Api;

/// <summary>
/// Options for the AddPetPhotos operation.
/// </summary>
public sealed class AddPetPhotosOptions
{
    /// <summary></summary>
    public required List<System.IO.Stream> Files { get; init; }

    /// <summary></summary>
    public required PhotoMetadata Metadata { get; init; }
}

/// <summary>
/// Options for the FindPetsByStatus operation.
/// </summary>
public sealed class FindPetsByStatusOptions
{
    /// <summary> Status values that need to be considered for filter</summary>
    public string? Status { get; init; }

    /// <summary> Filter criteria as key-value pairs</summary>
    public Dictionary<string, string>? Filter { get; init; }
}

/// <summary>
/// Options for the GetPetTag operation.
/// </summary>
public sealed class GetPetTagOptions
{
    /// <summary></summary>
    public List<string>? Colors { get; init; }

    /// <summary></summary>
    public List<string>? Sizes { get; init; }

    /// <summary></summary>
    public string? Filter { get; init; }
}

/// <summary>
/// Options for the UploadPetCertificate operation.
/// </summary>
public sealed class UploadPetCertificateOptions
{
    /// <summary></summary>
    public required System.IO.Stream File { get; init; }
}

/// <summary>
/// Options for the UploadPetDocument operation.
/// </summary>
public sealed class UploadPetDocumentOptions
{
    /// <summary></summary>
    public required System.IO.Stream File { get; init; }

    /// <summary></summary>
    public string? DocumentType { get; init; }

    /// <summary></summary>
    public string? Notes { get; init; }
}

/// <summary>
/// Server type for the GetExternalPetInfo operation.
/// </summary>
public abstract class GetExternalPetInfoServer
{
    /// <summary>Gets the server URL.</summary>
    public abstract string Url { get; }
}

public sealed class Server0 : GetExternalPetInfoServer
{
    /// <inheritdoc />
    public override string Url => "https://external-api.example.com/v1";
}

/// <summary>
/// Server type for the GetMultiServerPetInfo operation.
/// </summary>
public abstract class GetMultiServerPetInfoServer
{
    /// <summary>Gets the server URL.</summary>
    public abstract string Url { get; }
}

/// <summary>Enum for the region server variable.</summary>
public sealed class RegionValue
{
    private RegionValue(string value)
    {
        Value = value;
    }

    /// <summary>Gets the string value.</summary>
    public string Value { get; }

    /// <summary>The us value.</summary>
    public static RegionValue US { get; } = new("us");

    /// <summary>The eu value.</summary>
    public static RegionValue EU { get; } = new("eu");

    /// <summary>The ap value.</summary>
    public static RegionValue AP { get; } = new("ap");
}

/// <summary>Primary</summary>
public sealed class Primary : GetMultiServerPetInfoServer
{
    /// <inheritdoc />
    public override string Url => "https://primary.example.com/v1";
}

/// <summary>Regional</summary>
public sealed class Regional(RegionValue region) : GetMultiServerPetInfoServer
{
    /// <summary>Gets the region value.</summary>
    public RegionValue Region { get; } = region;

    /// <inheritdoc />
    public override string Url
    {
        get
        {
            string url = "https://{region}.example.com/v1";
            url = url.Replace("{" + "region" + "}", Region.Value, StringComparison.Ordinal);
            return url;
        }
    }
}

/// <summary>
/// Server type for the GetStagingPetInfo operation.
/// </summary>
public abstract class GetStagingPetInfoServer
{
    /// <summary>Gets the server URL.</summary>
    public abstract string Url { get; }
}

/// <summary>Enum for the environment server variable.</summary>
public sealed class EnvironmentValue
{
    private EnvironmentValue(string value)
    {
        Value = value;
    }

    /// <summary>Gets the string value.</summary>
    public string Value { get; }

    /// <summary>The staging value.</summary>
    public static EnvironmentValue STAGING { get; } = new("staging");

    /// <summary>The sandbox value.</summary>
    public static EnvironmentValue SANDBOX { get; } = new("sandbox");
}

/// <summary>Enum for the version server variable.</summary>
public sealed class VersionValue
{
    private VersionValue(string value)
    {
        Value = value;
    }

    /// <summary>Gets the string value.</summary>
    public string Value { get; }

    /// <summary>The v2 value.</summary>
    public static VersionValue V2 { get; } = new("v2");

    /// <summary>The v3 value.</summary>
    public static VersionValue V3 { get; } = new("v3");
}

/// <summary>Staging server</summary>
public sealed class StagingServer(EnvironmentValue environment, VersionValue version)
    : GetStagingPetInfoServer
{
    /// <summary>Gets the environment value.</summary>
    public EnvironmentValue Environment { get; } = environment;

    /// <summary>Gets the version value.</summary>
    public VersionValue Version { get; } = version;

    /// <inheritdoc />
    public override string Url
    {
        get
        {
            string url = "https://{environment}.example.com/api/{version}";
            url = url.Replace(
                "{" + "environment" + "}",
                Environment.Value,
                StringComparison.Ordinal
            );
            url = url.Replace("{" + "version" + "}", Version.Value, StringComparison.Ordinal);
            return url;
        }
    }
}

/// <summary>
/// PetApi provides methods for the Pet API group.
/// Everything about your Pets
/// </summary>
/// <seealso href="https://example.com/docs/pets">Find out more about pets</seealso>
public class PetApi : BaseApi
{
    private static readonly string[] AddPetAccepts = ["application/json"];

    private static readonly string[] AddPetPhotosAccepts = ["application/json"];

    private static readonly string[] DownloadPetDocumentAccepts = ["application/octet-stream"];

    private static readonly string[] FindPetsByStatusAccepts = ["application/json"];

    private static readonly string[] GetExternalPetInfoAccepts = ["application/json"];

    private static readonly string[] GetMultiServerPetInfoAccepts = ["application/json"];

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

    private static readonly string[] GetPetTagAccepts = ["application/json"];

    private static readonly string[] GetStagingPetInfoAccepts = ["application/json"];

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
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<Pet> AddPetAsync(IAuthenticator auth, Pet pet)
    {
        Task<ApiResult<Pet>> task = AddPetWithHttpInfoAsync(auth, pet);
        ApiResult<Pet> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Add a new pet to the store (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<Pet>> AddPetWithHttpInfoAsync(IAuthenticator auth, Pet pet)
    {
        string path = "/pet";

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<Pet>(
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
    }

    /// <summary>
    /// Add photos to the pet&#39;s gallery
    /// </summary>
    /// <remarks>Uploads one or more photos with structured metadata. The metadata part is serialised as JSON within the multipart body.</remarks>
    /// <param name="petId"></param>
    /// <param name="options">Options for query, header, and form parameters.</param>
    /// <returns><![CDATA[List<Photo>]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<List<Photo>> AddPetPhotosAsync(long petId, AddPetPhotosOptions options)
    {
        Task<ApiResult<List<Photo>>> task = AddPetPhotosWithHttpInfoAsync(petId, options);
        ApiResult<List<Photo>> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Add photos to the pet&#39;s gallery (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<List<Photo>>> AddPetPhotosWithHttpInfoAsync(
        long petId,
        AddPetPhotosOptions options
    )
    {
        ArgumentNullException.ThrowIfNull(options);
        string path = "/pet/{petId}/photos";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
        Dictionary<string, object> formBody = [];
        formBody["files"] = options.Files;
        formBody["metadata"] = options.Metadata;
        return await InvokeApiForResultAsync<List<Photo>>(
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
    }

    /// <summary>
    /// Deletes a pet
    /// </summary>
    /// <param name="auth">Authenticator for this operation.</param>
    /// <param name="petId">Pet id to delete</param>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task DeletePetAsync(IAuthenticator auth, long petId)
    {
        Task<ApiResult<object?>> task = DeletePetWithHttpInfoAsync(auth, petId);
        _ = await task.ConfigureAwait(false);
    }

    /// <summary>
    /// Deletes a pet (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<object?>> DeletePetWithHttpInfoAsync(
        IAuthenticator auth,
        long petId
    )
    {
        string path = "/pet/{petId}";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
                auth
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Download a vet document
    /// </summary>
    /// <remarks>Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.</remarks>
    /// <param name="petId"></param>
    /// <param name="documentId"></param>
    /// <returns><![CDATA[System.IO.Stream]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<System.IO.Stream> DownloadPetDocumentAsync(long petId, long documentId)
    {
        Task<ApiResult<System.IO.Stream>> task = DownloadPetDocumentWithHttpInfoAsync(
            petId,
            documentId
        );
        ApiResult<System.IO.Stream> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Download a vet document (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<System.IO.Stream>> DownloadPetDocumentWithHttpInfoAsync(
        long petId,
        long documentId
    )
    {
        string path = "/pet/{petId}/documents/{documentId}";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
                    "path",
                    "long",
                    null,
                    "simple",
                    false
                )!,
            StringComparison.Ordinal
        );
        path = path.Replace(
            "{" + nameof(documentId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(documentId),
                    documentId,
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
        return await InvokeApiForResultAsync<System.IO.Stream>(
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
    }

    /// <summary>
    /// Finds Pets by status
    /// </summary>
    /// <param name="options">Options for query, header, and form parameters.</param>
    /// <returns><![CDATA[List<Pet>]]></returns>
    /// <seealso href="https://example.com/docs/filtering">Find out more about filtering</seealso>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    [Obsolete("This operation is deprecated.")]
    public async Task<List<Pet>> FindPetsByStatusAsync(FindPetsByStatusOptions options)
    {
        Task<ApiResult<List<Pet>>> task = FindPetsByStatusWithHttpInfoAsync(options);
        ApiResult<List<Pet>> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Finds Pets by status (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<List<Pet>>> FindPetsByStatusWithHttpInfoAsync(
        FindPetsByStatusOptions options
    )
    {
        ArgumentNullException.ThrowIfNull(options);
        string path = "/pet/findByStatus";

        Dictionary<string, object?> queryParams = [];
        if (options.Status != null)
        {
            queryParams["status"] = ValueSerializer.SerializeStyled(
                "status",
                options.Status,
                "query",
                "string",
                null,
                "form",
                true
            );
        }
        if (options.Filter != null)
        {
            Dictionary<string, string> deepObj = ValueSerializer.SerializeDeepObject(
                "filter",
                options.Filter as IDictionary<string, object?>
            );
            foreach (KeyValuePair<string, string> entry in deepObj)
            {
                queryParams[entry.Key] = entry.Value;
            }
        }
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<List<Pet>>(
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
    }

    /// <summary>
    /// Get external pet info
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="server">Optional per-operation server override.</param>
    /// <returns><![CDATA[Pet]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<Pet> GetExternalPetInfoAsync(
        long petId,
        GetExternalPetInfoServer? server = null
    )
    {
        Task<ApiResult<Pet>> task = GetExternalPetInfoWithHttpInfoAsync(petId, server);
        ApiResult<Pet> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get external pet info (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<Pet>> GetExternalPetInfoWithHttpInfoAsync(
        long petId,
        GetExternalPetInfoServer? server = null
    )
    {
        string path = "/pet/{petId}/external";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
                    "path",
                    "long",
                    null,
                    "simple",
                    false
                )!,
            StringComparison.Ordinal
        );
        string serverUrl = server != null ? server.Url : "https://external-api.example.com/v1";
        if (
            serverUrl.StartsWith("http://", StringComparison.Ordinal)
            || serverUrl.StartsWith("https://", StringComparison.Ordinal)
        )
        {
            path = serverUrl + path;
        }

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<Pet>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetExternalPetInfoAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Get multi-server pet info
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="server">Optional per-operation server override.</param>
    /// <returns><![CDATA[Pet]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<Pet> GetMultiServerPetInfoAsync(
        long petId,
        GetMultiServerPetInfoServer? server = null
    )
    {
        Task<ApiResult<Pet>> task = GetMultiServerPetInfoWithHttpInfoAsync(petId, server);
        ApiResult<Pet> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get multi-server pet info (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<Pet>> GetMultiServerPetInfoWithHttpInfoAsync(
        long petId,
        GetMultiServerPetInfoServer? server = null
    )
    {
        string path = "/pet/{petId}/multi";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
                    "path",
                    "long",
                    null,
                    "simple",
                    false
                )!,
            StringComparison.Ordinal
        );
        string serverUrl = server != null ? server.Url : "https://primary.example.com/v1";
        if (
            serverUrl.StartsWith("http://", StringComparison.Ordinal)
            || serverUrl.StartsWith("https://", StringComparison.Ordinal)
        )
        {
            path = serverUrl + path;
        }

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<Pet>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetMultiServerPetInfoAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Get the pet&#39;s profile photo
    /// </summary>
    /// <remarks>Returns the raw image bytes of the pet&#39;s current avatar.</remarks>
    /// <param name="petId"></param>
    /// <returns><![CDATA[System.IO.Stream]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<System.IO.Stream> GetPetAvatarAsync(long petId)
    {
        Task<ApiResult<System.IO.Stream>> task = GetPetAvatarWithHttpInfoAsync(petId);
        ApiResult<System.IO.Stream> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get the pet&#39;s profile photo (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<System.IO.Stream>> GetPetAvatarWithHttpInfoAsync(long petId)
    {
        string path = "/pet/{petId}/avatar";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
        return await InvokeApiForResultAsync<System.IO.Stream>(
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
    }

    /// <summary>
    /// Get the pet&#39;s avatar thumbnail as base64
    /// </summary>
    /// <remarks>Returns a compact base64-encoded thumbnail suitable for embedding directly in mobile UI without a separate image request.</remarks>
    /// <param name="petId"></param>
    /// <returns><![CDATA[byte[]]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<byte[]> GetPetAvatarThumbnailAsync(long petId)
    {
        Task<ApiResult<byte[]>> task = GetPetAvatarThumbnailWithHttpInfoAsync(petId);
        ApiResult<byte[]> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get the pet&#39;s avatar thumbnail as base64 (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<byte[]>> GetPetAvatarThumbnailWithHttpInfoAsync(long petId)
    {
        string path = "/pet/{petId}/avatar/thumbnail";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
        return await InvokeApiForResultAsync<byte[]>(
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
    }

    /// <summary>
    /// Find pet by ID
    /// </summary>
    /// <remarks>Returns a single pet</remarks>
    /// <param name="petId">ID of pet to return</param>
    /// <returns><![CDATA[Pet]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    [Obsolete("This operation is deprecated.")]
    public async Task<Pet> GetPetByIdAsync(long petId)
    {
        Task<ApiResult<Pet>> task = GetPetByIdWithHttpInfoAsync(petId);
        ApiResult<Pet> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Find pet by ID (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<Pet>> GetPetByIdWithHttpInfoAsync(long petId)
    {
        string path = "/pet/{petId}";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
        return await InvokeApiForResultAsync<Pet>(
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
    }

    /// <summary>
    /// Get the pet&#39;s passport
    /// </summary>
    /// <remarks>Returns a single JSON document combining the pet&#39;s profile with an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for mobile clients that prefer a single-request workflow.</remarks>
    /// <param name="petId"></param>
    /// <returns><![CDATA[PetPassport]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<PetPassport> GetPetPassportAsync(long petId)
    {
        Task<ApiResult<PetPassport>> task = GetPetPassportWithHttpInfoAsync(petId);
        ApiResult<PetPassport> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get the pet&#39;s passport (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<PetPassport>> GetPetPassportWithHttpInfoAsync(long petId)
    {
        string path = "/pet/{petId}/passport";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
        return await InvokeApiForResultAsync<PetPassport>(
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
    }

    /// <summary>
    /// Get a photo or its metadata
    /// </summary>
    /// <remarks>Returns the raw image bytes or JSON metadata depending on the Accept header sent by the client.</remarks>
    /// <param name="petId"></param>
    /// <param name="photoId"></param>
    /// <returns><![CDATA[System.IO.Stream]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<System.IO.Stream> GetPetPhotoAsync(long petId, long photoId)
    {
        Task<ApiResult<System.IO.Stream>> task = GetPetPhotoWithHttpInfoAsync(petId, photoId);
        ApiResult<System.IO.Stream> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get a photo or its metadata (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<System.IO.Stream>> GetPetPhotoWithHttpInfoAsync(
        long petId,
        long photoId
    )
    {
        string path = "/pet/{petId}/photos/{photoId}";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
                    "path",
                    "long",
                    null,
                    "simple",
                    false
                )!,
            StringComparison.Ordinal
        );
        path = path.Replace(
            "{" + nameof(photoId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(photoId),
                    photoId,
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
        return await InvokeApiForResultAsync<System.IO.Stream>(
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
    }

    /// <summary>
    /// Get a tag for a pet
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="tagName"></param>
    /// <param name="options">Options for query, header, and form parameters.</param>
    /// <returns><![CDATA[Pet]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<Pet> GetPetTagAsync(long petId, string tagName, GetPetTagOptions options)
    {
        Task<ApiResult<Pet>> task = GetPetTagWithHttpInfoAsync(petId, tagName, options);
        ApiResult<Pet> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get a tag for a pet (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<Pet>> GetPetTagWithHttpInfoAsync(
        long petId,
        string tagName,
        GetPetTagOptions options
    )
    {
        ArgumentNullException.ThrowIfNull(options);
        string path = "/pet/{petId}/tag/{tagName}";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
                    "path",
                    "long",
                    null,
                    "matrix",
                    false
                )!,
            StringComparison.Ordinal
        );
        path = path.Replace(
            "{" + nameof(tagName) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(tagName),
                    tagName,
                    "path",
                    "string",
                    null,
                    "label",
                    false
                )!,
            StringComparison.Ordinal
        );

        Dictionary<string, object?> queryParams = [];
        if (options.Colors != null)
        {
            queryParams["colors"] = ValueSerializer.SerializeStyled(
                "colors",
                options.Colors,
                "query",
                "List<string>",
                "pipes",
                "pipeDelimited",
                false
            );
        }
        if (options.Sizes != null)
        {
            queryParams["sizes"] = ValueSerializer.SerializeStyled(
                "sizes",
                options.Sizes,
                "query",
                "List<string>",
                "ssv",
                "spaceDelimited",
                false
            );
        }
        queryParams["filter"] =
            options.Filter != null
                ? ValueSerializer.SerializeStyled(
                    "filter",
                    options.Filter,
                    "query",
                    "string",
                    null,
                    "form",
                    true
                )
                : "";
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<Pet>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetPetTagAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Get staging pet info
    /// </summary>
    /// <param name="petId"></param>
    /// <param name="server">Optional per-operation server override.</param>
    /// <returns><![CDATA[Pet]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<Pet> GetStagingPetInfoAsync(
        long petId,
        GetStagingPetInfoServer? server = null
    )
    {
        Task<ApiResult<Pet>> task = GetStagingPetInfoWithHttpInfoAsync(petId, server);
        ApiResult<Pet> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Get staging pet info (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<Pet>> GetStagingPetInfoWithHttpInfoAsync(
        long petId,
        GetStagingPetInfoServer? server = null
    )
    {
        string path = "/pet/{petId}/staging";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
                    "path",
                    "long",
                    null,
                    "simple",
                    false
                )!,
            StringComparison.Ordinal
        );
        string serverUrl =
            server != null ? server.Url : "https://{environment}.example.com/api/{version}";
        if (
            serverUrl.StartsWith("http://", StringComparison.Ordinal)
            || serverUrl.StartsWith("https://", StringComparison.Ordinal)
        )
        {
            path = serverUrl + path;
        }

        Dictionary<string, object?> queryParams = [];
        Dictionary<string, string> headerParams = [];
        return await InvokeApiForResultAsync<Pet>(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                GetStagingPetInfoAccepts,
                "application/json",
                null
            )
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Set the pet&#39;s profile photo
    /// </summary>
    /// <remarks>Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.</remarks>
    /// <param name="petId"></param>
    /// <param name="body"></param>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task SetPetAvatarAsync(long petId, System.IO.Stream body)
    {
        Task<ApiResult<object?>> task = SetPetAvatarWithHttpInfoAsync(petId, body);
        _ = await task.ConfigureAwait(false);
    }

    /// <summary>
    /// Set the pet&#39;s profile photo (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<object?>> SetPetAvatarWithHttpInfoAsync(
        long petId,
        System.IO.Stream body
    )
    {
        string path = "/pet/{petId}/avatar";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
    /// <remarks>Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.</remarks>
    /// <param name="petId"></param>
    /// <param name="setPetAvatarThumbnailRequest"></param>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task SetPetAvatarThumbnailAsync(
        long petId,
        SetPetAvatarThumbnailRequest setPetAvatarThumbnailRequest
    )
    {
        Task<ApiResult<object?>> task = SetPetAvatarThumbnailWithHttpInfoAsync(
            petId,
            setPetAvatarThumbnailRequest
        );
        _ = await task.ConfigureAwait(false);
    }

    /// <summary>
    /// Set the pet&#39;s avatar thumbnail as base64 (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<object?>> SetPetAvatarThumbnailWithHttpInfoAsync(
        long petId,
        SetPetAvatarThumbnailRequest setPetAvatarThumbnailRequest
    )
    {
        string path = "/pet/{petId}/avatar/thumbnail";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<Pet> UpdatePetAsync(long petId, Pet pet)
    {
        Task<ApiResult<Pet>> task = UpdatePetWithHttpInfoAsync(petId, pet);
        ApiResult<Pet> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Update an existing pet (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<Pet>> UpdatePetWithHttpInfoAsync(long petId, Pet pet)
    {
        string path = "/pet/{petId}";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
        return await InvokeApiForResultAsync<Pet>(
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
    }

    /// <summary>
    /// Upload the pet&#39;s adoption certificate
    /// </summary>
    /// <remarks>Attaches a single adoption certificate document. No metadata fields are required alongside the file.</remarks>
    /// <param name="petId"></param>
    /// <param name="options">Options for query, header, and form parameters.</param>
    /// <returns><![CDATA[ApiResponse]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResponse> UploadPetCertificateAsync(
        long petId,
        UploadPetCertificateOptions options
    )
    {
        Task<ApiResult<ApiResponse>> task = UploadPetCertificateWithHttpInfoAsync(petId, options);
        ApiResult<ApiResponse> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Upload the pet&#39;s adoption certificate (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<ApiResponse>> UploadPetCertificateWithHttpInfoAsync(
        long petId,
        UploadPetCertificateOptions options
    )
    {
        ArgumentNullException.ThrowIfNull(options);
        string path = "/pet/{petId}/certificate";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
        Dictionary<string, object> formBody = [];
        formBody["file"] = options.File;
        return await InvokeApiForResultAsync<ApiResponse>(
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
    }

    /// <summary>
    /// Attach a vet document or health record
    /// </summary>
    /// <remarks>Accepts either a multipart upload with document classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer to stream bytes directly.</remarks>
    /// <param name="petId"></param>
    /// <param name="options">Options for query, header, and form parameters.</param>
    /// <returns><![CDATA[ApiResponse]]></returns>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResponse> UploadPetDocumentAsync(
        long petId,
        UploadPetDocumentOptions options
    )
    {
        Task<ApiResult<ApiResponse>> task = UploadPetDocumentWithHttpInfoAsync(petId, options);
        ApiResult<ApiResponse> result = await task.ConfigureAwait(false);
        return result.Data
            ?? throw new InvalidOperationException("Expected non-null response body");
    }

    /// <summary>
    /// Attach a vet document or health record (with HTTP info)
    /// </summary>
    /// <exception cref="ApiException">Thrown when the API call fails.</exception>
    public async Task<ApiResult<ApiResponse>> UploadPetDocumentWithHttpInfoAsync(
        long petId,
        UploadPetDocumentOptions options
    )
    {
        ArgumentNullException.ThrowIfNull(options);
        string path = "/pet/{petId}/documents";
        path = path.Replace(
            "{" + nameof(petId) + "}",
            (string)
                ValueSerializer.SerializeStyled(
                    nameof(petId),
                    petId,
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
        Dictionary<string, object> formBody = [];
        formBody["file"] = options.File;
        if (options.DocumentType != null)
        {
            formBody["documentType"] = options.DocumentType;
        }
        if (options.Notes != null)
        {
            formBody["notes"] = options.Notes;
        }
        return await InvokeApiForResultAsync<ApiResponse>(
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
    }
}
