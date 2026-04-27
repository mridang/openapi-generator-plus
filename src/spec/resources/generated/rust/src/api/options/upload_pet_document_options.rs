#[allow(unused_imports)]
use crate::models::*;

/// Options for the upload_pet_document operation.
#[derive(Debug, Clone, Default)]
pub struct UploadPetDocumentOptions {
    pub file: Vec<u8>,
    pub document_type: Option<String>,
    pub notes: Option<String>,
}

impl UploadPetDocumentOptions {
    /// Creates a new UploadPetDocumentOptions with default values.
    pub fn new() -> Self {
        Self::default()
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
}
