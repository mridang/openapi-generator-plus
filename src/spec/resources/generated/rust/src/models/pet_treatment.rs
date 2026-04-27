#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// PetTreatment A treatment that can match a medication, a surgery, or both
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(untagged)]
pub enum PetTreatment {
    Medication(Medication),
    Surgery(Surgery),
}
