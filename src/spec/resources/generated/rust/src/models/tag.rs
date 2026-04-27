#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// Tag Tags are deprecated, use categories instead
#[deprecated]
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct Tag {
    #[serde(rename = "id", skip_serializing_if = "Option::is_none")]
    pub id: Option<i64>,
    #[serde(rename = "name", skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
}

#[allow(deprecated)]
impl Tag {
    /// Creates a new Tag instance with required parameters.
    pub fn new() -> Self {
        Self {
            id: None,
            name: None,
        }
    }
}
