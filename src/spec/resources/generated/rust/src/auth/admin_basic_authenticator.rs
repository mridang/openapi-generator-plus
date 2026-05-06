use super::BasicAuthenticator;

/// AdminBasicAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`BasicAuthenticator`].
pub struct AdminBasicAuthenticator(BasicAuthenticator);

impl AdminBasicAuthenticator {
    /// Creates a new `AdminBasicAuthenticator`.
    pub fn new(host: &str, username: &str, password: &str) -> Self {
        Self(BasicAuthenticator::new(host, username, password))
    }
}

impl std::ops::Deref for AdminBasicAuthenticator {
    type Target = BasicAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for AdminBasicAuthenticator {
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
