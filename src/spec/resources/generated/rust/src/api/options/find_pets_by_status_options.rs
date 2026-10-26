/// Options for the find_pets_by_status operation.
#[derive(Debug, Clone, Default)]
pub struct FindPetsByStatusOptions {
    /// Status values that need to be considered for filter
    #[deprecated]
    pub status: Option<String>,
    /// Filter criteria as key-value pairs
    pub filter: Option<std::collections::HashMap<String, String>>,
    /// Only return pets born on or after this date
    pub born_after: Option<chrono::NaiveDate>,
    /// Reference date for the report
    pub report_date: Option<chrono::NaiveDate>,
}

impl FindPetsByStatusOptions {
    /// Creates a new FindPetsByStatusOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the status field.
    #[deprecated]
    pub fn status(mut self, status: String) -> Self {
        self.status = Some(status);
        self
    }

    /// Sets the filter field.
    pub fn filter(mut self, filter: std::collections::HashMap<String, String>) -> Self {
        self.filter = Some(filter);
        self
    }

    /// Sets the born_after field.
    pub fn born_after(mut self, born_after: chrono::NaiveDate) -> Self {
        self.born_after = Some(born_after);
        self
    }

    /// Sets the report_date field.
    pub fn report_date(mut self, report_date: chrono::NaiveDate) -> Self {
        self.report_date = Some(report_date);
        self
    }
}
