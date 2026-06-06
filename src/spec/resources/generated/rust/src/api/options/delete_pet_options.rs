use crate::models::*;

/// Options for the delete_pet operation.
#[derive(Debug, Clone, Default)]
pub struct DeletePetOptions {
    /// Session cookie used for authentication
    pub api_key: Option<String>,
}

impl DeletePetOptions {
    /// Creates a new DeletePetOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the api_key field.
    pub fn api_key(mut self, api_key: String) -> Self {
        self.api_key = Some(api_key);
        self
    }
}
