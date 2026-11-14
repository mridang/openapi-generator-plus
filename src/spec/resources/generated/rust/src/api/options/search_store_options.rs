use crate::models::*;

/// Options for the search_store operation.
#[derive(Debug, Clone, Default)]
pub struct SearchStoreOptions {
    /// JSON-encoded query parameter (OAS `content`, not `schema`)
    pub q: Option<PhotoMetadata>,
    pub labels: Option<Vec<String>>,
    pub at: Option<chrono::NaiveTime>,
    pub x_filter: Option<std::collections::HashMap<String, String>>,
}

impl SearchStoreOptions {
    /// Creates a new SearchStoreOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the q field.
    pub fn q(mut self, q: PhotoMetadata) -> Self {
        self.q = Some(q);
        self
    }

    /// Sets the labels field.
    pub fn labels(mut self, labels: Vec<String>) -> Self {
        self.labels = Some(labels);
        self
    }

    /// Sets the at field.
    pub fn at(mut self, at: chrono::NaiveTime) -> Self {
        self.at = Some(at);
        self
    }

    /// Sets the x_filter field.
    pub fn x_filter(mut self, x_filter: std::collections::HashMap<String, String>) -> Self {
        self.x_filter = Some(x_filter);
        self
    }
}
