use super::OAuth2AuthorizationCodeAuthenticator;

/// UserAuthAuthorizationCodeAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`OAuth2AuthorizationCodeAuthenticator`].
pub struct UserAuthAuthorizationCodeAuthenticator(OAuth2AuthorizationCodeAuthenticator);

impl UserAuthAuthorizationCodeAuthenticator {
    /// Creates a new `UserAuthAuthorizationCodeAuthenticator`.
    pub fn new(host: &str, client_id: &str, client_secret: &str, redirect_uri: &str) -> Self {
        Self(OAuth2AuthorizationCodeAuthenticator::new(host, client_id, client_secret, "https://auth.example.com/authorize", "https://auth.example.com/oauth/token", redirect_uri, vec![], "https://auth.example.com/oauth/refresh"))
    }
}

impl std::ops::Deref for UserAuthAuthorizationCodeAuthenticator {
    type Target = OAuth2AuthorizationCodeAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for UserAuthAuthorizationCodeAuthenticator {
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
