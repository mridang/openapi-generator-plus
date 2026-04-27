use std::collections::HashMap;
use std::sync::Arc;

use crate::api::base_api::BaseApi;
use crate::api::base_api::InvokeApiParams;
#[allow(unused_imports)]
use crate::api::options::*;
use crate::api_client::ApiClient;
use crate::api_result::ApiResult;
use crate::authenticator::Authenticator;
use crate::configuration::Configuration;
use crate::models::*;
use crate::object_serializer;
use crate::value_serializer;
use crate::value_serializer::SerializedValue;

/// PetApi provides methods for the Pet API group.
/// Everything about your Pets
/// See https://example.com/docs/pets Find out more about pets
pub struct PetApi {
    base: BaseApi,
}

impl PetApi {
    /// Creates a new PetApi instance.
    pub fn new(api_client: Arc<dyn ApiClient>, config: Configuration) -> Self {
        Self {
            base: BaseApi::new(api_client, config),
        }
    }

    /// Add a new pet to the store
    pub async fn add_pet(
        &self,
        auth: &dyn Authenticator,
        pet: Pet,
    ) -> Result<Pet, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.add_pet_with_http_info(auth, pet).await?;
        Ok(result.data)
    }

    /// Performs the add_pet operation and returns the full API result.
    pub async fn add_pet_with_http_info(
        &self,
        auth: &dyn Authenticator,
        pet: Pet,
    ) -> Result<ApiResult<Pet>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet".to_string();

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body = Some(object_serializer::serialize(&pet)?);

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "POST",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Pet",
                auth: Some(auth),
            })
            .await?;

        let data: Pet = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Add photos to the pet's gallery
    /// Uploads one or more photos with structured metadata. The metadata part is serialised as JSON within the multipart body.
    pub async fn add_pet_photos(
        &self,
        pet_id: i64,

        options: Option<&AddPetPhotosOptions>,
    ) -> Result<Vec<Photo>, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.add_pet_photos_with_http_info(pet_id, options).await?;
        Ok(result.data)
    }

    /// Performs the add_pet_photos operation and returns the full API result.
    pub async fn add_pet_photos_with_http_info(
        &self,
        pet_id: i64,

        options: Option<&AddPetPhotosOptions>,
    ) -> Result<ApiResult<Vec<Photo>>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/photos".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let mut form_body: HashMap<String, String> = HashMap::new();
        if let Some(opts) = options {
            form_body.insert(
                "files".to_string(),
                object_serializer::stringify(&opts.files),
            );
        }
        if let Some(opts) = options {
            form_body.insert(
                "metadata".to_string(),
                object_serializer::stringify(&opts.metadata),
            );
        }
        let request_body = Some(serde_json::to_vec(&form_body)?);

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "POST",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "multipart/form-data",
                return_type: "Vec<Photo>",
                auth: None,
            })
            .await?;

        let data: Vec<Photo> = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Record a treatment for a pet
    pub async fn add_pet_treatment(
        &self,
        auth: &dyn Authenticator,
        pet_id: i64,
        pet_treatment: PetTreatment,
    ) -> Result<PetTreatment, Box<dyn std::error::Error + Send + Sync>> {
        let result = self
            .add_pet_treatment_with_http_info(auth, pet_id, pet_treatment)
            .await?;
        Ok(result.data)
    }

    /// Performs the add_pet_treatment operation and returns the full API result.
    pub async fn add_pet_treatment_with_http_info(
        &self,
        auth: &dyn Authenticator,
        pet_id: i64,
        pet_treatment: PetTreatment,
    ) -> Result<ApiResult<PetTreatment>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/treatment".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body = Some(object_serializer::serialize(&pet_treatment)?);

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "POST",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "PetTreatment",
                auth: Some(auth),
            })
            .await?;

        let data: PetTreatment = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Deletes a pet
    pub async fn delete_pet(
        &self,
        auth: &dyn Authenticator,
        pet_id: i64,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let result = self.delete_pet_with_http_info(auth, pet_id).await?;
        let _ = result;
        Ok(())
    }

    /// Performs the delete_pet operation and returns the full API result.
    pub async fn delete_pet_with_http_info(
        &self,
        auth: &dyn Authenticator,
        pet_id: i64,
    ) -> Result<ApiResult<()>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "DELETE",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec![],
                content_type: "application/json",
                return_type: "",
                auth: Some(auth),
            })
            .await?;

        Ok(ApiResult {
            status_code: response.status_code,
            data: (),
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Download a vet document
    /// Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.
    pub async fn download_pet_document(
        &self,
        pet_id: i64,
        document_id: i64,
    ) -> Result<Vec<u8>, Box<dyn std::error::Error + Send + Sync>> {
        let result = self
            .download_pet_document_with_http_info(pet_id, document_id)
            .await?;
        Ok(result.data)
    }

    /// Performs the download_pet_document operation and returns the full API result.
    pub async fn download_pet_document_with_http_info(
        &self,
        pet_id: i64,
        document_id: i64,
    ) -> Result<ApiResult<Vec<u8>>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/documents/{documentId}".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));
        path = path.replace(
            "{documentId}",
            &urlencoding::encode(&format!("{}", document_id)),
        );

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/octet-stream"],
                content_type: "application/json",
                return_type: "Vec<u8>",
                auth: None,
            })
            .await?;

        let data: Vec<u8> = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Finds Pets by status
    #[deprecated]
    /// See https://example.com/docs/filtering Find out more about filtering
    pub async fn find_pets_by_status(
        &self,

        options: Option<&FindPetsByStatusOptions>,
    ) -> Result<Vec<Pet>, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.find_pets_by_status_with_http_info(options).await?;
        Ok(result.data)
    }

    /// Performs the find_pets_by_status operation and returns the full API result.
    pub async fn find_pets_by_status_with_http_info(
        &self,

        options: Option<&FindPetsByStatusOptions>,
    ) -> Result<ApiResult<Vec<Pet>>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/findByStatus".to_string();

        let mut query_params: Vec<(String, String)> = Vec::new();
        if let Some(opts) = options {
            if let Some(ref val) = opts.status {
                if let Some(serialized) = value_serializer::serialize_styled(
                    "status",
                    Some(&object_serializer::stringify(val)),
                    None,
                    "query",
                    "String",
                    "",
                    "form",
                    true,
                ) {
                    match serialized {
                        SerializedValue::Single(v) => {
                            query_params.push(("status".to_string(), v));
                        }
                        SerializedValue::Multi(values) => {
                            for v in values {
                                query_params.push(("status".to_string(), v));
                            }
                        }
                    }
                }
            }
        }
        if let Some(opts) = options {
            if let Some(ref val) = opts.filter {
                for (k, v) in value_serializer::serialize_deep_object("filter", val) {
                    query_params.push((k, v));
                }
            }
        }

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Vec<Pet>",
                auth: None,
            })
            .await?;

        let data: Vec<Pet> = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Get external pet info
    pub async fn get_external_pet_info(
        &self,
        pet_id: i64,
    ) -> Result<Pet, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.get_external_pet_info_with_http_info(pet_id).await?;
        Ok(result.data)
    }

    /// Performs the get_external_pet_info operation and returns the full API result.
    pub async fn get_external_pet_info_with_http_info(
        &self,
        pet_id: i64,
    ) -> Result<ApiResult<Pet>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/external".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Pet",
                auth: None,
            })
            .await?;

        let data: Pet = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Get multi-server pet info
    pub async fn get_multi_server_pet_info(
        &self,
        pet_id: i64,
    ) -> Result<Pet, Box<dyn std::error::Error + Send + Sync>> {
        let result = self
            .get_multi_server_pet_info_with_http_info(pet_id)
            .await?;
        Ok(result.data)
    }

    /// Performs the get_multi_server_pet_info operation and returns the full API result.
    pub async fn get_multi_server_pet_info_with_http_info(
        &self,
        pet_id: i64,
    ) -> Result<ApiResult<Pet>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/multi".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Pet",
                auth: None,
            })
            .await?;

        let data: Pet = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Get the pet's profile photo
    /// Returns the raw image bytes of the pet's current avatar.
    pub async fn get_pet_avatar(
        &self,
        pet_id: i64,
    ) -> Result<Vec<u8>, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.get_pet_avatar_with_http_info(pet_id).await?;
        Ok(result.data)
    }

    /// Performs the get_pet_avatar operation and returns the full API result.
    pub async fn get_pet_avatar_with_http_info(
        &self,
        pet_id: i64,
    ) -> Result<ApiResult<Vec<u8>>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/avatar".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["image/jpeg", "image/png"],
                content_type: "application/json",
                return_type: "Vec<u8>",
                auth: None,
            })
            .await?;

        let data: Vec<u8> = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Get the pet's avatar thumbnail as base64
    /// Returns a compact base64-encoded thumbnail suitable for embedding directly in mobile UI without a separate image request.
    pub async fn get_pet_avatar_thumbnail(
        &self,
        pet_id: i64,
    ) -> Result<Vec<u8>, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.get_pet_avatar_thumbnail_with_http_info(pet_id).await?;
        Ok(result.data)
    }

    /// Performs the get_pet_avatar_thumbnail operation and returns the full API result.
    pub async fn get_pet_avatar_thumbnail_with_http_info(
        &self,
        pet_id: i64,
    ) -> Result<ApiResult<Vec<u8>>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/avatar/thumbnail".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Vec<u8>",
                auth: None,
            })
            .await?;

        let data: Vec<u8> = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Find pet by ID
    /// Returns a single pet
    #[deprecated]
    pub async fn get_pet_by_id(
        &self,
        pet_id: i64,
    ) -> Result<Pet, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.get_pet_by_id_with_http_info(pet_id).await?;
        Ok(result.data)
    }

    /// Performs the get_pet_by_id operation and returns the full API result.
    pub async fn get_pet_by_id_with_http_info(
        &self,
        pet_id: i64,
    ) -> Result<ApiResult<Pet>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Pet",
                auth: None,
            })
            .await?;

        let data: Pet = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Get the pet's passport
    /// Returns a single JSON document combining the pet's profile with an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for mobile clients that prefer a single-request workflow.
    pub async fn get_pet_passport(
        &self,
        pet_id: i64,
    ) -> Result<PetPassport, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.get_pet_passport_with_http_info(pet_id).await?;
        Ok(result.data)
    }

    /// Performs the get_pet_passport operation and returns the full API result.
    pub async fn get_pet_passport_with_http_info(
        &self,
        pet_id: i64,
    ) -> Result<ApiResult<PetPassport>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/passport".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "PetPassport",
                auth: None,
            })
            .await?;

        let data: PetPassport = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Get a photo or its metadata
    /// Returns the raw image bytes or JSON metadata depending on the Accept header sent by the client.
    pub async fn get_pet_photo(
        &self,
        pet_id: i64,
        photo_id: i64,
    ) -> Result<Vec<u8>, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.get_pet_photo_with_http_info(pet_id, photo_id).await?;
        Ok(result.data)
    }

    /// Performs the get_pet_photo operation and returns the full API result.
    pub async fn get_pet_photo_with_http_info(
        &self,
        pet_id: i64,
        photo_id: i64,
    ) -> Result<ApiResult<Vec<u8>>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/photos/{photoId}".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));
        path = path.replace("{photoId}", &urlencoding::encode(&format!("{}", photo_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["image/jpeg", "image/png", "application/json"],
                content_type: "application/json",
                return_type: "Vec<u8>",
                auth: None,
            })
            .await?;

        let data: Vec<u8> = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Get a tag for a pet
    pub async fn get_pet_tag(
        &self,
        pet_id: i64,
        tag_name: String,

        options: Option<&GetPetTagOptions>,
    ) -> Result<Pet, Box<dyn std::error::Error + Send + Sync>> {
        let result = self
            .get_pet_tag_with_http_info(pet_id, tag_name, options)
            .await?;
        Ok(result.data)
    }

    /// Performs the get_pet_tag operation and returns the full API result.
    pub async fn get_pet_tag_with_http_info(
        &self,
        pet_id: i64,
        tag_name: String,

        options: Option<&GetPetTagOptions>,
    ) -> Result<ApiResult<Pet>, Box<dyn std::error::Error + Send + Sync>> {
        if tag_name.is_empty() {
            return Err(format!(
                "missing required parameter '{}' when calling PetApi.get_pet_tag",
                "tag_name"
            )
            .into());
        }

        let mut path = "/pet/{petId}/tag/{tagName}".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));
        path = path.replace("{tagName}", &urlencoding::encode(&format!("{}", tag_name)));

        let mut query_params: Vec<(String, String)> = Vec::new();
        if let Some(opts) = options {
            if let Some(ref val) = opts.colors {
                if let Some(serialized) = value_serializer::serialize_styled(
                    "colors",
                    Some(&object_serializer::stringify(val)),
                    None,
                    "query",
                    "Vec<String>",
                    "pipes",
                    "pipeDelimited",
                    false,
                ) {
                    match serialized {
                        SerializedValue::Single(v) => {
                            query_params.push(("colors".to_string(), v));
                        }
                        SerializedValue::Multi(values) => {
                            for v in values {
                                query_params.push(("colors".to_string(), v));
                            }
                        }
                    }
                }
            }
        }
        if let Some(opts) = options {
            if let Some(ref val) = opts.sizes {
                if let Some(serialized) = value_serializer::serialize_styled(
                    "sizes",
                    Some(&object_serializer::stringify(val)),
                    None,
                    "query",
                    "Vec<String>",
                    "ssv",
                    "spaceDelimited",
                    false,
                ) {
                    match serialized {
                        SerializedValue::Single(v) => {
                            query_params.push(("sizes".to_string(), v));
                        }
                        SerializedValue::Multi(values) => {
                            for v in values {
                                query_params.push(("sizes".to_string(), v));
                            }
                        }
                    }
                }
            }
        }
        if let Some(opts) = options {
            if let Some(ref val) = opts.filter {
                if let Some(serialized) = value_serializer::serialize_styled(
                    "filter",
                    Some(&object_serializer::stringify(val)),
                    None,
                    "query",
                    "String",
                    "",
                    "form",
                    true,
                ) {
                    match serialized {
                        SerializedValue::Single(v) => {
                            query_params.push(("filter".to_string(), v));
                        }
                        SerializedValue::Multi(values) => {
                            for v in values {
                                query_params.push(("filter".to_string(), v));
                            }
                        }
                    }
                }
            }
        }

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Pet",
                auth: None,
            })
            .await?;

        let data: Pet = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Get staging pet info
    pub async fn get_staging_pet_info(
        &self,
        pet_id: i64,
    ) -> Result<Pet, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.get_staging_pet_info_with_http_info(pet_id).await?;
        Ok(result.data)
    }

    /// Performs the get_staging_pet_info operation and returns the full API result.
    pub async fn get_staging_pet_info_with_http_info(
        &self,
        pet_id: i64,
    ) -> Result<ApiResult<Pet>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/staging".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body: Option<Vec<u8>> = None;

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "GET",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Pet",
                auth: None,
            })
            .await?;

        let data: Pet = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Set the pet's profile photo
    /// Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
    pub async fn set_pet_avatar(
        &self,
        pet_id: i64,
        body: Vec<u8>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let result = self.set_pet_avatar_with_http_info(pet_id, body).await?;
        let _ = result;
        Ok(())
    }

    /// Performs the set_pet_avatar operation and returns the full API result.
    pub async fn set_pet_avatar_with_http_info(
        &self,
        pet_id: i64,
        body: Vec<u8>,
    ) -> Result<ApiResult<()>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/avatar".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body = Some(object_serializer::serialize(&body)?);

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "PUT",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec![],
                content_type: "image/jpeg",
                return_type: "",
                auth: None,
            })
            .await?;

        Ok(ApiResult {
            status_code: response.status_code,
            data: (),
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Set the pet's avatar thumbnail as base64
    /// Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.
    pub async fn set_pet_avatar_thumbnail(
        &self,
        pet_id: i64,
        set_pet_avatar_thumbnail_request: SetPetAvatarThumbnailRequest,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let result = self
            .set_pet_avatar_thumbnail_with_http_info(pet_id, set_pet_avatar_thumbnail_request)
            .await?;
        let _ = result;
        Ok(())
    }

    /// Performs the set_pet_avatar_thumbnail operation and returns the full API result.
    pub async fn set_pet_avatar_thumbnail_with_http_info(
        &self,
        pet_id: i64,
        set_pet_avatar_thumbnail_request: SetPetAvatarThumbnailRequest,
    ) -> Result<ApiResult<()>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/avatar/thumbnail".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body = Some(object_serializer::serialize(
            &set_pet_avatar_thumbnail_request,
        )?);

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "PUT",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec![],
                content_type: "application/json",
                return_type: "",
                auth: None,
            })
            .await?;

        Ok(ApiResult {
            status_code: response.status_code,
            data: (),
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Update an existing pet
    pub async fn update_pet(
        &self,
        pet_id: i64,
        pet: Pet,
    ) -> Result<Pet, Box<dyn std::error::Error + Send + Sync>> {
        let result = self.update_pet_with_http_info(pet_id, pet).await?;
        Ok(result.data)
    }

    /// Performs the update_pet operation and returns the full API result.
    pub async fn update_pet_with_http_info(
        &self,
        pet_id: i64,
        pet: Pet,
    ) -> Result<ApiResult<Pet>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let request_body = Some(object_serializer::serialize(&pet)?);

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "PUT",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "application/json",
                return_type: "Pet",
                auth: None,
            })
            .await?;

        let data: Pet = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Upload the pet's adoption certificate
    /// Attaches a single adoption certificate document. No metadata fields are required alongside the file.
    pub async fn upload_pet_certificate(
        &self,
        pet_id: i64,

        options: Option<&UploadPetCertificateOptions>,
    ) -> Result<ApiResponse, Box<dyn std::error::Error + Send + Sync>> {
        let result = self
            .upload_pet_certificate_with_http_info(pet_id, options)
            .await?;
        Ok(result.data)
    }

    /// Performs the upload_pet_certificate operation and returns the full API result.
    pub async fn upload_pet_certificate_with_http_info(
        &self,
        pet_id: i64,

        options: Option<&UploadPetCertificateOptions>,
    ) -> Result<ApiResult<ApiResponse>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/certificate".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let mut form_body: HashMap<String, String> = HashMap::new();
        if let Some(opts) = options {
            form_body.insert("file".to_string(), object_serializer::stringify(&opts.file));
        }
        let request_body = Some(serde_json::to_vec(&form_body)?);

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "POST",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "multipart/form-data",
                return_type: "ApiResponse",
                auth: None,
            })
            .await?;

        let data: ApiResponse = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }

    /// Attach a vet document or health record
    /// Accepts either a multipart upload with document classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer to stream bytes directly.
    pub async fn upload_pet_document(
        &self,
        pet_id: i64,

        options: Option<&UploadPetDocumentOptions>,
    ) -> Result<ApiResponse, Box<dyn std::error::Error + Send + Sync>> {
        let result = self
            .upload_pet_document_with_http_info(pet_id, options)
            .await?;
        Ok(result.data)
    }

    /// Performs the upload_pet_document operation and returns the full API result.
    pub async fn upload_pet_document_with_http_info(
        &self,
        pet_id: i64,

        options: Option<&UploadPetDocumentOptions>,
    ) -> Result<ApiResult<ApiResponse>, Box<dyn std::error::Error + Send + Sync>> {
        let mut path = "/pet/{petId}/documents".to_string();
        path = path.replace("{petId}", &urlencoding::encode(&format!("{}", pet_id)));

        let mut query_params: Vec<(String, String)> = Vec::new();

        let mut header_params: HashMap<String, String> = HashMap::new();

        let mut form_body: HashMap<String, String> = HashMap::new();
        if let Some(opts) = options {
            form_body.insert("file".to_string(), object_serializer::stringify(&opts.file));
        }
        if let Some(opts) = options {
            if let Some(ref val) = opts.document_type {
                form_body.insert(
                    "documentType".to_string(),
                    object_serializer::stringify(val),
                );
            }
        }
        if let Some(opts) = options {
            if let Some(ref val) = opts.notes {
                form_body.insert("notes".to_string(), object_serializer::stringify(val));
            }
        }
        let request_body = Some(serde_json::to_vec(&form_body)?);

        let response = self
            .base
            .invoke_api(InvokeApiParams {
                method: "POST",
                path: &path,
                query_params,
                header_params,
                body: request_body,
                accepts: vec!["application/json"],
                content_type: "multipart/form-data",
                return_type: "ApiResponse",
                auth: None,
            })
            .await?;

        let data: ApiResponse = if !response.body.is_empty() {
            object_serializer::deserialize(response.body.as_bytes())?
        } else {
            return Err("empty response body".into());
        };

        Ok(ApiResult {
            status_code: response.status_code,
            data,
            raw_body: response.body,
            headers: response.headers,
        })
    }
}
