use std::collections::HashMap;

use crate::object_serializer::stringify;

/// Represents a serialized parameter value that may be a single value or
/// multiple values (for exploded form-style query parameters).
#[derive(Debug, Clone)]
pub enum SerializedValue {
    /// A single serialized string value.
    Single(String),
    /// Multiple values for the same key (form style with explode=true).
    Multi(Vec<String>),
}

/// Serializes a parameter value for HTTP requests based on its location.
///
/// # Arguments
///
/// * `value` - The value to serialize, or None
/// * `location` - "path", "query", "header", or "cookie"
/// * `schema_type` - The schema type (e.g. "string", "array")
/// * `collection_format` - Legacy collection format (e.g. "csv", "ssv", "tsv", "pipes", "multi")
pub fn serialize_value(
    value: Option<&str>,
    location: &str,
    _schema_type: &str,
    _collection_format: &str,
) -> Option<String> {
    match value {
        None => serialize_nil(location),
        Some(val) => {
            if location == "path" {
                Some(urlencoding::encode(val).into_owned())
            } else {
                Some(val.to_string())
            }
        }
    }
}

/// Serializes an array value for HTTP requests based on its location.
pub fn serialize_array_value(
    items: &[String],
    location: &str,
    collection_format: &str,
) -> Option<SerializedValue> {
    if location == "query" {
        Some(serialize_query_array(items, collection_format))
    } else {
        Some(SerializedValue::Single(items.join(",")))
    }
}

/// Serializes a deepObject-style query parameter.
///
/// Produces a map of flattened keys in the form `param_name[key]` to stringified values.
pub fn serialize_deep_object(
    param_name: &str,
    value: &HashMap<String, String>,
) -> Vec<(String, String)> {
    let mut result = Vec::new();
    for (key, val) in value {
        result.push((format!("{}[{}]", param_name, key), val.clone()));
    }
    result
}

/// Serializes a parameter value according to OAS 3.0 style and explode rules.
///
/// # Arguments
///
/// * `param_name` - The parameter name
/// * `value` - The value to serialize, or None
/// * `location` - "path", "query", "header", "cookie"
/// * `schema_type` - The schema type
/// * `collection_format` - Legacy collection format
/// * `style` - OAS 3.0 style (e.g. "matrix", "label", "form", "simple", "spaceDelimited", "pipeDelimited")
/// * `explode` - Whether to explode array values
pub fn serialize_styled(
    param_name: &str,
    value: Option<&str>,
    items: Option<&[String]>,
    location: &str,
    _schema_type: &str,
    _collection_format: &str,
    style: &str,
    explode: bool,
) -> Option<SerializedValue> {
    if style.is_empty() {
        return match value {
            Some(val) => {
                if location == "path" {
                    Some(SerializedValue::Single(urlencoding::encode(val).into_owned()))
                } else {
                    Some(SerializedValue::Single(val.to_string()))
                }
            }
            None => serialize_nil(location).map(SerializedValue::Single),
        };
    }

    let is_array = items.is_some();

    match style {
        "matrix" => {
            if value.is_none() && !is_array {
                return if location == "query" { None } else { Some(SerializedValue::Single(String::new())) };
            }
            if let Some(arr) = items {
                if explode {
                    let parts: Vec<String> = arr
                        .iter()
                        .map(|v| format!(";{}={}", param_name, v))
                        .collect();
                    Some(SerializedValue::Single(parts.join("")))
                } else {
                    Some(SerializedValue::Single(format!(";{}={}", param_name, arr.join(","))))
                }
            } else {
                Some(SerializedValue::Single(format!(";{}={}", param_name, value.unwrap_or(""))))
            }
        }
        "label" => {
            if value.is_none() && !is_array {
                return if location == "query" { None } else { Some(SerializedValue::Single(String::new())) };
            }
            if let Some(arr) = items {
                if explode {
                    Some(SerializedValue::Single(format!(".{}", arr.join("."))))
                } else {
                    Some(SerializedValue::Single(format!(".{}", arr.join(","))))
                }
            } else {
                Some(SerializedValue::Single(format!(".{}", value.unwrap_or(""))))
            }
        }
        "spaceDelimited" => {
            if value.is_none() && !is_array {
                return if location == "query" { None } else { Some(SerializedValue::Single(String::new())) };
            }
            if let Some(arr) = items {
                Some(SerializedValue::Single(arr.join(" ")))
            } else {
                Some(SerializedValue::Single(value.unwrap_or("").to_string()))
            }
        }
        "pipeDelimited" => {
            if value.is_none() && !is_array {
                return if location == "query" { None } else { Some(SerializedValue::Single(String::new())) };
            }
            if let Some(arr) = items {
                Some(SerializedValue::Single(arr.join("|")))
            } else {
                Some(SerializedValue::Single(value.unwrap_or("").to_string()))
            }
        }
        "form" => {
            if value.is_none() && !is_array {
                return if location == "query" { None } else { Some(SerializedValue::Single(String::new())) };
            }
            if let Some(arr) = items {
                if explode {
                    Some(SerializedValue::Multi(arr.to_vec()))
                } else {
                    Some(SerializedValue::Single(arr.join(",")))
                }
            } else {
                Some(SerializedValue::Single(value.unwrap_or("").to_string()))
            }
        }
        "simple" => {
            if value.is_none() && !is_array {
                return if location == "query" { None } else { Some(SerializedValue::Single(String::new())) };
            }
            if let Some(arr) = items {
                Some(SerializedValue::Single(arr.join(",")))
            } else {
                Some(SerializedValue::Single(value.unwrap_or("").to_string()))
            }
        }
        _ => match value {
            Some(val) => {
                if location == "path" {
                    Some(SerializedValue::Single(urlencoding::encode(val).into_owned()))
                } else {
                    Some(SerializedValue::Single(val.to_string()))
                }
            }
            None => serialize_nil(location).map(SerializedValue::Single),
        },
    }
}

fn serialize_nil(location: &str) -> Option<String> {
    if location == "query" {
        None
    } else {
        Some(String::new())
    }
}

fn serialize_query_array(items: &[String], collection_format: &str) -> SerializedValue {
    match collection_format {
        "multi" => SerializedValue::Multi(items.to_vec()),
        "ssv" => SerializedValue::Single(items.join(" ")),
        "tsv" => SerializedValue::Single(items.join("\t")),
        "pipes" => SerializedValue::Single(items.join("|")),
        _ => SerializedValue::Single(items.join(",")),
    }
}
