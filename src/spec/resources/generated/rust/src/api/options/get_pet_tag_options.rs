use crate::models::*;

/// Options for the get_pet_tag operation.
#[derive(Debug, Clone, Default)]
pub struct GetPetTagOptions {
    pub colors: Option<Vec<String>>,
    pub sizes: Option<Vec<String>>,
    pub filter: Option<String>,
    /// Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
    pub revision: Option<String>,
    /// Object-valued query parameter, form style exploded (the OAS default for query)
    pub criteria: Option<PetCriteria>,
    pub criteria_csv: Option<PetCriteria>,
    pub criteria_deep: Option<PetCriteria>,
    /// Opaque bytes, base64 on the wire (OAS format byte)
    pub fingerprint: Option<Vec<u8>>,
    pub x_criteria: Option<PetCriteria>,
    pub x_fingerprint: Option<Vec<u8>>,
}

impl GetPetTagOptions {
    /// Creates a new GetPetTagOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }

    /// Sets the colors field.
    pub fn colors(mut self, colors: Vec<String>) -> Self {
        self.colors = Some(colors);
        self
    }

    /// Sets the sizes field.
    pub fn sizes(mut self, sizes: Vec<String>) -> Self {
        self.sizes = Some(sizes);
        self
    }

    /// Sets the filter field.
    pub fn filter(mut self, filter: String) -> Self {
        self.filter = Some(filter);
        self
    }

    /// Sets the revision field.
    pub fn revision(mut self, revision: String) -> Self {
        self.revision = Some(revision);
        self
    }

    /// Sets the criteria field.
    pub fn criteria(mut self, criteria: PetCriteria) -> Self {
        self.criteria = Some(criteria);
        self
    }

    /// Sets the criteria_csv field.
    pub fn criteria_csv(mut self, criteria_csv: PetCriteria) -> Self {
        self.criteria_csv = Some(criteria_csv);
        self
    }

    /// Sets the criteria_deep field.
    pub fn criteria_deep(mut self, criteria_deep: PetCriteria) -> Self {
        self.criteria_deep = Some(criteria_deep);
        self
    }

    /// Sets the fingerprint field.
    pub fn fingerprint(mut self, fingerprint: Vec<u8>) -> Self {
        self.fingerprint = Some(fingerprint);
        self
    }

    /// Sets the x_criteria field.
    pub fn x_criteria(mut self, x_criteria: PetCriteria) -> Self {
        self.x_criteria = Some(x_criteria);
        self
    }

    /// Sets the x_fingerprint field.
    pub fn x_fingerprint(mut self, x_fingerprint: Vec<u8>) -> Self {
        self.x_fingerprint = Some(x_fingerprint);
        self
    }
}
