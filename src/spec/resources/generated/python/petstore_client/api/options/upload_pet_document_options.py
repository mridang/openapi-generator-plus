from dataclasses import dataclass
from typing import Optional


@dataclass
class UploadPetDocumentOptions:
    """Options for the upload_pet_document operation."""

    file: bytes
    document_type: Optional[str] = None
    notes: Optional[str] = None
