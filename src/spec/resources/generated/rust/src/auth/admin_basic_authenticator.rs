use super::BasicAuthenticator;

/// AdminBasicAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`BasicAuthenticator`].
pub struct AdminBasicAuthenticator(BasicAuthenticator);

impl AdminBasicAuthenticator {
    /// Creates a new `AdminBasicAuthenticator`.
    ///
    /// # Errors
    ///
    /// Returns the [`BasicAuthenticator`] constructor's
    /// [`ConfigurationError`](crate::errors::configuration_error::ConfigurationError)
    /// when the credentials are invalid.
    pub fn new(
        host: &str,
        username: &str,
        password: &str,
    ) -> Result<Self, crate::errors::configuration_error::ConfigurationError> {
        Ok(Self(BasicAuthenticator::new(host, username, password)?))
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

    fn try_auth_headers<'a>(
        &'a self,
    ) -> std::pin::Pin<
        Box<
            dyn std::future::Future<
                    Output = Result<
                        std::collections::HashMap<String, String>,
                        Box<dyn std::error::Error + Send + Sync>,
                    >,
                > + Send
                + 'a,
        >,
    > {
        self.0.try_auth_headers()
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
