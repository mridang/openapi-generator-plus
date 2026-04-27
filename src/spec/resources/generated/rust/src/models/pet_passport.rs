#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// PetPassport is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct PetPassport {
    #[serde(rename = "pet", skip_serializing_if = "Option::is_none")]
    pub pet: Option<Pet>,
    /// Base64-encoded primary thumbnail
    #[serde(rename = "thumbnail", skip_serializing_if = "Option::is_none")]
    pub thumbnail: Option<Vec<u8>>,
    /// Base64-encoded scans of each passport page
    #[serde(rename = "scans", skip_serializing_if = "Option::is_none")]
    pub scans: Option<Vec<Vec<u8>>>,
    #[serde(rename = "issuedAt", skip_serializing_if = "Option::is_none")]
    pub issued_at: Option<String>,
}

#[allow(deprecated)]
impl PetPassport {
    /// Creates a new PetPassport instance with required parameters.
    pub fn new() -> Self {
        Self {
            pet: None,
            thumbnail: None,
            scans: None,
            issued_at: None,
        }
    }
}
