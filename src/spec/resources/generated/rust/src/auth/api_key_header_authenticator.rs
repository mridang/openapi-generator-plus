use super::ApiKeyAuthenticator;

/// ApiKeyHeaderAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`ApiKeyAuthenticator`].
pub struct ApiKeyHeaderAuthenticator(ApiKeyAuthenticator);

impl ApiKeyHeaderAuthenticator {
    /// Creates a new `ApiKeyHeaderAuthenticator`.
    pub fn new(host: &str, api_key: &str) -> Self {
        Self(ApiKeyAuthenticator::new(host, "X-API-Key", api_key, ApiKeyLocation::Header))
    }
}

impl std::ops::Deref for ApiKeyHeaderAuthenticator {
    type Target = ApiKeyAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for ApiKeyHeaderAuthenticator {
    fn host(&self) -> &str {
        self.0.host()
    }

    fn auth_headers(&self) -> std::collections::HashMap<String, String> {
        self.0.auth_headers()
    }

    fn query_params(&self) -> std::collections::HashMap<String, String> {
        self.0.query_params()
    }

    fn cookie_params(&self) -> std::collections::HashMap<String, String> {
        self.0.cookie_params()
    }
}
