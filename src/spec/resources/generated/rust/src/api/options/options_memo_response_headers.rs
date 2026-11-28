use std::collections::HashMap;

use crate::errors::SerializationError;

/// The headers the success response of the options_memo operation declares,
/// read in their declared types.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct OptionsMemoResponseHeaders {
    /// The `Allow` response header.
    allow: Option<String>,
}

/// Returns the value of the header called `name`, compared without case.
fn header_value<'a>(headers: &'a HashMap<String, String>, name: &str) -> Option<&'a str> {
    headers
        .iter()
        .find(|(key, _)| key.eq_ignore_ascii_case(name))
        .map(|(_, value)| value.as_str())
}

impl OptionsMemoResponseHeaders {
    /// Reads the declared headers from the response headers, as
    /// `ApiResult::headers()` returns them; names are compared without case. A
    /// header that is absent is left `None`, and a value that does not parse as
    /// its declared type is a `SerializationError`.
    pub fn from_headers(headers: &HashMap<String, String>) -> Result<Self, SerializationError> {
        Ok(Self {
            allow: header_value(headers, "Allow").map(str::to_string),
        })
    }

    /// The `Allow` response header.
    pub fn allow(&self) -> Option<&str> {
        self.allow.as_deref()
    }
}
