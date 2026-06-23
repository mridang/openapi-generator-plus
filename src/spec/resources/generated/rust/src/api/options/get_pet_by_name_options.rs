use crate::models::*;

/// Options for the get_pet_by_name operation.
#[derive(Debug, Clone)]
pub struct GetPetByNameOptions {
    pub category: String,
}

impl GetPetByNameOptions {
    /// Creates a new GetPetByNameOptions, requiring every required parameter up front.
    ///
    /// Every field of this struct is required and is taken as a constructor
    /// argument so it cannot be silently omitted. This struct has no optional
    /// fields and therefore no chained setters.
    pub fn new(category: String) -> Self {
        Self { category }
    }
}
