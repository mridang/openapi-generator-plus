from dataclasses import dataclass
from typing import Optional, List


@dataclass
class GetPetTagOptions:
    """Options for the get_pet_tag operation."""

    colors: Optional[List[str]] = None
    sizes: Optional[List[str]] = None
    filter: Optional[str] = None
