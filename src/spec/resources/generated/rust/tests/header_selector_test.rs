use petstore::header_selector::HeaderSelector;

#[test]
fn test_header_selector_is_json_mime() {
    let hs = HeaderSelector::new();

    assert!(hs.is_json_mime("application/json"));
    assert!(hs.is_json_mime("application/json; charset=utf-8"));
    assert!(hs.is_json_mime("application/vnd.api+json"));
    assert!(!hs.is_json_mime("application/xml"));
    assert!(!hs.is_json_mime("text/plain"));
    assert!(!hs.is_json_mime(""));
    assert!(!hs.is_json_mime("application/octet-stream"));
    assert!(hs.is_json_mime("application/hal+json"));
}

#[test]
fn test_header_selector_select_headers_with_single_accept() {
    let hs = HeaderSelector::new();

    let headers = hs.select_headers(&["application/json"], "application/json", false);

    assert_eq!(headers.get("Accept").unwrap(), "application/json");
    assert_eq!(headers.get("Content-Type").unwrap(), "application/json");
}

#[test]
fn test_header_selector_select_headers_with_multiple_accepts() {
    let hs = HeaderSelector::new();

    let headers = hs.select_headers(
        &["application/json", "application/xml"],
        "application/json",
        false,
    );

    let accept = headers.get("Accept").unwrap();
    assert!(accept.contains("application/json"));
}

#[test]
fn test_header_selector_select_headers_with_empty_accepts() {
    let hs = HeaderSelector::new();

    let headers = hs.select_headers(&[], "application/json", false);

    assert!(!headers.contains_key("Accept"));
}

#[test]
fn test_header_selector_multipart_omits_content_type() {
    let hs = HeaderSelector::new();

    let headers = hs.select_headers(&["application/json"], "multipart/form-data", true);

    assert!(
        !headers.contains_key("Content-Type"),
        "expected no Content-Type header for multipart requests"
    );
}

#[test]
fn test_header_selector_default_content_type() {
    let hs = HeaderSelector::new();

    let headers = hs.select_headers(&["application/json"], "", false);

    assert_eq!(headers.get("Content-Type").unwrap(), "application/json");
}

#[test]
fn test_header_selector_quality_weighting() {
    let hs = HeaderSelector::new();

    let headers = hs.select_headers(
        &["application/json", "application/xml", "text/plain"],
        "application/json",
        false,
    );

    let accept = headers.get("Accept").unwrap();
    assert!(!accept.is_empty(), "expected non-empty Accept header");

    // application/json should appear first (highest priority)
    let json_idx = accept.find("application/json");
    let xml_idx = accept.find("application/xml");
    assert!(
        json_idx.is_some(),
        "expected application/json in Accept header"
    );
    assert!(
        xml_idx.is_some(),
        "expected application/xml in Accept header"
    );
    assert!(
        json_idx.unwrap() < xml_idx.unwrap(),
        "expected application/json to appear before application/xml"
    );
}

#[test]
fn test_header_selector_with_vendor_json() {
    let hs = HeaderSelector::new();

    let headers = hs.select_headers(
        &["application/vnd.api+json", "application/xml"],
        "application/json",
        false,
    );

    let accept = headers.get("Accept").unwrap();
    assert!(accept.contains("application/vnd.api+json"));
}
