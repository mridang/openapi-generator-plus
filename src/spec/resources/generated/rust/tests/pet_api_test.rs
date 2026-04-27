use std::collections::HashMap;
use std::io::Read;
use std::net::TcpListener;
use std::sync::Arc;
use std::thread;

use petstore::api::*;
use petstore::models::*;
use petstore::*;

/// Returns the Prism mock server base URL from the environment.
/// Tests are skipped if the Prism server is not available.
fn prism_base_url() -> Option<String> {
    std::env::var("API_BASE_URL").ok()
}

fn new_pet_api_for_integration() -> Option<PetApi> {
    let base_url = prism_base_url()?;
    let config = ConfigurationBuilder::new()
        .base_url(&base_url)
        .default_header("Authorization", "Bearer test-token")
        .build();
    let client = DefaultApiClient::new(None);
    Some(PetApi::new(Arc::new(client), config))
}

/// Starts a minimal HTTP mock server and returns a PetApi configured against it.
fn new_pet_api_for_mock(status: u16, content_type: &str, body: &str) -> (PetApi, String) {
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

    thread::sleep(std::time::Duration::from_millis(50));

    let config = ConfigurationBuilder::new().base_url(&base_url).build();
    let client = DefaultApiClient::new(None);
    (PetApi::new(Arc::new(client), config), base_url)
}

#[tokio::test]
async fn test_pet_api_get_pet_by_id() {
    if let Some(api) = new_pet_api_for_integration() {
        let result = api.get_pet_by_id(1).await;
        assert!(result.is_ok(), "GetPetById failed: {:?}", result.err());
    }
}

#[tokio::test]
async fn test_pet_api_find_pets_by_status() {
    if let Some(api) = new_pet_api_for_integration() {
        let result = api.find_pets_by_status(None).await;
        assert!(
            result.is_ok(),
            "FindPetsByStatus failed: {:?}",
            result.err()
        );
    }
}

#[tokio::test]
async fn test_pet_api_get_pet_passport() {
    if let Some(api) = new_pet_api_for_integration() {
        let result = api.get_pet_passport(1).await;
        assert!(result.is_ok(), "GetPetPassport failed: {:?}", result.err());
    }
}

#[tokio::test]
async fn test_pet_api_error_handling_not_found() {
    let (api, _) = new_pet_api_for_mock(404, "application/json", r#"{"message":"Pet not found"}"#);

    let result = api.get_pet_by_id(99999).await;
    assert!(result.is_err(), "expected error for non-existent pet");
}

#[tokio::test]
async fn test_pet_api_error_handling_server_error() {
    let (api, _) = new_pet_api_for_mock(
        500,
        "application/json",
        r#"{"message":"Internal server error"}"#,
    );

    let result = api.get_pet_by_id(1).await;
    assert!(result.is_err(), "expected error for server error response");
}

#[tokio::test]
async fn test_pet_api_download_binary_mock() {
    let (api, _) = new_pet_api_for_mock(200, "application/octet-stream", "FAKE_BINARY_DATA");

    // This exercises the HTTP plumbing for binary download endpoints.
    let _ = api.get_pet_avatar(1).await;
}
