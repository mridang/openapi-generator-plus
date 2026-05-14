use super::OpenIdConnectAuthenticator;

/// SsoAuthAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`OpenIdConnectAuthenticator`].
pub struct SsoAuthAuthenticator(OpenIdConnectAuthenticator);

impl SsoAuthAuthenticator {
    /// Creates a new `SsoAuthAuthenticator`.
    pub fn new(host: &str, client_id: &str, client_secret: &str, redirect_uri: &str) -> Self {
        Self(OpenIdConnectAuthenticator::new(host, "https://auth.example.com/.well-known/openid-configuration", client_id, client_secret, redirect_uri, vec![]))
    }
}

impl std::ops::Deref for SsoAuthAuthenticator {
    type Target = OpenIdConnectAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for SsoAuthAuthenticator {
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
