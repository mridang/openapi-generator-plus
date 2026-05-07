use super::ApiKeyAuthenticator;

/// ApiKeyQueryAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`ApiKeyAuthenticator`].
pub struct ApiKeyQueryAuthenticator(ApiKeyAuthenticator);

impl ApiKeyQueryAuthenticator {
    /// Creates a new `ApiKeyQueryAuthenticator`.
    pub fn new(host: &str, api_key: &str) -> Self {
        Self(ApiKeyAuthenticator::new(host, "api_key", api_key, ApiKeyLocation::Query))
    }
}

impl std::ops::Deref for ApiKeyQueryAuthenticator {
    type Target = ApiKeyAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for ApiKeyQueryAuthenticator {
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

    fn as_http_aware_mut(&mut self) -> Option<&mut dyn crate::auth::HttpAwareAuthenticator> {
        self.0.as_http_aware_mut()
    }
}
