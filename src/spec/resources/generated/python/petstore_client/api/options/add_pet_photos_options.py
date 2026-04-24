from dataclasses import dataclass
from petstore_client.models.photo_metadata import PhotoMetadata
from typing import List


@dataclass
class AddPetPhotosOptions:
    """Options for the add_pet_photos operation."""

    files: List[bytes]
    metadata: PhotoMetadata
