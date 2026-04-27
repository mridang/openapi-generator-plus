use std::collections::HashMap;
use std::sync::Arc;

use crate::api_client::ApiClient;
use crate::api_error::ApiError;
use crate::api_response::ApiResponse;
use crate::authenticator::Authenticator;
use crate::configuration::Configuration;
use crate::errors::bad_request_error::BadRequestError;
use crate::errors::client_error::ClientError;
use crate::errors::conflict_error::ConflictError;
use crate::errors::forbidden_error::ForbiddenError;
use crate::errors::internal_server_error::InternalServerError;
use crate::errors::not_found_error::NotFoundError;
use crate::errors::server_error::ServerError;
use crate::errors::unauthorized_error::UnauthorizedError;
use crate::errors::unprocessable_entity_error::UnprocessableEntityError;
use crate::header_selector::HeaderSelector;
use crate::trace_context_util;

/// InvokeApiParams holds parameters for the invoke_api call.
pub struct InvokeApiParams<'a> {
    pub method: &'a str,
    pub path: &'a str,
    pub query_params: HashMap<String, String>,
    pub header_params: HashMap<String, String>,
    pub body: Option<Vec<u8>>,
    pub accepts: Vec<&'a str>,
    pub content_type: &'a str,
    pub return_type: &'a str,
    pub auth: Option<&'a dyn Authenticator>,
}

/// BaseApi provides common functionality for all API classes.
pub struct BaseApi {
    config: Configuration,
    api_client: Arc<dyn ApiClient>,
    header_selector: HeaderSelector,
}

impl BaseApi {
    /// Creates a new BaseApi instance.
    pub fn new(api_client: Arc<dyn ApiClient>, config: Configuration) -> Self {
        Self {
            config,
            api_client,
            header_selector: HeaderSelector::new(),
        }
    }

    /// Dispatches an API request and returns the full response.
    pub fn invoke_api(
        &self,
        params: InvokeApiParams<'_>,
    ) -> Result<ApiResponse, Box<dyn std::error::Error + Send + Sync>> {
        let mut request_url = params.path.to_string();
        if !request_url.starts_with("http://") && !request_url.starts_with("https://") {
            request_url = format!("{}{}", self.config.base_url(), params.path);
        }

        // Merge authentication query params
        let mut query_params = params.query_params;
        if let Some(auth) = params.auth {
            for (k, v) in auth.query_params() {
                query_params.insert(k, v);
            }
        }

        // Build query string
        let query_string = build_query_string(&query_params);
        if !query_string.is_empty() {
            request_url = format!("{}?{}", request_url, query_string);
        }

        // Select headers
        let is_multipart = params.content_type == "multipart/form-data";
        let ct = if params.content_type.is_empty() {
            "application/json"
        } else {
            params.content_type
        };
        let selected = self
            .header_selector
            .select_headers(&params.accepts, ct, is_multipart);

        let mut headers = HashMap::new();
        if let Some(accept) = selected.get("Accept") {
            headers.insert("Accept".to_string(), accept.clone());
        }
        if let Some(content_type) = selected.get("Content-Type") {
            headers.insert("Content-Type".to_string(), content_type.clone());
        }

        // Merge config default headers
        for (k, v) in self.config.default_headers() {
            headers.insert(k, v);
        }

        // Merge operation-specific headers
        for (k, v) in &params.header_params {
            headers.insert(k.clone(), v.clone());
        }

        // Merge auth headers
        if let Some(auth) = params.auth {
            for (k, v) in auth.auth_headers() {
                headers.insert(k, v);
            }
            // Handle cookie params
            let cookies = auth.cookie_params();
            if !cookies.is_empty() {
                let cookie_parts: Vec<String> = cookies
                    .iter()
                    .map(|(k, v)| format!("{}={}", k, v))
                    .collect();
                let cookie_str = cookie_parts.join("; ");
                if let Some(existing) = headers.get("Cookie").cloned() {
                    headers.insert(
                        "Cookie".to_string(),
                        format!("{}; {}", existing, cookie_str),
                    );
                } else {
                    headers.insert("Cookie".to_string(), cookie_str);
                }
            }
        }

        // Inject trace context
        trace_context_util::inject_trace_context(&mut headers);

        // Serialize body
        let serialized_body = serialize_body(params.body, params.content_type)?;

        // Send request
        let response = self.api_client.send_request(
            params.method,
            &request_url,
            &headers,
            serialized_body.as_deref(),
        )?;

        // Check for errors
        if response.status_code < 200 || response.status_code >= 300 {
            return Err(throw_api_error(&response));
        }

        Ok(response)
    }
}

fn build_query_string(query_params: &HashMap<String, String>) -> String {
    if query_params.is_empty() {
        return String::new();
    }

    let parts: Vec<String> = query_params
        .iter()
        .map(|(k, v)| format!("{}={}", urlencoding::encode(k), urlencoding::encode(v)))
        .collect();

    parts.join("&")
}

fn serialize_body(
    body: Option<Vec<u8>>,
    content_type: &str,
) -> Result<Option<Vec<u8>>, Box<dyn std::error::Error + Send + Sync>> {
    let body = match body {
        Some(b) => b,
        None => return Ok(None),
    };

    if content_type == "multipart/form-data" {
        // Multipart is handled separately by the API client
        return Ok(None);
    }

    if content_type.starts_with("image/") || content_type == "application/octet-stream" {
        return Ok(Some(body));
    }

    if content_type == "text/plain" {
        return Ok(Some(body));
    }

    if content_type == "application/x-www-form-urlencoded" {
        return Ok(Some(body));
    }

    Ok(Some(body))
}

fn throw_api_error(response: &ApiResponse) -> Box<dyn std::error::Error + Send + Sync> {
    let code = response.status_code;
    let msg = format!("API returned status code {}", code);
    let body = response.body.clone();

    let base_err = ApiError::new(code, msg, body, response.headers.clone());

    if code >= 400 && code < 500 {
        let client_err = ClientError::from(base_err);
        return match code {
            400 => Box::new(BadRequestError::from(client_err)),
            401 => Box::new(UnauthorizedError::from(client_err)),
            403 => Box::new(ForbiddenError::from(client_err)),
            404 => Box::new(NotFoundError::from(client_err)),
            409 => Box::new(ConflictError::from(client_err)),
            422 => Box::new(UnprocessableEntityError::from(client_err)),
            _ => Box::new(client_err),
        };
    }

    if code >= 500 {
        let server_err = ServerError::from(base_err);
        return match code {
            500 => Box::new(InternalServerError::from(server_err)),
            _ => Box::new(server_err),
        };
    }

    Box::new(base_err)
}
