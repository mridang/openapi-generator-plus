#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// DryFood is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct DryFood {
    #[serde(rename = "weightKg")]
    pub weight_kg: f64,
}

#[allow(deprecated)]
impl DryFood {
    /// Creates a new DryFood instance with required parameters.
    pub fn new(weight_kg: f64) -> Self {
        Self { weight_kg }
    }
}
