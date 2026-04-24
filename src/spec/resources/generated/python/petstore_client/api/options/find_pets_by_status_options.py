from dataclasses import dataclass
from typing import Optional, Dict


@dataclass
class FindPetsByStatusOptions:
    """Options for the find_pets_by_status operation."""

    status: Optional[str] = None
    filter: Optional[Dict[str, str]] = None
