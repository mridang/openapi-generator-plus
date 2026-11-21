use std::collections::HashMap;
use std::time::Duration;

/// Options for the delete_pet operation.
#[derive(Debug, Clone, Default)]
pub struct DeletePetOptions {
    /// Session cookie used for authentication
    pub api_key: Option<String>,
    /// Replaces the client's end-to-end timeout for this call. Zero is refused
    /// with `ConfigurationError::InvalidTimeout` before anything is sent.
    pub timeout: Option<Duration>,
    /// Replaces the client's retry budget for this call; `Some(0)` turns the
    /// retries off.
    pub max_retries: Option<u32>,
    /// Headers added to this call. They win over the client's default headers
    /// and the operation's header parameters; the authentication headers are
    /// applied after them.
    pub headers: HashMap<String, String>,
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

    /// Sets the end-to-end timeout of this call, replacing the client's.
    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = Some(timeout);
        self
    }

    /// Sets the retry budget of this call, replacing the client's.
    pub fn max_retries(mut self, max_retries: u32) -> Self {
        self.max_retries = Some(max_retries);
        self
    }

    /// Adds one header to this call, replacing an earlier one of the same name.
    pub fn header(mut self, name: &str, value: &str) -> Self {
        self.headers.insert(name.to_string(), value.to_string());
        self
    }

    /// The per-call settings the transport layer applies.
    pub(crate) fn call_options(&self) -> crate::api::base_api::CallOptions {
        crate::api::base_api::CallOptions {
            timeout: self.timeout,
            max_retries: self.max_retries,
            headers: self.headers.clone(),
            idle_timeout: None,
        }
    }
}
