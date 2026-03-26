"""Tests for the Pet model.

These tests verify that the generated Pet Pydantic model enforces required
fields, validates enum values, and supports JSON round-tripping.
"""

import json

import pytest
from pydantic import ValidationError

from petstore_client.models.pet import Pet


class TestPetModel:
    """Verify Pet model validation and serialization."""

    def test_requires_name_field(self):
        """Deserializing a Pet without 'name' should raise a validation error.

        The 'name' field is declared as `str` (not Optional), so omitting it
        must fail.
        """
        data = {
            'photoUrls': ['https://example.com/photo.jpg'],
        }
        with pytest.raises(ValidationError) as exc_info:
            Pet.model_validate(data)

        # The error should reference the missing 'name' field
        errors = exc_info.value.errors()
        field_names = [e['loc'][0] for e in errors]
        assert 'name' in field_names, (
            f'Expected validation error for "name", got errors for: {field_names}'
        )

    def test_requires_photo_urls_field(self):
        """Deserializing a Pet without 'photoUrls' should raise a validation error.

        The 'photo_urls' field is declared as `Set[str]` (not Optional), so
        omitting it must fail.
        """
        data = {
            'name': 'Buddy',
        }
        with pytest.raises(ValidationError) as exc_info:
            Pet.model_validate(data)

        errors = exc_info.value.errors()
        field_names = [e['loc'][0] for e in errors]
        assert 'photoUrls' in field_names or 'photo_urls' in field_names, (
            f'Expected validation error for "photoUrls", got errors for: {field_names}'
        )

    def test_rejects_invalid_status_enum(self):
        """An invalid status enum value should be rejected by the validator.

        Valid values are: 'available', 'pending', 'sold'.
        """
        data = {
            'name': 'Buddy',
            'photoUrls': ['https://example.com/photo.jpg'],
            'status': 'flying',
        }
        with pytest.raises(ValidationError) as exc_info:
            Pet.model_validate(data)

        errors = exc_info.value.errors()
        field_names = [e['loc'][0] for e in errors]
        assert 'status' in field_names, (
            f'Expected validation error for "status", got errors for: {field_names}'
        )

    def test_serializes_to_json(self):
        """A Pet should round-trip through JSON serialization preserving all fields."""
        pet = Pet(
            id=10,
            name='Buddy',
            photoUrls={'https://example.com/photo.jpg'},
            status='available',
        )

        json_str = pet.model_dump_json(by_alias=True, exclude_none=True)
        data = json.loads(json_str)

        assert data['name'] == 'Buddy'
        assert data['id'] == 10
        assert 'https://example.com/photo.jpg' in data['photoUrls']
        assert data['status'] == 'available'

        # Round-trip back to a Pet instance
        restored = Pet.model_validate(data)
        assert restored.name == 'Buddy'
        assert restored.id == 10
        assert restored.status == 'available'
