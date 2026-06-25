use crate::auth::Authenticator;
use crate::models::*;
use std::sync::Arc;

/// Options for the delete_pet operation.
#[derive(Clone, Default)]
pub struct DeletePetOptions {
    /// Session cookie used for authentication
    pub api_key: Option<String>,
    /// Per-operation authenticator. When set, it overrides the client's
    /// configured credentials for this call only.
    pub auth: Option<Arc<dyn Authenticator>>,
}

impl DeletePetOptions {
    /// Creates a new DeletePetOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the api_key field.
    pub fn api_key(mut self, api_key: String) -> Self {
        self.api_key = Some(api_key);
        self
    }

    /// Sets the per-operation authenticator. When set, it overrides the
    /// client's configured credentials for this call only.
    pub fn auth(mut self, auth: Arc<dyn Authenticator>) -> Self {
        self.auth = Some(auth);
        self
    }
}
