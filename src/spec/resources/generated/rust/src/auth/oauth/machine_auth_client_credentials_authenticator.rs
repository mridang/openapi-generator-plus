use super::OAuth2ClientCredentialsAuthenticator;

/// MachineAuthClientCredentialsAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`OAuth2ClientCredentialsAuthenticator`].
pub struct MachineAuthClientCredentialsAuthenticator(OAuth2ClientCredentialsAuthenticator);

impl MachineAuthClientCredentialsAuthenticator {
    /// Creates a new `MachineAuthClientCredentialsAuthenticator`.
    pub fn new(host: &str, client_id: &str, client_secret: &str) -> Self {
        Self(OAuth2ClientCredentialsAuthenticator::new(host, client_id, client_secret, "https://auth.example.com/oauth/token", vec![]))
    }
}

impl std::ops::Deref for MachineAuthClientCredentialsAuthenticator {
    type Target = OAuth2ClientCredentialsAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for MachineAuthClientCredentialsAuthenticator {
    fn host(&self) -> &str {
        self.0.host()
    }

    fn auth_headers<'a>(
        &'a self,
    ) -> std::pin::Pin<
        Box<
            dyn std::future::Future<Output = std::collections::HashMap<String, String>>
                + Send
                + 'a,
        >,
    > {
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
