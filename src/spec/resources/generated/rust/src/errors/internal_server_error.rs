use std::fmt;

use crate::errors::server_error::ServerError;

/// InternalServerError represents an HTTP 500 Internal Server Error.
#[derive(Debug, Clone)]
pub struct InternalServerError {
    pub server_error: ServerError,
}

impl fmt::Display for InternalServerError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Internal server error (500): {}",
            self.server_error.api_error.message
        )
    }
}

impl std::error::Error for InternalServerError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        Some(&self.server_error)
    }
}

impl From<ServerError> for InternalServerError {
    fn from(server_error: ServerError) -> Self {
        Self { server_error }
    }
}
