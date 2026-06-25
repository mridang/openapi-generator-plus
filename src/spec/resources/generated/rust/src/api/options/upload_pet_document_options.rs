use crate::models::*;

/// Options for the upload_pet_document operation.
#[derive(Debug, Clone)]
pub struct UploadPetDocumentOptions {
    pub file: Vec<u8>,
    pub document_type: Option<String>,
    pub notes: Option<String>,
}

impl UploadPetDocumentOptions {
    /// Creates a new UploadPetDocumentOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(file: Vec<u8>, ) -> Self {
        Self {
            file,
            document_type: None,
            notes: None,
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
}
