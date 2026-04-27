use std::collections::HashMap;

use crate::api_response::ApiResponse;

/// ApiClient is the trait for HTTP clients. Implementations must provide
/// `send_request` to perform the actual HTTP call.
pub trait ApiClient: Send + Sync {
    /// Sends an HTTP request and returns the response.
    ///
    /// # Arguments
    ///
    /// * `method` - HTTP method (GET, POST, PUT, DELETE, etc.)
    /// * `url` - Fully qualified URL
    /// * `headers` - Caller-provided headers
    /// * `body` - Request body, or None
    ///
    /// # Returns
    ///
    /// An `ApiResponse` on success, or an error.
    fn send_request(
        &self,
        method: &str,
        url: &str,
        headers: &HashMap<String, String>,
        body: Option<&[u8]>,
    ) -> Result<ApiResponse, Box<dyn std::error::Error + Send + Sync>>;
}
