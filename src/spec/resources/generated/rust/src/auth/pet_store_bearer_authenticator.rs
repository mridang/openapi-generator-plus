use super::BearerAuthenticator;

/// PetStoreBearerAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`BearerAuthenticator`].
pub struct PetStoreBearerAuthenticator(BearerAuthenticator);

impl PetStoreBearerAuthenticator {
    /// Creates a new `PetStoreBearerAuthenticator`.
    pub fn new(host: &str, token: &str) -> Self {
        Self(BearerAuthenticator::new(host, token))
    }
}

impl std::ops::Deref for PetStoreBearerAuthenticator {
    type Target = BearerAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for PetStoreBearerAuthenticator {
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
