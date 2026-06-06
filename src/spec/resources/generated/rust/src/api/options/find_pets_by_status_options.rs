use crate::models::*;

/// Options for the find_pets_by_status operation.
#[derive(Debug, Clone, Default)]
pub struct FindPetsByStatusOptions {
    /// Status values that need to be considered for filter
    pub status: Option<String>,
    /// Filter criteria as key-value pairs
    pub filter: Option<std::collections::HashMap<String, String>>,
}

impl FindPetsByStatusOptions {
    /// Creates a new FindPetsByStatusOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the status field.
    pub fn status(mut self, status: String) -> Self {
        self.status = Some(status);
        self
    }

    /// Sets the filter field.
    pub fn filter(mut self, filter: std::collections::HashMap<String, String>) -> Self {
        self.filter = Some(filter);
        self
    }
}
