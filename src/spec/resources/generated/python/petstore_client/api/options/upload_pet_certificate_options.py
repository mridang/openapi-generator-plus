from dataclasses import dataclass


@dataclass
class UploadPetCertificateOptions:
    """Options for the upload_pet_certificate operation."""

    file: bytes
