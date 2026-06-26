use crate::models::*;

/// Options for the get_stock_item operation.
#[derive(Debug, Clone, Default)]
pub struct GetStockItemOptions {
    /// Only consider stock as of this instant
    pub as_of: Option<chrono::DateTime<chrono::Utc>>,
}

impl GetStockItemOptions {
    /// Creates a new GetStockItemOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the as_of field.
    pub fn as_of(mut self, as_of: chrono::DateTime<chrono::Utc>) -> Self {
        self.as_of = Some(as_of);
        self
    }
}
