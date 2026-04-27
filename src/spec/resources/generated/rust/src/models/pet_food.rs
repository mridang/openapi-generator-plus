#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// PetFood Food for pets, discriminated by foodType
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "foodType")]
pub enum PetFood {
    #[serde(rename = "dry")]
    DryFood(DryFood),
    #[serde(rename = "wet")]
    WetFood(WetFood),
}
