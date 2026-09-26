use super::ApiKeyAuthenticator;

/// ApiKeyQueryAuthenticator is a scheme-specific authenticator generated from the
/// OpenAPI security scheme. Delegates to [`ApiKeyAuthenticator`].
pub struct ApiKeyQueryAuthenticator(ApiKeyAuthenticator);

impl ApiKeyQueryAuthenticator {
    /// Creates a new `ApiKeyQueryAuthenticator`.
    ///
    /// # Errors
    ///
    /// Returns the [`ApiKeyAuthenticator`] constructor's
    /// [`ConfigurationError`](crate::errors::configuration_error::ConfigurationError)
    /// when the credentials are invalid.
    pub fn new(
        host: &str,
        api_key: &str,
    ) -> Result<Self, crate::errors::configuration_error::ConfigurationError> {
        Ok(Self(ApiKeyAuthenticator::new(host, "api_key", api_key, ApiKeyLocation::Query)?))
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
