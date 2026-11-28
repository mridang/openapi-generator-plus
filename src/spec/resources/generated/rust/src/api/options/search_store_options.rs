use crate::models::*;
use std::collections::HashMap;
use std::time::Duration;

/// Options for the search_store operation.
#[derive(Debug, Clone, Default)]
pub struct SearchStoreOptions {
    /// JSON-encoded query parameter (OAS `content`, not `schema`)
    pub q: Option<PhotoMetadata>,
    pub labels: Option<Vec<String>>,
    pub at: Option<chrono::NaiveTime>,
    pub x_filter: Option<std::collections::HashMap<String, String>>,
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

impl SearchStoreOptions {
    /// Creates a new SearchStoreOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the q field.
    pub fn q(mut self, q: PhotoMetadata) -> Self {
        self.q = Some(q);
        self
    }

    /// Sets the labels field.
    pub fn labels(mut self, labels: Vec<String>) -> Self {
        self.labels = Some(labels);
        self
    }

    /// Sets the at field.
    pub fn at(mut self, at: chrono::NaiveTime) -> Self {
        self.at = Some(at);
        self
    }

    /// Sets the x_filter field.
    pub fn x_filter(mut self, x_filter: std::collections::HashMap<String, String>) -> Self {
        self.x_filter = Some(x_filter);
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
