use std::collections::HashMap;

use crate::api_client::ApiClient;
use crate::auth::http_aware_authenticator::HttpAwareAuthenticator;
use crate::auth::oauth::oauth2_token_manager::OAuth2TokenManager;
use crate::authenticator::Authenticator;

/// OAuth2ClientCredentialsAuthenticator provides OAuth2 client credentials
/// flow authentication.
///
/// Implements HttpAwareAuthenticator so that token exchange requests use the
/// shared ApiClient with the same transport configuration (proxy, TLS, timeouts)
/// as regular API calls.
pub struct OAuth2ClientCredentialsAuthenticator {
    host: String,
    client_id: String,
    client_secret: String,
    token_url: String,
    scopes: Vec<String>,
    token_manager: OAuth2TokenManager,
}

impl OAuth2ClientCredentialsAuthenticator {
    /// Creates a new client credentials authenticator.
    pub fn new(
        host: &str,
        client_id: &str,
        client_secret: &str,
        token_url: &str,
        scopes: Vec<String>,
    ) -> Self {
        Self {
            host: host.to_string(),
            client_id: client_id.to_string(),
            client_secret: client_secret.to_string(),
            token_url: token_url.to_string(),
            scopes,
            token_manager: OAuth2TokenManager::new(),
        }
    }
}

impl Authenticator for OAuth2ClientCredentialsAuthenticator {
    fn host(&self) -> &str {
        &self.host
    }

    fn auth_headers(&self) -> HashMap<String, String> {
        let mut params = HashMap::new();
        params.insert("grant_type".to_string(), "client_credentials".to_string());
        params.insert("client_id".to_string(), self.client_id.clone());
        params.insert("client_secret".to_string(), self.client_secret.clone());
        if !self.scopes.is_empty() {
            params.insert("scope".to_string(), self.scopes.join(" "));
        }

        match self.token_manager.get_access_token(&self.token_url, &params) {
            Ok(token) => {
                let mut headers = HashMap::new();
                headers.insert(
                    "Authorization".to_string(),
                    format!("Bearer {}", token),
                );
                headers
            }
            Err(_) => HashMap::new(),
        }
    }
}

impl HttpAwareAuthenticator for OAuth2ClientCredentialsAuthenticator {
    fn set_api_client(&mut self, client: Box<dyn ApiClient>) {
        self.token_manager.set_api_client(client);
    }
}
