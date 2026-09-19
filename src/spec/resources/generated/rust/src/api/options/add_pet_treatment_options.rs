use crate::auth::Authenticator;
use crate::models::*;
use std::sync::Arc;

/// Options for the add_pet_treatment operation.
#[derive(Clone, Default)]
pub struct AddPetTreatmentOptions {
    /// Per-operation authenticator. When set, it overrides the client's
    /// configured credentials for this call only.
    pub auth: Option<Arc<dyn Authenticator>>,
}

impl AddPetTreatmentOptions {
    /// Creates a new AddPetTreatmentOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the per-operation authenticator. When set, it overrides the
    /// client's configured credentials for this call only.
    pub fn auth(mut self, auth: Arc<dyn Authenticator>) -> Self {
        self.auth = Some(auth);
        self
    }
}
