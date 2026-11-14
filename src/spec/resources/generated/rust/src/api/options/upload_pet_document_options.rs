use crate::models::*;

/// Options for the upload_pet_document operation.
#[derive(Debug, Clone)]
pub struct UploadPetDocumentOptions {
    pub file: Vec<u8>,
    pub document_type: Option<String>,
    pub notes: Option<String>,
    pub is_primary: Option<bool>,
    pub page_count: Option<i64>,
    pub taken_at: Option<crate::date_time::DateTime>,
    pub swatch: Option<Swatch>,
}

impl UploadPetDocumentOptions {
    /// Creates a new UploadPetDocumentOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(file: Vec<u8>) -> Self {
        Self {
            file,
            document_type: None,
            notes: None,
            is_primary: None,
            page_count: None,
            taken_at: None,
            swatch: None,
        }
    }

    /// Sets the document_type field.
    pub fn document_type(mut self, document_type: String) -> Self {
        self.document_type = Some(document_type);
        self
    }

    /// Sets the notes field.
    pub fn notes(mut self, notes: String) -> Self {
        self.notes = Some(notes);
        self
    }

    /// Sets the is_primary field.
    pub fn is_primary(mut self, is_primary: bool) -> Self {
        self.is_primary = Some(is_primary);
        self
    }

    /// Sets the page_count field.
    pub fn page_count(mut self, page_count: i64) -> Self {
        self.page_count = Some(page_count);
        self
    }

    /// Sets the taken_at field.
    pub fn taken_at(mut self, taken_at: crate::date_time::DateTime) -> Self {
        self.taken_at = Some(taken_at);
        self
    }

    /// Sets the swatch field.
    pub fn swatch(mut self, swatch: Swatch) -> Self {
        self.swatch = Some(swatch);
        self
    }
}
