#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// SetPetAvatarRequest is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct SetPetAvatarRequest {
    /// Base64-encoded image data
    #[serde(rename = "data")]
    pub data: Vec<u8>,
    #[serde(rename = "mimeType")]
    pub mime_type: String,
}

#[allow(deprecated)]
impl SetPetAvatarRequest {
    /// Creates a new SetPetAvatarRequest instance with required parameters.
    pub fn new(data: Vec<u8>, mime_type: String) -> Self {
        Self { data, mime_type }
    }
}
