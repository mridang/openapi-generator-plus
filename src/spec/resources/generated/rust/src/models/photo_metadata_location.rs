#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// PhotoMetadataLocation is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct PhotoMetadataLocation {
    #[serde(rename = "lat", skip_serializing_if = "Option::is_none")]
    pub lat: Option<f64>,
    #[serde(rename = "lng", skip_serializing_if = "Option::is_none")]
    pub lng: Option<f64>,
}

#[allow(deprecated)]
impl PhotoMetadataLocation {
    /// Creates a new PhotoMetadataLocation instance with required parameters.
    pub fn new() -> Self {
        Self {
            lat: None,
            lng: None,
        }
    }
}
