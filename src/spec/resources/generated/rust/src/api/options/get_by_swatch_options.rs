use crate::models::*;

/// Options for the get_by_swatch operation.
#[derive(Debug, Clone, Default)]
pub struct GetBySwatchOptions {
    pub query_swatch: Option<Swatch>,
    pub preferred_swatch: Option<Swatch>,
}

impl GetBySwatchOptions {
    /// Creates a new GetBySwatchOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the query_swatch field.
    pub fn query_swatch(mut self, query_swatch: Swatch) -> Self {
        self.query_swatch = Some(query_swatch);
        self
    }

    /// Sets the preferred_swatch field.
    pub fn preferred_swatch(mut self, preferred_swatch: Swatch) -> Self {
        self.preferred_swatch = Some(preferred_swatch);
        self
    }
}
