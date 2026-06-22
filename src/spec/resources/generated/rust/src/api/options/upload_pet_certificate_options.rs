use crate::models::*;

/// Options for the upload_pet_certificate operation.
#[derive(Debug, Clone)]
pub struct UploadPetCertificateOptions {
    pub file: Vec<u8>,
}

impl UploadPetCertificateOptions {
    /// Creates a new UploadPetCertificateOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(file: Vec<u8>) -> Self {
        Self { file }
    }
}
