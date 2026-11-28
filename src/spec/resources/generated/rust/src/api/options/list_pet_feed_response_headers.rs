use std::collections::HashMap;

use crate::errors::SerializationError;

/// The headers the success response of the list_pet_feed operation declares,
/// read in their declared types.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct ListPetFeedResponseHeaders {
    /// The next page as rel="next", when there is one
    link: Option<String>,
}

/// Returns the value of the header called `name`, compared without case.
fn header_value<'a>(headers: &'a HashMap<String, String>, name: &str) -> Option<&'a str> {
    headers
        .iter()
        .find(|(key, _)| key.eq_ignore_ascii_case(name))
        .map(|(_, value)| value.as_str())
}

impl ListPetFeedResponseHeaders {
    /// Reads the declared headers from the response headers, as
    /// `ApiResult::headers()` returns them; names are compared without case. A
    /// header that is absent is left `None`, and a value that does not parse as
    /// its declared type is a `SerializationError`.
    pub fn from_headers(headers: &HashMap<String, String>) -> Result<Self, SerializationError> {
        Ok(Self {
            link: header_value(headers, "Link").map(str::to_string),
        })
    }

    /// The next page as rel="next", when there is one
    pub fn link(&self) -> Option<&str> {
        self.link.as_deref()
    }
}
