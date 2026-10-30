/// Options for the get_pet_summary operation.
#[derive(Debug, Clone)]
pub struct GetPetSummaryOptions {
    /// Session token: "a\<b\>" & it's required
    pub session: String,
    /// Extra dates to summarise
    pub days: Option<Vec<chrono::NaiveDate>>,
}

impl GetPetSummaryOptions {
    /// Creates a new GetPetSummaryOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(session: String) -> Self {
        Self {
            session,
            days: None,
        }
    }

    /// Sets the days field.
    pub fn days(mut self, days: Vec<chrono::NaiveDate>) -> Self {
        self.days = Some(days);
        self
    }
}
