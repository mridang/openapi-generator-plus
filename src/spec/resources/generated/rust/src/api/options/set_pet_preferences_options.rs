use crate::models::*;

/// Options for the set_pet_preferences operation.
#[derive(Debug, Clone, Default)]
pub struct SetPetPreferencesOptions {
    pub nickname: String,
    pub tags: Option<Vec<String>>,
    pub note: Option<String>,
}

impl SetPetPreferencesOptions {
    /// Creates a new SetPetPreferencesOptions with default values.
    pub fn new() -> Self {
        Self::default()
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
