#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// ApiResponse is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct ApiResponse {
    #[serde(rename = "code", skip_serializing_if = "Option::is_none")]
    pub code: Option<i32>,
    #[serde(rename = "type", skip_serializing_if = "Option::is_none")]
    pub _type: Option<String>,
    #[serde(rename = "message", skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}

#[allow(deprecated)]
impl ApiResponse {
    /// Creates a new ApiResponse instance with required parameters.
    pub fn new() -> Self {
        Self {
            code: None,
            _type: None,
            message: None,
        }
    }
}
