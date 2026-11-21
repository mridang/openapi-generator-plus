use crate::models::*;
use std::collections::HashMap;
use std::time::Duration;

/// Options for the upload_pet_document operation.
#[derive(Debug, Clone)]
pub struct UploadPetDocumentOptions {
    pub file: Vec<u8>,
    pub document_type: Option<String>,
    pub notes: Option<String>,
    pub is_primary: Option<bool>,
    pub page_count: Option<i64>,
    pub taken_at: Option<crate::date_time::DateTime>,
    pub swatch: Option<Swatch>,
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

impl UploadPetDocumentOptions {
    /// Creates a new UploadPetDocumentOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(file: Vec<u8>) -> Self {
        Self {
            file,
            document_type: None,
            notes: None,
            is_primary: None,
            page_count: None,
            taken_at: None,
            swatch: None,
            timeout: None,
            max_retries: None,
            headers: HashMap::new(),
        }
    }

    /// Sets the document_type field.
    pub fn document_type(mut self, document_type: String) -> Self {
        self.document_type = Some(document_type);
        self
    }

    /// Sets the notes field.
    pub fn notes(mut self, notes: String) -> Self {
        self.notes = Some(notes);
        self
    }

    /// Sets the is_primary field.
    pub fn is_primary(mut self, is_primary: bool) -> Self {
        self.is_primary = Some(is_primary);
        self
    }

    /// Sets the page_count field.
    pub fn page_count(mut self, page_count: i64) -> Self {
        self.page_count = Some(page_count);
        self
    }

    /// Sets the taken_at field.
    pub fn taken_at(mut self, taken_at: crate::date_time::DateTime) -> Self {
        self.taken_at = Some(taken_at);
        self
    }

    /// Sets the swatch field.
    pub fn swatch(mut self, swatch: Swatch) -> Self {
        self.swatch = Some(swatch);
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
        }
    }
}
