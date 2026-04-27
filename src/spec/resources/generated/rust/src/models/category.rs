#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// Category is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct Category {
    #[serde(rename = "id", skip_serializing_if = "Option::is_none")]
    pub id: Option<i64>,
    #[serde(rename = "name", skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
}

#[allow(deprecated)]
impl Category {
    /// Creates a new Category instance with required parameters.
    pub fn new() -> Self {
        Self {
            id: None,
            name: None,
        }
    }
}
