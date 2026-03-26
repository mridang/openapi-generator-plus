"""Tests for the DryFood model.

These tests verify that the generated DryFood Pydantic model enforces
required fields and supports JSON round-tripping.
"""

import json

import pytest
from pydantic import ValidationError

from petstore_client.models.dry_food import DryFood


class TestDryFoodModel:
    """Verify DryFood model validation and serialization."""

    def test_requires_food_type_field(self):
        """Deserializing a DryFood without 'foodType' should raise a validation error.

        The 'food_type' field is declared as `str` (not Optional), so
        omitting it must fail.
        """
        data = {
            'weightKg': 2.5,
        }
        with pytest.raises(ValidationError) as exc_info:
            DryFood.model_validate(data)

        errors = exc_info.value.errors()
        field_names = [e['loc'][0] for e in errors]
        assert 'foodType' in field_names or 'food_type' in field_names, (
            f'Expected validation error for "foodType", got errors for: {field_names}'
        )

    def test_requires_weight_kg_field(self):
        """Deserializing a DryFood without 'weightKg' should raise a validation error.

        The 'weight_kg' field is declared as `float` (not Optional), so
        omitting it must fail.
        """
        data = {
            'foodType': 'kibble',
        }
        with pytest.raises(ValidationError) as exc_info:
            DryFood.model_validate(data)

        errors = exc_info.value.errors()
        field_names = [e['loc'][0] for e in errors]
        assert 'weightKg' in field_names or 'weight_kg' in field_names, (
            f'Expected validation error for "weightKg", got errors for: {field_names}'
        )

    def test_serializes_to_json(self):
        """A DryFood should round-trip through JSON serialization preserving all fields."""
        dry_food = DryFood(foodType='kibble', weightKg=2.5)

        json_str = dry_food.model_dump_json(by_alias=True, exclude_none=True)
        data = json.loads(json_str)

        assert data['foodType'] == 'kibble'
        assert data['weightKg'] == 2.5

        # Round-trip back to a DryFood instance
        restored = DryFood.model_validate(data)
        assert restored.food_type == 'kibble'
        assert restored.weight_kg == 2.5
