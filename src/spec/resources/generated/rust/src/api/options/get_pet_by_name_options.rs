use crate::models::*;

/// Options for the get_pet_by_name operation.
#[derive(Debug, Clone)]
pub struct GetPetByNameOptions {
    pub category: String,
}

impl GetPetByNameOptions {
    /// Creates a new GetPetByNameOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(category: String) -> Self {
        Self { category }
    }
}
