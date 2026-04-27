#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// Medication is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct Medication {
    #[serde(rename = "drugName")]
    pub drug_name: String,
    #[serde(rename = "dosage", skip_serializing_if = "Option::is_none")]
    pub dosage: Option<String>,
}

#[allow(deprecated)]
impl Medication {
    /// Creates a new Medication instance with required parameters.
    pub fn new(drug_name: String) -> Self {
        Self {
            drug_name,
            dosage: None,
        }
    }
}
