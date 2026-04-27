#[allow(unused_imports)]
use crate::models::*;

/// Options for the upload_pet_certificate operation.
#[derive(Debug, Clone, Default)]
pub struct UploadPetCertificateOptions {
    pub file: Vec<u8>,
}

impl UploadPetCertificateOptions {
    /// Creates a new UploadPetCertificateOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }
}
