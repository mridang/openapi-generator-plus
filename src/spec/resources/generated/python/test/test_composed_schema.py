# ruff: noqa
# mypy: ignore-errors
import pytest

from petstore_client.object_serializer import ObjectSerializer
from petstore_client.models.dry_food import DryFood
from petstore_client.models.wet_food import WetFood
from petstore_client.models.medication import Medication
from petstore_client.models.surgery import Surgery
from petstore_client.models.set_pet_avatar_thumbnail_request import (
    SetPetAvatarThumbnailRequest,
)


class TestOneOfPetFood:
    """oneOf with discriminator: PetFood"""

    def test_deserializes_dry_food(self) -> None:
        json_str = '{"foodType":"dry","weightKg":2.5}'
        result = ObjectSerializer().deserialize(json_str, "PetFood")
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, DryFood)

    def test_deserializes_wet_food(self) -> None:
        json_str = '{"foodType":"wet","volumeMl":400}'
        result = ObjectSerializer().deserialize(json_str, "PetFood")
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, WetFood)

    def test_missing_discriminator_raises(self) -> None:
        """Gap AU: missing discriminator field must raise, not silently wrap."""
        json_str = '{"weightKg":2.5}'
        with pytest.raises(Exception) as exc_info:
            ObjectSerializer().deserialize(json_str, "PetFood")
        assert "Missing discriminator" in str(exc_info.value)

    def test_empty_discriminator_raises(self) -> None:
        """Gap AU: empty discriminator value must raise."""
        json_str = '{"foodType":"","weightKg":2.5}'
        with pytest.raises(Exception) as exc_info:
            ObjectSerializer().deserialize(json_str, "PetFood")
        assert "Empty discriminator" in str(
            exc_info.value
        ) or "Unknown discriminator" in str(exc_info.value)

    def test_unknown_discriminator_raises(self) -> None:
        """Gap AU: unknown discriminator value must raise."""
        json_str = '{"foodType":"raw","calories":300}'
        with pytest.raises(Exception) as exc_info:
            ObjectSerializer().deserialize(json_str, "PetFood")
        assert "Unknown discriminator" in str(exc_info.value)

    def test_serializes_dry_food(self) -> None:
        json_str = '{"foodType":"dry","weightKg":2.5}'
        result = ObjectSerializer().deserialize(json_str, "PetFood")
        serialized = ObjectSerializer().serialize(result)
        assert "dry" in serialized
        assert "2.5" in serialized


class TestAnyOfPetTreatment:
    """anyOf without discriminator: PetTreatment"""

    def test_deserializes_medication(self) -> None:
        json_str = '{"drugName":"Amoxicillin","dosage":"500mg"}'
        result = ObjectSerializer().deserialize(json_str, "PetTreatment")
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, Medication)

    def test_deserializes_surgery(self) -> None:
        json_str = '{"procedureName":"Spay","durationMinutes":45}'
        result = ObjectSerializer().deserialize(json_str, "PetTreatment")
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, Surgery)

    def test_serialize_round_trip(self) -> None:
        json_str = '{"drugName":"Amoxicillin","dosage":"500mg"}'
        result = ObjectSerializer().deserialize(json_str, "PetTreatment")
        serialized = ObjectSerializer().serialize(result)
        assert serialized is not None
        assert len(serialized) > 0

    def test_no_matching_variant_raises(self) -> None:
        """oneof-nondiscriminator-no-match-silent: a payload matching neither
        Medication nor Surgery must raise rather than yield a silently-empty
        union.
        """
        json_str = '{"unrelatedKey":"value","anotherUnknown":123}'
        with pytest.raises(Exception):
            ObjectSerializer().deserialize(json_str, "PetTreatment")


class TestOneOfNonDiscriminatorValidator:
    """oneof-multiple-match: the non-discriminator oneOf validator now uses
    FIRST-match semantics -- it returns the first variant the payload
    validates against rather than counting matches and raising 'Multiple
    matches found' on more than one. This aligns Python with the other 11
    SDKs. SetPetAvatarThumbnailRequest is the spec's non-discriminator oneOf
    (bytes | List[bytes]); these tests cover the first-match-returns and
    no-match-still-raises branches of the rewritten validator.
    """

    def test_first_variant_match_returns_value(self) -> None:
        req = SetPetAvatarThumbnailRequest(actual_instance=b"avatar-bytes")
        assert req.actual_instance == b"avatar-bytes"

    def test_second_variant_match_returns_value(self) -> None:
        req = SetPetAvatarThumbnailRequest(actual_instance=[b"a", b"b"])
        assert req.actual_instance == [b"a", b"b"]

    def test_no_matching_variant_still_raises(self) -> None:
        """The no-match error path is preserved by the first-match rewrite."""
        with pytest.raises(Exception):
            SetPetAvatarThumbnailRequest(actual_instance=object())


class TestAllOfPetWithOwner:
    """allOf: PetWithOwner"""

    def test_deserializes_all_properties(self) -> None:
        json_str = '{"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John","ownerEmail":"john@example.com"}'
        result = ObjectSerializer().deserialize(json_str, "PetWithOwner")
        assert result is not None
        assert result.name == "doggie"
        assert result.owner_name == "John"
        assert result.owner_email == "john@example.com"

    def test_serializes_to_json(self) -> None:
        json_str = '{"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"}'
        result = ObjectSerializer().deserialize(json_str, "PetWithOwner")
        serialized = ObjectSerializer().serialize(result)
        assert "Fido" in serialized
        assert "John Doe" in serialized

    def test_round_trip_preserves_fields(self) -> None:
        json_str = '{"name":"Buddy","photoUrls":["http://example.com/buddy.jpg"],"ownerName":"Jane Smith"}'
        original = ObjectSerializer().deserialize(json_str, "PetWithOwner")
        serialized = ObjectSerializer().serialize(original)
        restored = ObjectSerializer().deserialize(serialized, "PetWithOwner")
        assert original is not None
        assert restored is not None
        assert restored.name == original.name
        assert restored.owner_name == original.owner_name
