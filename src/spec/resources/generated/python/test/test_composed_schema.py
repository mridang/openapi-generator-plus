from petstore_client.object_serializer import ObjectSerializer
from petstore_client.models.dry_food import DryFood
from petstore_client.models.wet_food import WetFood
from petstore_client.models.medication import Medication
from petstore_client.models.surgery import Surgery


class TestOneOfPetFood:
    """oneOf with discriminator: PetFood"""

    def test_deserializes_dry_food(self) -> None:
        json_str = '{"foodType":"dry","weightKg":2.5}'
        result = ObjectSerializer().deserialize(json_str, 'PetFood')
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, DryFood)

    def test_deserializes_wet_food(self) -> None:
        json_str = '{"foodType":"wet","volumeMl":400}'
        result = ObjectSerializer().deserialize(json_str, 'PetFood')
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, WetFood)

    def test_unknown_discriminator_returns_none(self) -> None:
        json_str = '{"foodType":"raw","calories":300}'
        result = ObjectSerializer().deserialize(json_str, 'PetFood')
        assert result is None

    def test_serializes_dry_food(self) -> None:
        json_str = '{"foodType":"dry","weightKg":2.5}'
        result = ObjectSerializer().deserialize(json_str, 'PetFood')
        serialized = ObjectSerializer().serialize(result)
        assert 'dry' in serialized
        assert '2.5' in serialized


class TestAnyOfPetTreatment:
    """anyOf without discriminator: PetTreatment"""

    def test_deserializes_medication(self) -> None:
        json_str = '{"drugName":"Amoxicillin","dosage":"500mg"}'
        result = ObjectSerializer().deserialize(json_str, 'PetTreatment')
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, Medication)

    def test_deserializes_surgery(self) -> None:
        json_str = '{"procedureName":"Spay","durationMinutes":45}'
        result = ObjectSerializer().deserialize(json_str, 'PetTreatment')
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, Surgery)

    def test_serialize_round_trip(self) -> None:
        json_str = '{"drugName":"Amoxicillin","dosage":"500mg"}'
        result = ObjectSerializer().deserialize(json_str, 'PetTreatment')
        serialized = ObjectSerializer().serialize(result)
        assert serialized is not None
        assert len(serialized) > 0


class TestAllOfPetWithOwner:
    """allOf: PetWithOwner"""

    def test_deserializes_all_properties(self) -> None:
        json_str = '{"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John","ownerEmail":"john@example.com"}'
        result = ObjectSerializer().deserialize(json_str, 'PetWithOwner')
        assert result is not None
        assert result.name == 'doggie'
        assert result.owner_name == 'John'
        assert result.owner_email == 'john@example.com'

    def test_serializes_to_json(self) -> None:
        json_str = '{"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"}'
        result = ObjectSerializer().deserialize(json_str, 'PetWithOwner')
        serialized = ObjectSerializer().serialize(result)
        assert 'Fido' in serialized
        assert 'John Doe' in serialized

    def test_round_trip_preserves_fields(self) -> None:
        json_str = '{"name":"Buddy","photoUrls":["http://example.com/buddy.jpg"],"ownerName":"Jane Smith"}'
        original = ObjectSerializer().deserialize(json_str, 'PetWithOwner')
        serialized = ObjectSerializer().serialize(original)
        restored = ObjectSerializer().deserialize(serialized, 'PetWithOwner')
        assert original is not None
        assert restored is not None
        assert restored.name == original.name
        assert restored.owner_name == original.owner_name
