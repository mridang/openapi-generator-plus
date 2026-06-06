use crate::models::*;

/// Options for the add_pet_photos operation.
#[derive(Debug, Clone, Default)]
pub struct AddPetPhotosOptions {
    pub files: Vec<Vec<u8>>,
    pub metadata: PhotoMetadata,
}

impl AddPetPhotosOptions {
    /// Creates a new AddPetPhotosOptions with default values.
    pub fn new() -> Self {
        Self::default()
    }
}
