use super::OAuth2PasswordAuthenticator;

/// LegacyAuthPasswordAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`OAuth2PasswordAuthenticator`].
pub struct LegacyAuthPasswordAuthenticator(OAuth2PasswordAuthenticator);

impl LegacyAuthPasswordAuthenticator {
    /// Creates a new `LegacyAuthPasswordAuthenticator`.
    pub fn new(host: &str, client_id: &str, client_secret: &str, username: &str, password: &str) -> Self {
        Self(OAuth2PasswordAuthenticator::new(host, client_id, client_secret, "https://auth.example.com/oauth/token", Some("https://auth.example.com/oauth/refresh"), username, password, &[]))
    }
}

impl std::ops::Deref for LegacyAuthPasswordAuthenticator {
    type Target = OAuth2PasswordAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for LegacyAuthPasswordAuthenticator {
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
