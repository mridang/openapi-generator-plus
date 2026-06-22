use crate::models::*;

/// Options for the add_pet_photos operation.
#[derive(Debug, Clone)]
pub struct AddPetPhotosOptions {
    pub files: Vec<Vec<u8>>,
    pub metadata: PhotoMetadata,
}

impl AddPetPhotosOptions {
    /// Creates a new AddPetPhotosOptions, requiring every required parameter up front.
    ///
    /// Required fields are taken as constructor arguments so they cannot be
    /// silently omitted; optional fields start as `None` and are populated via
    /// the chained setters below.
    pub fn new(files: Vec<Vec<u8>>, metadata: PhotoMetadata) -> Self {
        Self { files, metadata }
    }
}
