#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// Metadata is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct Metadata {
    #[serde(rename = "createdAt", skip_serializing_if = "Option::is_none")]
    pub created_at: Option<String>,
}

#[allow(deprecated)]
impl Metadata {
    /// Creates a new Metadata instance with required parameters.
    pub fn new() -> Self {
        Self { created_at: None }
    }
}
