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
    /// Every field of this struct is required and is taken as a constructor
    /// argument so it cannot be silently omitted. This struct has no optional
    /// fields and therefore no chained setters.
    pub fn new(files: Vec<Vec<u8>>, metadata: PhotoMetadata, ) -> Self {
        Self {
            files,
            metadata,
        }
    }
}
