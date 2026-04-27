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
fn store_prism_base_url() -> Option<String> {
    std::env::var("API_BASE_URL").ok()
}

fn new_store_api_for_integration() -> Option<StoreApi> {
    let base_url = store_prism_base_url()?;
    let config = ConfigurationBuilder::new()
        .base_url(&base_url)
        .default_header("Authorization", "Bearer test-token")
        .build();
    let client = DefaultApiClient::new(None);
    Some(StoreApi::new(Arc::new(client), config))
}

/// Starts a minimal HTTP mock server and returns a StoreApi configured against it.
fn new_store_api_for_mock(status: u16, content_type: &str, body: &str) -> StoreApi {
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
    StoreApi::new(Arc::new(client), config)
}

#[tokio::test]
async fn test_store_api_get_inventory() {
    if let Some(api) = new_store_api_for_integration() {
        let result = api.get_inventory().await;
        assert!(result.is_ok(), "GetInventory failed: {:?}", result.err());
    }
}

#[tokio::test]
async fn test_store_api_get_order_by_id() {
    if let Some(api) = new_store_api_for_integration() {
        let result = api.get_order_by_id(1).await;
        assert!(result.is_ok(), "GetOrderById failed: {:?}", result.err());
    }
}

#[tokio::test]
async fn test_store_api_place_order() {
    if let Some(api) = new_store_api_for_integration() {
        let order = Order::new();
        let result = api.place_order(Some(order)).await;
        assert!(result.is_ok(), "PlaceOrder failed: {:?}", result.err());
    }
}

#[tokio::test]
async fn test_store_api_delete_order() {
    if let Some(api) = new_store_api_for_integration() {
        let result = api.delete_order(1).await;
        assert!(result.is_ok(), "DeleteOrder failed: {:?}", result.err());
    }
}

#[tokio::test]
async fn test_store_api_get_order_not_found() {
    let api = new_store_api_for_mock(404, "application/json", r#"{"message":"Order not found"}"#);

    let result = api.get_order_by_id(99999).await;
    assert!(result.is_err(), "expected error for non-existent order");
}

#[tokio::test]
async fn test_store_api_place_order_server_error() {
    let api = new_store_api_for_mock(
        500,
        "application/json",
        r#"{"message":"Internal server error"}"#,
    );

    let order = Order::new();
    let result = api.place_order(Some(order)).await;
    assert!(result.is_err(), "expected error for server error response");
}

#[tokio::test]
async fn test_store_api_delete_order_not_found() {
    let api = new_store_api_for_mock(404, "application/json", r#"{"message":"Order not found"}"#);

    let result = api.delete_order(99999).await;
    assert!(
        result.is_err(),
        "expected error for deleting non-existent order"
    );
}
