use std::collections::HashMap;
use std::time::Duration;

/// Options for the get_pet_by_note operation.
#[derive(Debug, Clone)]
pub struct GetPetByNoteOptions {
    pub note: Option<String>,
    pub rank: Option<i64>,
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

impl GetPetByNoteOptions {
    /// Creates a new GetPetByNoteOptions, requiring every required parameter up front.
    ///
    /// Every field of this struct is required and is taken as a constructor
    /// argument so it cannot be silently omitted. The per-call timeout, retry
    /// budget and headers start unset and have chained setters below.
    pub fn new(note: Option<String>, rank: Option<i64>) -> Self {
        Self {
            note,
            rank,
            timeout: None,
            max_retries: None,
            headers: HashMap::new(),
        }
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
        }
    }
}
