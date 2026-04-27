#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// WetFood is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct WetFood {
    #[serde(rename = "volumeMl")]
    pub volume_ml: i32,
}

#[allow(deprecated)]
impl WetFood {
    /// Creates a new WetFood instance with required parameters.
    pub fn new(volume_ml: i32) -> Self {
        Self { volume_ml }
    }
}
