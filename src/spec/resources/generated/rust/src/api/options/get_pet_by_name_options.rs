use crate::models::*;

/// Options for the get_pet_by_name operation.
#[derive(Debug, Clone, Default)]
pub struct GetPetByNameOptions {
    pub category: String,
}

impl GetPetByNameOptions {
    /// Creates a new GetPetByNameOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }
}
