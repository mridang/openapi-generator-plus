use super::BearerAuthenticator;

/// ServiceTokenAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`BearerAuthenticator`].
pub struct ServiceTokenAuthenticator(BearerAuthenticator);

impl ServiceTokenAuthenticator {
    /// Creates a new `ServiceTokenAuthenticator`.
    pub fn new(host: &str, token: &str) -> Self {
        Self(BearerAuthenticator::new(host, token))
    }
}

impl std::ops::Deref for ServiceTokenAuthenticator {
    type Target = BearerAuthenticator;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl crate::auth::Authenticator for ServiceTokenAuthenticator {
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
