use crate::models::*;
use crate::auth::Authenticator;
use std::sync::Arc;

/// Options for the add_pet operation.
#[derive(Clone, Default)]
pub struct AddPetOptions {
    /// Per-operation authenticator. When set, it overrides the client's
    /// configured credentials for this call only.
    pub auth: Option<Arc<dyn Authenticator>>,
}

impl AddPetOptions {
    /// Creates a new AddPetOptions with default values.
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
