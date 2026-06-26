use crate::models::*;

/// Options for the upload_pet_certificate operation.
#[derive(Debug, Clone)]
pub struct UploadPetCertificateOptions {
    pub file: Vec<u8>,
}

impl UploadPetCertificateOptions {
    /// Creates a new UploadPetCertificateOptions, requiring every required parameter up front.
    ///
    /// Every field of this struct is required and is taken as a constructor
    /// argument so it cannot be silently omitted. This struct has no optional
    /// fields and therefore no chained setters.
    pub fn new(file: Vec<u8>) -> Self {
        Self { file }
    }
}
