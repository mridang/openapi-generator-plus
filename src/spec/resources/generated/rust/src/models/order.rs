#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// Order is a model class generated from the OpenAPI schema.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
pub struct Order {
    #[serde(rename = "id", skip_serializing_if = "Option::is_none")]
    pub id: Option<i64>,
    #[serde(rename = "petId", skip_serializing_if = "Option::is_none")]
    pub pet_id: Option<i64>,
    #[serde(rename = "quantity", skip_serializing_if = "Option::is_none")]
    pub quantity: Option<i32>,
    #[serde(rename = "shipDate", skip_serializing_if = "Option::is_none")]
    pub ship_date: Option<String>,
    /// Order Status
    #[serde(rename = "status", skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,
    #[serde(rename = "complete", skip_serializing_if = "Option::is_none")]
    pub complete: Option<bool>,
}

#[allow(deprecated)]
impl Order {
    /// Creates a new Order instance with required parameters.
    pub fn new() -> Self {
        Self {
            id: None,
            pet_id: None,
            quantity: None,
            ship_date: None,
            status: None,
            complete: None,
        }
    }
}
