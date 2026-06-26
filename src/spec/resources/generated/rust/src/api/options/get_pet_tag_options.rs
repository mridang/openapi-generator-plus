use crate::models::*;

/// Options for the get_pet_tag operation.
#[derive(Debug, Clone, Default)]
pub struct GetPetTagOptions {
    pub colors: Option<Vec<String>>,
    pub sizes: Option<Vec<String>>,
    pub filter: Option<String>,
}

impl GetPetTagOptions {
    /// Creates a new GetPetTagOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the colors field.
    pub fn colors(mut self, colors: Vec<String>) -> Self {
        self.colors = Some(colors);
        self
    }

    /// Sets the sizes field.
    pub fn sizes(mut self, sizes: Vec<String>) -> Self {
        self.sizes = Some(sizes);
        self
    }

    /// Sets the filter field.
    pub fn filter(mut self, filter: String) -> Self {
        self.filter = Some(filter);
        self
    }
}
