package com.example.petstore.api

import com.example.petstore.ApiClient
import com.example.petstore.ApiException
import com.example.petstore.ApiResult
import com.example.petstore.Configuration
import com.example.petstore.ValueSerializer
import com.example.petstore.api.options.AddPetPhotosOptions
import com.example.petstore.api.options.FindPetsByStatusOptions
import com.example.petstore.api.options.GetPetTagOptions
import com.example.petstore.api.options.UploadPetCertificateOptions
import com.example.petstore.api.options.UploadPetDocumentOptions
import com.example.petstore.auth.AdminBasicAuthenticator
import com.example.petstore.auth.ApiKeyHeaderAuthenticator
import com.example.petstore.auth.Authenticator
import com.example.petstore.auth.PetStoreBasicAuthenticator
import com.example.petstore.auth.PetStoreBearerAuthenticator
import com.example.petstore.auth.oauth.MachineAuthClientCredentialsAuthenticator
import com.example.petstore.models.ApiResponse
import com.example.petstore.models.Pet
import com.example.petstore.models.PetPassport
import com.example.petstore.models.PetTreatment
import com.example.petstore.models.Photo
import com.example.petstore.models.SetPetAvatarThumbnailRequest
import kotlin.collections.List

/**
 * PetApi provides methods for the Pet API group.
 * Everything about your Pets
 * @see <a href="https://example.com/docs/pets">Find out more about pets</a>
 */
class PetApi : BaseApi {
    /**
     * Server type for the getExternalPetInfo operation.
     */
    sealed interface GetExternalPetInfoServer {
        fun getUrl(): String

        object Server0 : GetExternalPetInfoServer {
            override fun getUrl(): String = "https://external-api.example.com/v1"
        }
    }

    /**
     * Server type for the getMultiServerPetInfo operation.
     */
    sealed interface GetMultiServerPetInfoServer {
        fun getUrl(): String

        enum class Region(
            val value: String,
        ) {
            US("us"),
            EU("eu"),
            AP("ap"),
        }

        /** Primary */
        object Primary : GetMultiServerPetInfoServer {
            override fun getUrl(): String = "https://primary.example.com/v1"
        }

        /** Regional */
        data class Regional(
            val region: Region,
        ) : GetMultiServerPetInfoServer {
            override fun getUrl(): String {
                var url = "https://{region}.example.com/v1"
                url = url.replace("{" + "region" + "}", region.value)
                return url
            }
        }
    }

    /**
     * Server type for the getStagingPetInfo operation.
     */
    sealed interface GetStagingPetInfoServer {
        fun getUrl(): String

        enum class Environment(
            val value: String,
        ) {
            STAGING("staging"),
            SANDBOX("sandbox"),
        }

        enum class Version(
            val value: String,
        ) {
            V2("v2"),
            V3("v3"),
        }

        /** Staging server */
        data class StagingServer(
            val environment: Environment,
            val version: Version,
        ) : GetStagingPetInfoServer {
            override fun getUrl(): String {
                var url = "https://{environment}.example.com/api/{version}"
                url = url.replace("{" + "environment" + "}", environment.value)
                url = url.replace("{" + "version" + "}", version.value)
                return url
            }
        }
    }

    constructor() : super()

    constructor(apiClient: ApiClient, config: Configuration) : super(apiClient, config)

    /**
     * Add a new pet to the store
     * @param pet Create a new pet in the store (required)

     * @return Pet
     * @throws ApiException if fails to make API call
     */
    suspend fun addPet(
        auth: PetStoreBearerAuthenticator,
        pet: Pet,
    ): Pet? = addPetWithHttpInfo(auth, pet).data

    suspend fun addPetWithHttpInfo(
        auth: PetStoreBearerAuthenticator,
        pet: Pet,
    ): ApiResult<Pet> = addPetInternal(auth, pet)

    suspend fun addPet(
        auth: ApiKeyHeaderAuthenticator,
        pet: Pet,
    ): Pet? = addPetWithHttpInfo(auth, pet).data

    suspend fun addPetWithHttpInfo(
        auth: ApiKeyHeaderAuthenticator,
        pet: Pet,
    ): ApiResult<Pet> = addPetInternal(auth, pet)

    private suspend fun addPetInternal(
        auth: Authenticator,
        pet: Pet,
    ): ApiResult<Pet> {
        requireNotNull(pet) {
            "Missing the required parameter 'pet' when calling addPet"
        }
        var path = "/pet"
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "POST",
                path,
                queryParams,
                headerParams,
                pet,
                arrayOf("application/json"),
                "application/json",
                auth,
            )
        val data = objectSerializer.deserialize<Pet>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Add photos to the pet&#39;s gallery
     * Uploads one or more photos with structured metadata. The metadata part is serialised as JSON within the multipart body.
     * @param petId  (required)

     * @param options options for query, header, form, and cookie parameters

     * @return List<Photo>
     * @throws ApiException if fails to make API call
     */

    suspend fun addPetPhotos(
        petId: Long,
        options: AddPetPhotosOptions,
    ): List<Photo>? = addPetPhotosWithHttpInfo(petId, options).data

    suspend fun addPetPhotosWithHttpInfo(
        petId: Long,
        options: AddPetPhotosOptions,
    ): ApiResult<List<Photo>> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling addPetPhotos"
        }
        var path =
            "/pet/{petId}/photos"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val formBody = mutableMapOf<String, Any?>()
        formBody["files"] = options.files
        formBody["metadata"] = options.metadata

        val response =
            invokeApi(
                "POST",
                path,
                queryParams,
                headerParams,
                formBody,
                arrayOf("application/json"),
                "multipart/form-data",
                null,
            )
        val data = objectSerializer.deserialize<List<Photo>>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Record a treatment for a pet
     * @param petId  (required)
     * @param petTreatment  (required)

     * @return PetTreatment
     * @throws ApiException if fails to make API call
     */
    suspend fun addPetTreatment(
        auth: PetStoreBasicAuthenticator,
        petId: Long,
        petTreatment: PetTreatment,
    ): PetTreatment? = addPetTreatmentWithHttpInfo(auth, petId, petTreatment).data

    suspend fun addPetTreatmentWithHttpInfo(
        auth: PetStoreBasicAuthenticator,
        petId: Long,
        petTreatment: PetTreatment,
    ): ApiResult<PetTreatment> = addPetTreatmentInternal(auth, petId, petTreatment)

    suspend fun addPetTreatment(
        auth: PetStoreBearerAuthenticator,
        petId: Long,
        petTreatment: PetTreatment,
    ): PetTreatment? = addPetTreatmentWithHttpInfo(auth, petId, petTreatment).data

    suspend fun addPetTreatmentWithHttpInfo(
        auth: PetStoreBearerAuthenticator,
        petId: Long,
        petTreatment: PetTreatment,
    ): ApiResult<PetTreatment> = addPetTreatmentInternal(auth, petId, petTreatment)

    private suspend fun addPetTreatmentInternal(
        auth: Authenticator,
        petId: Long,
        petTreatment: PetTreatment,
    ): ApiResult<PetTreatment> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling addPetTreatment"
        }
        requireNotNull(petTreatment) {
            "Missing the required parameter 'petTreatment' when calling addPetTreatment"
        }
        var path =
            "/pet/{petId}/treatment"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "POST",
                path,
                queryParams,
                headerParams,
                petTreatment,
                arrayOf("application/json"),
                "application/json",
                auth,
            )
        val data = objectSerializer.deserialize<PetTreatment>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Deletes a pet
     * @param petId Pet id to delete (required)

     * @throws ApiException if fails to make API call
     */
    suspend fun deletePet(
        auth: MachineAuthClientCredentialsAuthenticator,
        petId: Long,
    ) {
        deletePetWithHttpInfo(auth, petId)
    }

    suspend fun deletePetWithHttpInfo(
        auth: MachineAuthClientCredentialsAuthenticator,
        petId: Long,
    ): ApiResult<Unit> = deletePetInternal(auth, petId)

    suspend fun deletePet(
        auth: AdminBasicAuthenticator,
        petId: Long,
    ) {
        deletePetWithHttpInfo(auth, petId)
    }

    suspend fun deletePetWithHttpInfo(
        auth: AdminBasicAuthenticator,
        petId: Long,
    ): ApiResult<Unit> = deletePetInternal(auth, petId)

    private suspend fun deletePetInternal(
        auth: Authenticator,
        petId: Long,
    ): ApiResult<Unit> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling deletePet"
        }
        var path =
            "/pet/{petId}"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "DELETE",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf(),
                "application/json",
                auth,
            )
        return ApiResult(response.statusCode, Unit, response.body, response.headers)
    }

    /**
     * Download a vet document
     * Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.
     * @param petId  (required)
     * @param documentId  (required)

     * @return ByteArray
     * @throws ApiException if fails to make API call
     */

    suspend fun downloadPetDocument(
        petId: Long,
        documentId: Long,
    ): ByteArray? = downloadPetDocumentWithHttpInfo(petId, documentId).data

    suspend fun downloadPetDocumentWithHttpInfo(
        petId: Long,
        documentId: Long,
    ): ApiResult<ByteArray> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling downloadPetDocument"
        }
        requireNotNull(documentId) {
            "Missing the required parameter 'documentId' when calling downloadPetDocument"
        }
        var path =
            "/pet/{petId}/documents/{documentId}"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                ).replace(
                    "{" + "documentId" + "}",
                    ValueSerializer.serializeStyled("documentId", documentId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/octet-stream"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<ByteArray>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Finds Pets by status

     * @param options options for query, header, form, and cookie parameters

     * @return List<Pet>
     * @throws ApiException if fails to make API call
     * @deprecated This operation is deprecated.
     * Find out more about filtering
     * @see <a href="https://example.com/docs/filtering">Finds Pets by status Documentation</a>
     */
    @Deprecated("This operation is deprecated.")
    suspend fun findPetsByStatus(options: FindPetsByStatusOptions): List<Pet>? = findPetsByStatusWithHttpInfo(options).data

    suspend fun findPetsByStatusWithHttpInfo(options: FindPetsByStatusOptions): ApiResult<List<Pet>> {
        var path = "/pet/findByStatus"
        val queryParams = mutableMapOf<String, Any?>()
        if (options.status != null) {
            queryParams["status"] = ValueSerializer.serializeStyled("status", options.status, "query", "String", null, "form", true)
        }
        if (options.filter != null) {
            queryParams.putAll(ValueSerializer.serializeDeepObject("filter", options.filter))
        }
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<List<Pet>>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Get external pet info
     * @param petId  (required)

     * @return Pet
     * @throws ApiException if fails to make API call
     */

    suspend fun getExternalPetInfo(petId: Long): Pet? = getExternalPetInfo(petId, null)

    suspend fun getExternalPetInfoWithHttpInfo(petId: Long): ApiResult<Pet> = getExternalPetInfoWithHttpInfo(petId, null)

    suspend fun getExternalPetInfo(
        petId: Long,
        server: GetExternalPetInfoServer? = null,
    ): Pet? = getExternalPetInfoWithHttpInfo(petId, server).data

    suspend fun getExternalPetInfoWithHttpInfo(
        petId: Long,
        server: GetExternalPetInfoServer? = null,
    ): ApiResult<Pet> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getExternalPetInfo"
        }
        var path =
            "/pet/{petId}/external"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        if (server != null) {
            val serverUrl = server.getUrl()
            if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
                path = serverUrl + path
            }
        } else if ("https://external-api.example.com/v1".startsWith("http://") ||
            "https://external-api.example.com/v1".startsWith("https://")
        ) {
            path = "https://external-api.example.com/v1" + path
        }
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<Pet>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Get multi-server pet info
     * @param petId  (required)

     * @return Pet
     * @throws ApiException if fails to make API call
     */

    suspend fun getMultiServerPetInfo(petId: Long): Pet? = getMultiServerPetInfo(petId, null)

    suspend fun getMultiServerPetInfoWithHttpInfo(petId: Long): ApiResult<Pet> = getMultiServerPetInfoWithHttpInfo(petId, null)

    suspend fun getMultiServerPetInfo(
        petId: Long,
        server: GetMultiServerPetInfoServer? = null,
    ): Pet? = getMultiServerPetInfoWithHttpInfo(petId, server).data

    suspend fun getMultiServerPetInfoWithHttpInfo(
        petId: Long,
        server: GetMultiServerPetInfoServer? = null,
    ): ApiResult<Pet> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getMultiServerPetInfo"
        }
        var path =
            "/pet/{petId}/multi"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        if (server != null) {
            val serverUrl = server.getUrl()
            if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
                path = serverUrl + path
            }
        } else if ("https://primary.example.com/v1".startsWith("http://") || "https://primary.example.com/v1".startsWith("https://")) {
            path = "https://primary.example.com/v1" + path
        }
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<Pet>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Get the pet&#39;s profile photo
     * Returns the raw image bytes of the pet&#39;s current avatar.
     * @param petId  (required)

     * @return ByteArray
     * @throws ApiException if fails to make API call
     */

    suspend fun getPetAvatar(petId: Long): ByteArray? = getPetAvatarWithHttpInfo(petId).data

    suspend fun getPetAvatarWithHttpInfo(petId: Long): ApiResult<ByteArray> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getPetAvatar"
        }
        var path =
            "/pet/{petId}/avatar"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("image/jpeg", "image/png"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<ByteArray>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Get the pet&#39;s avatar thumbnail as base64
     * Returns a compact base64-encoded thumbnail suitable for embedding directly in mobile UI without a separate image request.
     * @param petId  (required)

     * @return ByteArray
     * @throws ApiException if fails to make API call
     */

    suspend fun getPetAvatarThumbnail(petId: Long): ByteArray? = getPetAvatarThumbnailWithHttpInfo(petId).data

    suspend fun getPetAvatarThumbnailWithHttpInfo(petId: Long): ApiResult<ByteArray> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getPetAvatarThumbnail"
        }
        var path =
            "/pet/{petId}/avatar/thumbnail"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<ByteArray>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Find pet by ID
     * Returns a single pet
     * @param petId ID of pet to return (required)

     * @return Pet
     * @throws ApiException if fails to make API call
     * @deprecated This operation is deprecated.
     */
    @Deprecated("This operation is deprecated.")
    suspend fun getPetById(petId: Long): Pet? = getPetByIdWithHttpInfo(petId).data

    suspend fun getPetByIdWithHttpInfo(petId: Long): ApiResult<Pet> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getPetById"
        }
        var path =
            "/pet/{petId}"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<Pet>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Get the pet&#39;s passport
     * Returns a single JSON document combining the pet&#39;s profile with an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for mobile clients that prefer a single-request workflow.
     * @param petId  (required)

     * @return PetPassport
     * @throws ApiException if fails to make API call
     */

    suspend fun getPetPassport(petId: Long): PetPassport? = getPetPassportWithHttpInfo(petId).data

    suspend fun getPetPassportWithHttpInfo(petId: Long): ApiResult<PetPassport> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getPetPassport"
        }
        var path =
            "/pet/{petId}/passport"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<PetPassport>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Get a photo or its metadata
     * Returns the raw image bytes or JSON metadata depending on the Accept header sent by the client.
     * @param petId  (required)
     * @param photoId  (required)

     * @return ByteArray
     * @throws ApiException if fails to make API call
     */

    suspend fun getPetPhoto(
        petId: Long,
        photoId: Long,
    ): ByteArray? = getPetPhotoWithHttpInfo(petId, photoId).data

    suspend fun getPetPhotoWithHttpInfo(
        petId: Long,
        photoId: Long,
    ): ApiResult<ByteArray> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getPetPhoto"
        }
        requireNotNull(photoId) {
            "Missing the required parameter 'photoId' when calling getPetPhoto"
        }
        var path =
            "/pet/{petId}/photos/{photoId}"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                ).replace(
                    "{" + "photoId" + "}",
                    ValueSerializer.serializeStyled("photoId", photoId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("image/jpeg", "image/png", "application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<ByteArray>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Get a tag for a pet
     * @param petId  (required)
     * @param tagName  (required)

     * @param options options for query, header, form, and cookie parameters

     * @return Pet
     * @throws ApiException if fails to make API call
     */

    suspend fun getPetTag(
        petId: Long,
        tagName: String,
        options: GetPetTagOptions,
    ): Pet? = getPetTagWithHttpInfo(petId, tagName, options).data

    suspend fun getPetTagWithHttpInfo(
        petId: Long,
        tagName: String,
        options: GetPetTagOptions,
    ): ApiResult<Pet> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getPetTag"
        }
        requireNotNull(tagName) {
            "Missing the required parameter 'tagName' when calling getPetTag"
        }
        var path =
            "/pet/{petId}/tag/{tagName}"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "matrix", false) as String,
                ).replace(
                    "{" + "tagName" + "}",
                    ValueSerializer.serializeStyled("tagName", tagName, "path", "String", null, "label", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        if (options.colors != null) {
            queryParams["colors"] =
                ValueSerializer.serializeStyled("colors", options.colors, "query", "List<String>", "pipes", "pipeDelimited", false)
        }
        if (options.sizes != null) {
            queryParams["sizes"] =
                ValueSerializer.serializeStyled("sizes", options.sizes, "query", "List<String>", "ssv", "spaceDelimited", false)
        }
        run {
            val _filterVal = ValueSerializer.serializeStyled("filter", options.filter, "query", "String", null, "form", true)
            if (_filterVal != null) {
                queryParams["filter"] = _filterVal
            } else {
                queryParams["filter"] = ""
            }
        }
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<Pet>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Get staging pet info
     * @param petId  (required)

     * @return Pet
     * @throws ApiException if fails to make API call
     */

    suspend fun getStagingPetInfo(petId: Long): Pet? = getStagingPetInfo(petId, null)

    suspend fun getStagingPetInfoWithHttpInfo(petId: Long): ApiResult<Pet> = getStagingPetInfoWithHttpInfo(petId, null)

    suspend fun getStagingPetInfo(
        petId: Long,
        server: GetStagingPetInfoServer? = null,
    ): Pet? = getStagingPetInfoWithHttpInfo(petId, server).data

    suspend fun getStagingPetInfoWithHttpInfo(
        petId: Long,
        server: GetStagingPetInfoServer? = null,
    ): ApiResult<Pet> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling getStagingPetInfo"
        }
        var path =
            "/pet/{petId}/staging"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        if (server != null) {
            val serverUrl = server.getUrl()
            if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
                path = serverUrl + path
            }
        } else if ("https://{environment}.example.com/api/{version}".startsWith("http://") ||
            "https://{environment}.example.com/api/{version}".startsWith("https://")
        ) {
            path = "https://{environment}.example.com/api/{version}" + path
        }
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "GET",
                path,
                queryParams,
                headerParams,
                null,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<Pet>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Set the pet&#39;s profile photo
     * Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
     * @param petId  (required)
     * @param body  (required)

     * @throws ApiException if fails to make API call
     */

    suspend fun setPetAvatar(
        petId: Long,
        body: ByteArray,
    ) {
        setPetAvatarWithHttpInfo(petId, body)
    }

    suspend fun setPetAvatarWithHttpInfo(
        petId: Long,
        body: ByteArray,
    ): ApiResult<Unit> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling setPetAvatar"
        }
        requireNotNull(body) {
            "Missing the required parameter 'body' when calling setPetAvatar"
        }
        var path =
            "/pet/{petId}/avatar"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "PUT",
                path,
                queryParams,
                headerParams,
                body,
                arrayOf(),
                "image/jpeg",
                null,
            )
        return ApiResult(response.statusCode, Unit, response.body, response.headers)
    }

    /**
     * Set the pet&#39;s avatar thumbnail as base64
     * Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.
     * @param petId  (required)
     * @param setPetAvatarThumbnailRequest  (required)

     * @throws ApiException if fails to make API call
     */

    suspend fun setPetAvatarThumbnail(
        petId: Long,
        setPetAvatarThumbnailRequest: SetPetAvatarThumbnailRequest,
    ) {
        setPetAvatarThumbnailWithHttpInfo(petId, setPetAvatarThumbnailRequest)
    }

    suspend fun setPetAvatarThumbnailWithHttpInfo(
        petId: Long,
        setPetAvatarThumbnailRequest: SetPetAvatarThumbnailRequest,
    ): ApiResult<Unit> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling setPetAvatarThumbnail"
        }
        requireNotNull(setPetAvatarThumbnailRequest) {
            "Missing the required parameter 'setPetAvatarThumbnailRequest' when calling setPetAvatarThumbnail"
        }
        var path =
            "/pet/{petId}/avatar/thumbnail"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "PUT",
                path,
                queryParams,
                headerParams,
                setPetAvatarThumbnailRequest,
                arrayOf(),
                "application/json",
                null,
            )
        return ApiResult(response.statusCode, Unit, response.body, response.headers)
    }

    /**
     * Update an existing pet
     * @param petId ID of pet to update (required)
     * @param pet Pet object that needs to be updated (required)

     * @return Pet
     * @throws ApiException if fails to make API call
     */

    suspend fun updatePet(
        petId: Long,
        pet: Pet,
    ): Pet? = updatePetWithHttpInfo(petId, pet).data

    suspend fun updatePetWithHttpInfo(
        petId: Long,
        pet: Pet,
    ): ApiResult<Pet> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling updatePet"
        }
        requireNotNull(pet) {
            "Missing the required parameter 'pet' when calling updatePet"
        }
        var path =
            "/pet/{petId}"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val response =
            invokeApi(
                "PUT",
                path,
                queryParams,
                headerParams,
                pet,
                arrayOf("application/json"),
                "application/json",
                null,
            )
        val data = objectSerializer.deserialize<Pet>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Upload the pet&#39;s adoption certificate
     * Attaches a single adoption certificate document. No metadata fields are required alongside the file.
     * @param petId  (required)

     * @param options options for query, header, form, and cookie parameters

     * @return ApiResponse
     * @throws ApiException if fails to make API call
     */

    suspend fun uploadPetCertificate(
        petId: Long,
        options: UploadPetCertificateOptions,
    ): ApiResponse? = uploadPetCertificateWithHttpInfo(petId, options).data

    suspend fun uploadPetCertificateWithHttpInfo(
        petId: Long,
        options: UploadPetCertificateOptions,
    ): ApiResult<ApiResponse> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling uploadPetCertificate"
        }
        var path =
            "/pet/{petId}/certificate"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val formBody = mutableMapOf<String, Any?>()
        formBody["file"] = options._file

        val response =
            invokeApi(
                "POST",
                path,
                queryParams,
                headerParams,
                formBody,
                arrayOf("application/json"),
                "multipart/form-data",
                null,
            )
        val data = objectSerializer.deserialize<ApiResponse>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }

    /**
     * Attach a vet document or health record
     * Accepts either a multipart upload with document classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer to stream bytes directly.
     * @param petId  (required)

     * @param options options for query, header, form, and cookie parameters

     * @return ApiResponse
     * @throws ApiException if fails to make API call
     */

    suspend fun uploadPetDocument(
        petId: Long,
        options: UploadPetDocumentOptions,
    ): ApiResponse? = uploadPetDocumentWithHttpInfo(petId, options).data

    suspend fun uploadPetDocumentWithHttpInfo(
        petId: Long,
        options: UploadPetDocumentOptions,
    ): ApiResult<ApiResponse> {
        requireNotNull(petId) {
            "Missing the required parameter 'petId' when calling uploadPetDocument"
        }
        var path =
            "/pet/{petId}/documents"
                .replace(
                    "{" + "petId" + "}",
                    ValueSerializer.serializeStyled("petId", petId, "path", "Long", null, "simple", false) as String,
                )
        val queryParams = mutableMapOf<String, Any?>()
        val headerParams = mutableMapOf<String, String>()
        val formBody = mutableMapOf<String, Any?>()
        formBody["file"] = options._file
        if (options.documentType != null) {
            formBody["documentType"] = options.documentType
        }
        if (options.notes != null) {
            formBody["notes"] = options.notes
        }

        val response =
            invokeApi(
                "POST",
                path,
                queryParams,
                headerParams,
                formBody,
                arrayOf("application/json"),
                "multipart/form-data",
                null,
            )
        val data = objectSerializer.deserialize<ApiResponse>(response.body)
        return ApiResult(response.statusCode, data, response.body, response.headers)
    }
}
