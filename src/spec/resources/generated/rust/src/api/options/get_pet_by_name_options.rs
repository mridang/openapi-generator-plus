/// Options for the get_pet_by_name operation.
#[derive(Debug, Clone)]
pub struct GetPetByNameOptions {
    pub category: String,
    pub x_correlation_id: String,
}

impl GetPetByNameOptions {
    /// Creates a new GetPetByNameOptions, requiring every required parameter up front.
    ///
    /// Every field of this struct is required and is taken as a constructor
    /// argument so it cannot be silently omitted. This struct has no optional
    /// fields and therefore no chained setters.
    pub fn new(category: String, x_correlation_id: String) -> Self {
        Self {
            category,
            x_correlation_id,
        }
    }
}
