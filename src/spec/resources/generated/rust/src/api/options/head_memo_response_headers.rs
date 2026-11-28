use std::collections::HashMap;

use crate::errors::SerializationError;

/// The headers the success response of the head_memo operation declares,
/// read in their declared types.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct HeadMemoResponseHeaders {
    /// The revision of the memo
    x_revision: Option<i32>,
}

/// Returns the value of the header called `name`, compared without case.
fn header_value<'a>(headers: &'a HashMap<String, String>, name: &str) -> Option<&'a str> {
    headers
        .iter()
        .find(|(key, _)| key.eq_ignore_ascii_case(name))
        .map(|(_, value)| value.as_str())
}

/// The error for a header value that does not parse as its declared type.
fn invalid(name: &str, kind: &str, value: &str) -> SerializationError {
    SerializationError::new(
        format!(
            "Response header '{}' is not a valid {}: '{}'",
            name, kind, value
        ),
        None,
    )
}

impl HeadMemoResponseHeaders {
    /// Reads the declared headers from the response headers, as
    /// `ApiResult::headers()` returns them; names are compared without case. A
    /// header that is absent is left `None`, and a value that does not parse as
    /// its declared type is a `SerializationError`.
    pub fn from_headers(headers: &HashMap<String, String>) -> Result<Self, SerializationError> {
        Ok(Self {
            x_revision: header_value(headers, "X-Revision")
                .map(|item| {
                    item.parse::<i32>()
                        .map_err(|_| invalid("X-Revision", "integer", item))
                })
                .transpose()?,
        })
    }

    /// The revision of the memo
    pub fn x_revision(&self) -> Option<i32> {
        self.x_revision
    }
}
