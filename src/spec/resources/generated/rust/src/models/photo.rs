#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// Photo is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct Photo {
    #[serde(rename = "id", skip_serializing_if = "Option::is_none")]
    pub id: Option<i64>,
    #[serde(rename = "caption", skip_serializing_if = "Option::is_none")]
    pub caption: Option<String>,
    #[serde(rename = "isPrimary", skip_serializing_if = "Option::is_none")]
    pub is_primary: Option<bool>,
    #[serde(rename = "url", skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
}

#[allow(deprecated)]
impl Photo {
    /// Creates a new Photo instance with required parameters.
    pub fn new() -> Self {
        Self {
            id: None,
            caption: None,
            is_primary: None,
            url: None,
        }
    }
}
