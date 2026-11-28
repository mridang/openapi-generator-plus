use std::collections::HashMap;

use crate::errors::SerializationError;

/// The headers the success response of the patch_memo operation declares,
/// read in their declared types.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct PatchMemoResponseHeaders {
    /// The revision of the memo
    x_revision: Option<i32>,
    /// The labels of the memo
    x_labels: Option<Vec<String>>,
    /// Whether the memo is archived
    /// Example:
    /// ```json
    /// false
    /// ```
    x_archived: Option<bool>,
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

impl PatchMemoResponseHeaders {
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
            x_labels: header_value(headers, "X-Labels")
                .map(|raw| raw.split(',').map(|item| item.trim().to_string()).collect()),
            x_archived: header_value(headers, "X-Archived")
                .map(|item| match item {
                    "true" => Ok(true),
                    "false" => Ok(false),
                    _ => Err(invalid("X-Archived", "boolean", item)),
                })
                .transpose()?,
        })
    }

    /// The revision of the memo
    pub fn x_revision(&self) -> Option<i32> {
        self.x_revision
    }

    /// The labels of the memo
    pub fn x_labels(&self) -> Option<&[String]> {
        self.x_labels.as_deref()
    }

    /// Whether the memo is archived
    /// Example:
    /// ```json
    /// false
    /// ```
    pub fn x_archived(&self) -> Option<bool> {
        self.x_archived
    }
}
