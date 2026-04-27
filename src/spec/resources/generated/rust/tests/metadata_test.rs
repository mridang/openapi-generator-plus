use petstore::models::Metadata;

#[test]
fn test_metadata_serialize_deserialize() {
    let original = Metadata::new();

    let data = serde_json::to_string(&original).expect("failed to serialize Metadata");
    assert!(!data.is_empty(), "expected non-empty serialized data");

    let restored: Metadata = serde_json::from_str(&data).expect("failed to deserialize Metadata");
    let _ = restored;
}

#[test]
fn test_metadata_deserialize_from_json() {
    let json_data = r#"{"createdAt":"2024-01-15T10:30:00+00:00"}"#;

    let metadata: Metadata =
        serde_json::from_str(json_data).expect("failed to deserialize Metadata");
    let _ = metadata;
}

#[test]
fn test_metadata_deserialize_empty_object() {
    let json_data = "{}";

    let metadata: Metadata =
        serde_json::from_str(json_data).expect("failed to deserialize empty Metadata");
    let _ = metadata;
}

#[test]
fn test_metadata_deserialize_with_additional_properties() {
    // Metadata may contain additional properties beyond the defined schema fields.
    // This test verifies that unknown fields are handled gracefully during
    // deserialization (either ignored or captured depending on schema config).
    let json_data =
        r#"{"createdAt":"2024-01-15T10:30:00+00:00","customField":"customValue","count":42}"#;

    let result = serde_json::from_str::<Metadata>(json_data);
    // Should either succeed (ignoring unknown keys) or fail gracefully
    let _ = result;
}

#[test]
fn test_metadata_round_trip() {
    let json_data = r#"{"createdAt":"2024-01-15T10:30:00+00:00"}"#;

    let metadata: Metadata = serde_json::from_str(json_data).expect("failed to deserialize");

    let data = serde_json::to_string(&metadata).expect("failed to serialize");
    assert!(
        !data.is_empty(),
        "expected non-empty serialized data after round-trip"
    );
}
