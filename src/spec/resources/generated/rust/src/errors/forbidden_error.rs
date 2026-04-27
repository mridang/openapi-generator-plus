use std::fmt;

use crate::errors::client_error::ClientError;

/// ForbiddenError represents an HTTP 403 Forbidden error.
#[derive(Debug, Clone)]
pub struct ForbiddenError {
    pub client_error: ClientError,
}

impl fmt::Display for ForbiddenError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Forbidden (403): {}",
            self.client_error.api_error.message
        )
    }
}

impl std::error::Error for ForbiddenError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        Some(&self.client_error)
    }
}

impl From<ClientError> for ForbiddenError {
    fn from(client_error: ClientError) -> Self {
        Self { client_error }
    }
}
