use crate::models::*;

/// Options for the set_pet_preferences operation.
#[derive(Debug, Clone)]
pub struct SetPetPreferencesOptions {
    pub nickname: String,
    pub tags: Option<Vec<String>>,
    pub note: Option<String>,
}

impl SetPetPreferencesOptions {
    /// Creates a new SetPetPreferencesOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(nickname: String, ) -> Self {
        Self {
            nickname,
            tags: None,
            note: None,
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
}
