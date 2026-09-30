/// Options for the set_pet_preferences operation.
#[derive(Debug, Clone)]
pub struct SetPetPreferencesOptions {
    pub nickname: String,
    pub tags: Option<Vec<String>>,
    pub note: Option<String>,
    pub renewal_date: Option<chrono::NaiveDate>,
}

impl SetPetPreferencesOptions {
    /// Creates a new SetPetPreferencesOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(nickname: String) -> Self {
        Self {
            nickname,
            tags: None,
            note: None,
            renewal_date: None,
        }
    }

    /// Sets the tags field.
    pub fn tags(mut self, tags: Vec<String>) -> Self {
        self.tags = Some(tags);
        self
    }

    /// Sets the note field.
    pub fn note(mut self, note: String) -> Self {
        self.note = Some(note);
        self
    }

    /// Sets the renewal_date field.
    pub fn renewal_date(mut self, renewal_date: chrono::NaiveDate) -> Self {
        self.renewal_date = Some(renewal_date);
        self
    }
}
