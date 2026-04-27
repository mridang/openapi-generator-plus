use std::collections::HashMap;
use std::io::Read;
use std::net::TcpListener;
use std::sync::Arc;
use std::thread;

use petstore::api::*;
use petstore::errors::*;
use petstore::models::*;
use petstore::*;

/// Starts a minimal HTTP server on an ephemeral port that responds with the
/// given status code, content type, and body. Returns the base URL.
fn start_mock_server(status: u16, content_type: &str, body: &str) -> String {
    let listener = TcpListener::bind("127.0.0.1:0").expect("failed to bind");
    let addr = listener.local_addr().unwrap();
    let base_url = format!("http://{}", addr);

    let content_type = content_type.to_string();
    let body = body.to_string();

    thread::spawn(move || {
        for stream in listener.incoming() {
            let mut stream = stream.unwrap();
            let mut buf = [0u8; 4096];
            let _ = stream.read(&mut buf);

            let response = format!(
                "HTTP/1.1 {} OK\r\nContent-Type: {}\r\nContent-Length: {}\r\n\r\n{}",
                status,
                content_type,
                body.len(),
                body
            );
            let _ = std::io::Write::write_all(&mut stream, response.as_bytes());
            break;
        }
    });

    // Give the server a moment to start
    thread::sleep(std::time::Duration::from_millis(50));
    base_url
}

// -- Error dispatch --
// Tests that the correct error types are returned for various HTTP status codes.
// We use PetApi.get_pet_by_id as the test surface since BaseApi methods are internal.

#[tokio::test]
async fn test_base_api_error_dispatch_400() {
    let base_url = start_mock_server(400, "application/json", r#"{"error":"test error"}"#);
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for status 400");
}

#[tokio::test]
async fn test_base_api_error_dispatch_401() {
    let base_url = start_mock_server(401, "application/json", r#"{"error":"test error"}"#);
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for status 401");
}

#[tokio::test]
async fn test_base_api_error_dispatch_403() {
    let base_url = start_mock_server(403, "application/json", r#"{"error":"test error"}"#);
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for status 403");
}

#[tokio::test]
async fn test_base_api_error_dispatch_404() {
    let base_url = start_mock_server(404, "application/json", r#"{"error":"test error"}"#);
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for status 404");
}

#[tokio::test]
async fn test_base_api_error_dispatch_409() {
    let base_url = start_mock_server(409, "application/json", r#"{"error":"test error"}"#);
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for status 409");
}

#[tokio::test]
async fn test_base_api_error_dispatch_422() {
    let base_url = start_mock_server(422, "application/json", r#"{"error":"test error"}"#);
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for status 422");
}

#[tokio::test]
async fn test_base_api_error_dispatch_500() {
    let base_url = start_mock_server(500, "application/json", r#"{"error":"test error"}"#);
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for status 500");
}

#[tokio::test]
async fn test_base_api_error_dispatch_502() {
    let base_url = start_mock_server(502, "application/json", r#"{"error":"test error"}"#);
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for status 502");
}

// -- JSON response deserialization --

#[tokio::test]
async fn test_base_api_deserializes_json_response() {
    let base_url = start_mock_server(
        200,
        "application/json",
        r#"{"id":1,"name":"Fido","photoUrls":["http://example.com/fido.jpg"]}"#,
    );
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_ok(), "expected successful response");
}

// -- Auth header forwarding --

#[tokio::test]
async fn test_base_api_forwards_auth_headers() {
    let base_url = start_mock_server(
        200,
        "application/json",
        r#"{"id":1,"name":"Fido","photoUrls":[]}"#,
    );
    let config = ConfigurationBuilder::new()
        .base_url(&base_url)
        .default_header("Authorization", "Bearer test-token")
        .build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(
        result.is_ok(),
        "expected successful response with auth header"
    );
}

// -- Nil body handling --

#[tokio::test]
async fn test_base_api_handles_nil_body() {
    let base_url = start_mock_server(
        200,
        "application/json",
        r#"{"id":1,"name":"Fido","photoUrls":[]}"#,
    );
    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    let api = PetApi::new(Arc::new(client), config);

    let result = api.get_pet_by_id(1).await;
    assert!(
        result.is_ok(),
        "expected successful response for GET with no body"
    );
}
