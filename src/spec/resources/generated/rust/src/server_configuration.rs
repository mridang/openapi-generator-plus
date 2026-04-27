use std::collections::HashMap;

/// ServerVariable represents a server variable from the OpenAPI specification.
///
/// Server variables define substitution parameters in server URL templates.
/// Each variable has a default value and may optionally restrict values to
/// an enumerated set.
#[derive(Debug, Clone)]
pub struct ServerVariable {
    /// The default value for this variable.
    pub default_value: String,

    /// A human-readable description of this variable.
    pub description: String,

    /// The allowed values for this variable. An empty vec means any value
    /// is accepted.
    pub enum_values: Vec<String>,
}

/// ServerConfiguration represents a single server entry from the OpenAPI specification.
///
/// A server URL may contain template variables (e.g.
/// `https://{env}.api.example.com/v{version}`). Use `url` to resolve the URL
/// with default variable values, or `url` with overrides to substitute specific
/// variables.
#[derive(Debug, Clone)]
pub struct ServerConfiguration {
    /// The raw URL template before variable substitution.
    pub url_template: String,

    /// A human-readable description of this server.
    pub description: String,

    /// The server variables and their definitions.
    pub variables: HashMap<String, ServerVariable>,
}

impl ServerConfiguration {
    /// Resolves the URL template using default variable values or the given overrides.
    ///
    /// Variables not present in overrides use their default values. If a variable
    /// has an enum constraint, the override value is validated against the allowed values.
    ///
    /// # Panics
    ///
    /// Panics if an override value is not in the variable's enum constraint.
    pub fn url(&self, overrides: &HashMap<String, String>) -> String {
        let mut result = self.url_template.clone();
        for (var_name, variable) in &self.variables {
            let value = overrides
                .get(var_name)
                .unwrap_or(&variable.default_value)
                .clone();

            if !variable.enum_values.is_empty() && !variable.enum_values.contains(&value) {
                panic!(
                    "invalid value '{}' for variable '{}'; allowed: {:?}",
                    value, var_name, variable.enum_values
                );
            }

            result = result.replace(&format!("{{{}}}", var_name), &value);
        }
        result
    }
}
