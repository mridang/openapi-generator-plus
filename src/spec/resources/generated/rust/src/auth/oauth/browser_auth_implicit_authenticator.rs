use super::OAuth2ImplicitAuthenticator;

/// BrowserAuthImplicitAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`OAuth2ImplicitAuthenticator`].
pub struct BrowserAuthImplicitAuthenticator(OAuth2ImplicitAuthenticator);

impl BrowserAuthImplicitAuthenticator {
    /// Creates a new `BrowserAuthImplicitAuthenticator`.
    pub fn new(host: &str, client_id: &str) -> Self {
        Self(OAuth2ImplicitAuthenticator::new(host, client_id, "https://auth.example.com/authorize", &[]))
    }
}

impl std::ops::Deref for BrowserAuthImplicitAuthenticator {
    type Target = OAuth2ImplicitAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for BrowserAuthImplicitAuthenticator {
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
