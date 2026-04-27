use std::collections::HashMap;
use std::io::Read;
use std::net::TcpListener;
use std::thread;

use petstore::*;

/// Starts a minimal HTTP server that captures the request and responds with the
/// given status code and body. Returns the base URL.
fn start_server(status: u16, content_type: &str, body: &str) -> String {
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
    base_url
}

#[tokio::test]
async fn test_default_api_client_get() {
    let base_url = start_server(200, "application/json", r#"{"message":"success"}"#);

    let client = DefaultApiClient::new(None);
    let headers = HashMap::new();
    let resp = client
        .send_request("GET", &format!("{}/test", base_url), &headers, None)
        .await
        .expect("unexpected error");

    assert_eq!(resp.status_code, 200);
    assert!(resp.body.contains("success"));
}

#[tokio::test]
async fn test_default_api_client_post_with_json_body() {
    let base_url = start_server(201, "application/json", r#"{"id":1}"#);

    let client = DefaultApiClient::new(None);
    let body = br#"{"name":"test"}"#;
    let mut headers = HashMap::new();
    headers.insert("Content-Type".to_string(), "application/json".to_string());

    let resp = client
        .send_request("POST", &format!("{}/items", base_url), &headers, Some(body))
        .await
        .expect("unexpected error");

    assert_eq!(resp.status_code, 201);
}

#[tokio::test]
async fn test_default_api_client_response_headers() {
    let base_url = start_server(200, "application/json", "{}");

    let client = DefaultApiClient::new(None);
    let headers = HashMap::new();
    let resp = client
        .send_request("GET", &format!("{}/test", base_url), &headers, None)
        .await
        .expect("unexpected error");

    assert_eq!(resp.status_code, 200);
    // Response headers should be populated
    assert!(!resp.headers.is_empty());
}

#[tokio::test]
async fn test_default_api_client_non_2xx_status() {
    let base_url = start_server(500, "application/json", r#"{"error":"internal"}"#);

    let client = DefaultApiClient::new(None);
    let headers = HashMap::new();
    let resp = client
        .send_request("GET", &format!("{}/fail", base_url), &headers, None)
        .await
        .expect("unexpected error");

    // DefaultApiClient returns the response as-is; error dispatch is done by BaseApi
    assert_eq!(resp.status_code, 500);
}

#[tokio::test]
async fn test_default_api_client_put() {
    let base_url = start_server(200, "application/json", "{}");

    let client = DefaultApiClient::new(None);
    let body = br#"{"name":"updated"}"#;
    let mut headers = HashMap::new();
    headers.insert("Content-Type".to_string(), "application/json".to_string());

    let resp = client
        .send_request(
            "PUT",
            &format!("{}/items/1", base_url),
            &headers,
            Some(body),
        )
        .await
        .expect("unexpected error");

    assert_eq!(resp.status_code, 200);
}

#[tokio::test]
async fn test_default_api_client_delete() {
    let base_url = start_server(204, "application/json", "");

    let client = DefaultApiClient::new(None);
    let headers = HashMap::new();

    let resp = client
        .send_request("DELETE", &format!("{}/items/1", base_url), &headers, None)
        .await
        .expect("unexpected error");

    assert_eq!(resp.status_code, 204);
}
