import pytest
from pydantic import ValidationError

from petstore_client.errors import SerializationException
from petstore_client.object_serializer import ObjectSerializer
from petstore_client.models.dry_food import DryFood
from petstore_client.models.pet_food import PetFood
from petstore_client.models.wet_food import WetFood
from petstore_client.models.medication import Medication
from petstore_client.models.surgery import Surgery
from petstore_client.models.pet_treatment import PetTreatment
from petstore_client.models.set_pet_avatar_thumbnail_request import (
    SetPetAvatarThumbnailRequest,
)
from petstore_client.models.reserve_stock_response import ReserveStockResponse


class TestOneOfPetFood:
    """oneOf with discriminator: PetFood"""

    def test_bhv_unions_012_deserializes_dry_food(self) -> None:
        json_str = '{"foodType":"dry","weightKg":2.5}'
        result = ObjectSerializer().deserialize(json_str, "PetFood")
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, DryFood)

    def test_bhv_unions_013_deserializes_wet_food(self) -> None:
        json_str = '{"foodType":"wet","volumeMl":400}'
        result = ObjectSerializer().deserialize(json_str, "PetFood")
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, WetFood)

    def test_bhv_unions_015_missing_discriminator_raises(self) -> None:
        """Gap AU: missing discriminator field must raise, not silently wrap."""
        json_str = '{"weightKg":2.5}'
        with pytest.raises(SerializationException) as exc_info:
            ObjectSerializer().deserialize(json_str, "PetFood")
        assert "Missing discriminator" in str(exc_info.value)

    def test_bhv_unions_024_empty_discriminator_raises(self) -> None:
        """Gap AU: empty discriminator value must raise."""
        json_str = '{"foodType":"","weightKg":2.5}'
        with pytest.raises(SerializationException) as exc_info:
            ObjectSerializer().deserialize(json_str, "PetFood")
        assert "Empty discriminator" in str(
            exc_info.value
        ) or "Unknown discriminator" in str(exc_info.value)

    def test_bhv_unions_016_unknown_discriminator_raises(self) -> None:
        """Gap AU: unknown discriminator value must raise."""
        json_str = '{"foodType":"raw","calories":300}'
        with pytest.raises(SerializationException) as exc_info:
            ObjectSerializer().deserialize(json_str, "PetFood")
        assert "Unknown discriminator" in str(exc_info.value)

    def test_bhv_unions_026_unknown_discriminator_error_names_the_offending_value(
        self,
    ) -> None:
        json_str = '{"foodType":"raw","calories":300}'
        with pytest.raises(SerializationException) as exc_info:
            ObjectSerializer().deserialize(json_str, "PetFood")
        assert "'raw'" in str(exc_info.value)

    def test_bhv_unions_020_serializes_dry_food(self) -> None:
        json_str = '{"foodType":"dry","weightKg":2.5}'
        result = ObjectSerializer().deserialize(json_str, "PetFood")
        serialized = ObjectSerializer().serialize(result)
        assert "dry" in serialized
        assert "2.5" in serialized

    def test_bhv_unions_029_oneof_wrappers_have_value_equality(self) -> None:
        first = PetFood(DryFood(weightKg=2.5))
        second = PetFood(DryFood(weightKg=2.5))
        other = PetFood(WetFood(volumeMl=400))
        assert first == second
        assert hash(first) == hash(second)
        assert first != other

    def test_bhv_unions_030_an_absent_discriminator_is_filled_with_the_mapping_default(
        self,
    ) -> None:
        import json

        result = ObjectSerializer().deserialize('{"weightKg":2.5}', "DryFood")
        assert result.food_type == "dry"
        assert json.loads(ObjectSerializer().serialize(result))["foodType"] == "dry"

    def test_bhv_unions_032_a_oneof_wrapper_built_from_a_variant_serializes_with_its_discriminator(
        self,
    ) -> None:
        import json

        wrapped = PetFood(DryFood(weightKg=2.5))
        assert wrapped.actual_instance == DryFood(weightKg=2.5)
        assert json.loads(ObjectSerializer().serialize(wrapped)) == {
            "foodType": "dry",
            "weightKg": 2.5,
        }

    def test_bhv_unions_034_a_oneof_wrapper_holds_its_variant_and_not_the_other(
        self,
    ) -> None:
        dry = PetFood(DryFood(weightKg=2.5))
        assert isinstance(dry.actual_instance, DryFood)
        assert not isinstance(dry.actual_instance, WetFood)
        wet = PetFood(WetFood(volumeMl=400))
        assert isinstance(wet.actual_instance, WetFood)
        assert not isinstance(wet.actual_instance, DryFood)

    @pytest.mark.parametrize(
        "json_str",
        [
            '{"foodType":"raw","calories":300}',
            '{"foodType":"Dry","weightKg":2.5}',
        ],
    )
    def test_bhv_unions_035_a_discriminator_value_not_in_the_mapping_fails_to_deserialize(
        self, json_str: str
    ) -> None:
        with pytest.raises(SerializationException):
            ObjectSerializer().deserialize(json_str, "PetFood")

    @pytest.mark.parametrize(
        "json_str",
        [
            '{"weightKg":2.5}',
            '{"foodType":"","weightKg":2.5}',
            '{"foodType":"raw","calories":300}',
        ],
    )
    def test_bhv_unions_033_an_unknown_missing_or_empty_discriminator_is_a_serialization_error(
        self, json_str: str
    ) -> None:
        with pytest.raises(SerializationException):
            ObjectSerializer().deserialize(json_str, "PetFood")


class TestAnyOfPetTreatment:
    """anyOf without discriminator: PetTreatment"""

    def test_bhv_unions_014_deserializes_medication(self) -> None:
        json_str = '{"drugName":"Amoxicillin","dosage":"500mg"}'
        result = ObjectSerializer().deserialize(json_str, "PetTreatment")
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, Medication)

    def test_bhv_unions_006_deserializes_surgery(self) -> None:
        json_str = '{"procedureName":"Spay","durationMinutes":45}'
        result = ObjectSerializer().deserialize(json_str, "PetTreatment")
        assert result is not None
        assert result.actual_instance is not None
        assert isinstance(result.actual_instance, Surgery)

    def test_bhv_unions_008_serialize_round_trip(self) -> None:
        json_str = '{"drugName":"Amoxicillin","dosage":"500mg"}'
        result = ObjectSerializer().deserialize(json_str, "PetTreatment")
        serialized = ObjectSerializer().serialize(result)
        assert serialized is not None
        assert len(serialized) > 0

    def test_bhv_unions_001_a_factory_wraps_its_variant_and_only_that_variant_matches(
        self,
    ) -> None:
        medication = Medication(drugName="Amoxicillin", dosage="500mg")
        wrapped = PetTreatment(medication)
        assert wrapped.actual_instance == medication
        assert isinstance(wrapped.actual_instance, Medication)
        assert not isinstance(wrapped.actual_instance, Surgery)
        assert wrapped.matched_instances == [medication]

    def test_bhv_unions_011_anyof_wrappers_have_value_equality_and_a_matching_hash(
        self,
    ) -> None:
        first = PetTreatment(Medication(drugName="Amoxicillin", dosage="500mg"))
        second = PetTreatment(Medication(drugName="Amoxicillin", dosage="500mg"))
        other = PetTreatment(Surgery(procedureName="Spay", durationMinutes=45))
        assert first == second
        assert hash(first) == hash(second)
        assert first != other

    def test_bhv_unions_007_no_matching_variant_raises(self) -> None:
        """oneof-nondiscriminator-no-match-silent: a payload matching neither
        Medication nor Surgery must raise rather than yield a silently-empty
        union.
        """
        json_str = '{"unrelatedKey":"value","anotherUnknown":123}'
        with pytest.raises(SerializationException):
            ObjectSerializer().deserialize(json_str, "PetTreatment")

    def test_bhv_unions_009_anyof_retains_all_matching_variants_losslessly(
        self,
    ) -> None:
        """anyOf retains all matching variants (medication and surgery)
        losslessly.

        PetTreatment is a non-discriminated anyOf of Medication and Surgery,
        documented as matching a medication, a surgery, OR BOTH. A payload that
        satisfies BOTH variants at once must retain BOTH -- the Medication data
        AND the Surgery data must all be accessible on the decoded value, not
        just the first variant -- and must re-serialize to JSON containing ALL
        FOUR fields so the co-satisfied data round-trips with no silent drop.
        """
        json_str = (
            '{"drugName":"Amoxicillin","dosage":"250mg",'
            '"procedureName":"Spay","durationMinutes":45}'
        )
        result = ObjectSerializer().deserialize(json_str, "PetTreatment")
        assert result is not None

        # BOTH variants are retained on the decoded value, not just the first.
        medications = [m for m in result.matched_instances if isinstance(m, Medication)]
        surgeries = [s for s in result.matched_instances if isinstance(s, Surgery)]
        assert len(medications) == 1
        assert len(surgeries) == 1
        assert medications[0].drug_name == "Amoxicillin"
        assert medications[0].dosage == "250mg"
        assert surgeries[0].procedure_name == "Spay"
        assert surgeries[0].duration_minutes == 45

        # Re-serialize: all four fields survive the round-trip (lossless).
        serialized = ObjectSerializer().serialize(result)
        assert "Amoxicillin" in serialized
        assert "250mg" in serialized
        assert "Spay" in serialized
        assert "45" in serialized
        for field in ("drugName", "dosage", "procedureName", "durationMinutes"):
            assert field in serialized

    def test_bhv_unions_010_anyof_shared_property_collision_last_wins(self) -> None:
        """anyOf-collision-last-wins: both Medication and Surgery declare
        `notes`. When a PetTreatment retains both members with DIFFERENT
        `notes` values, the merged encoding keeps the LAST-declared member's
        value (Surgery) and emits `notes` exactly once -- never a duplicate key.
        """
        combined = PetTreatment(
            [
                Medication(drugName="Amoxicillin", notes="from-medication"),
                Surgery(procedureName="Spay", notes="from-surgery"),
            ]
        )

        serialized = ObjectSerializer().serialize(combined)

        # Surgery is the later-declared member, so its `notes` wins the collision.
        assert "from-surgery" in serialized
        assert "from-medication" not in serialized
        # `notes` appears exactly once -- no duplicate key.
        assert serialized.count('"notes"') == 1

    def test_bhv_unions_003_anyof_single_variant_still_round_trips(self) -> None:
        """Retain-all must not regress the single-variant case: a
        medication-only and a surgery-only payload each still decode to exactly
        one retained variant and serialize back without leaking the other
        variant's fields.
        """
        med = ObjectSerializer().deserialize(
            '{"drugName":"Amoxicillin","dosage":"500mg"}', "PetTreatment"
        )
        assert isinstance(med.actual_instance, Medication)
        assert len(med.matched_instances) == 1
        med_json = ObjectSerializer().serialize(med)
        assert "drugName" in med_json
        assert "procedureName" not in med_json

        surg = ObjectSerializer().deserialize(
            '{"procedureName":"Spay","durationMinutes":45}', "PetTreatment"
        )
        assert isinstance(surg.actual_instance, Surgery)
        assert len(surg.matched_instances) == 1
        surg_json = ObjectSerializer().serialize(surg)
        assert "procedureName" in surg_json
        assert "drugName" not in surg_json


class TestAnyOfTreatmentRecord:
    """anyOf variants are tried in DECLARATION order: TreatmentRecord declares
    Surgery before Medication, the reverse of PetTreatment and of the alphabet."""

    _BOTH = '{"drugName":"Amoxicillin","procedureName":"Spay"}'

    def test_bhv_unions_005_variants_are_retained_in_declaration_order(self) -> None:
        record = ObjectSerializer().deserialize(self._BOTH, "TreatmentRecord")
        assert [type(m) for m in record.matched_instances] == [Surgery, Medication]
        assert isinstance(record.actual_instance, Surgery)

    def test_the_variant_tuple_follows_the_declaration(self) -> None:
        from petstore_client.models.treatment_record import TreatmentRecord

        assert TreatmentRecord.any_of_schemas == ("Surgery", "Medication")

    def test_bhv_unions_002_a_shared_property_keeps_the_later_declared_variant_value_once(
        self,
    ) -> None:
        from petstore_client.models.treatment_record import TreatmentRecord

        # Medication is declared after Surgery here, so its `notes` wins.
        record = TreatmentRecord(
            [
                Surgery(procedureName="Spay", notes="from-surgery"),
                Medication(drugName="Amoxicillin", notes="from-medication"),
            ]
        )
        serialized = ObjectSerializer().serialize(record)
        assert "from-medication" in serialized
        assert "from-surgery" not in serialized
        assert serialized.count('"notes"') == 1

    def test_bhv_unions_025_the_opposite_declaration_order_keeps_its_own_order(
        self,
    ) -> None:
        treatment = ObjectSerializer().deserialize(self._BOTH, "PetTreatment")
        assert [type(m) for m in treatment.matched_instances] == [Medication, Surgery]


class TestOneOfNonDiscriminatorValidator:
    """oneof-multiple-match: the non-discriminator oneOf validator now uses
    FIRST-match semantics -- it returns the first variant the payload
    validates against rather than counting matches and raising 'Multiple
    matches found' on more than one. This aligns Python with the other 11
    SDKs. SetPetAvatarThumbnailRequest is the spec's non-discriminator oneOf
    (bytes | List[bytes]); these tests cover the first-match-returns and
    no-match-still-raises branches of the rewritten validator.
    """

    def test_bhv_serializationobject_128_first_variant_match_returns_value(
        self,
    ) -> None:
        req = SetPetAvatarThumbnailRequest(actual_instance=b"avatar-bytes")
        assert req.actual_instance == b"avatar-bytes"

    def test_bhv_unions_018_scalar_base64_string_resolves_to_the_scalar_variant(
        self,
    ) -> None:
        restored = ObjectSerializer().deserialize(
            '"AQIDBA=="', SetPetAvatarThumbnailRequest
        )
        assert restored is not None
        assert restored.actual_instance == b"\x01\x02\x03\x04"
        assert not isinstance(restored.actual_instance, list)

    def test_bhv_unions_023_byte_oneof_scalar_round_trips_through_base64(self) -> None:
        raw = b"\x01\x02\x03\x04"
        wire = ObjectSerializer().serialize(SetPetAvatarThumbnailRequest(raw))
        restored = ObjectSerializer().deserialize(wire, SetPetAvatarThumbnailRequest)
        assert restored is not None
        assert restored.actual_instance == raw

    def test_bhv_unions_037_first_variant_match_returns_value(self) -> None:
        req = SetPetAvatarThumbnailRequest(actual_instance=b"avatar-bytes")
        assert req.actual_instance == b"avatar-bytes"

    def test_second_variant_match_returns_value(self) -> None:
        req = SetPetAvatarThumbnailRequest(actual_instance=[b"a", b"b"])
        assert req.actual_instance == [b"a", b"b"]

    def test_bhv_serializationobject_129_no_matching_variant_still_raises_on_decode(
        self,
    ) -> None:
        """The no-match error path is preserved by the first-match rewrite."""
        with pytest.raises(ValidationError):
            SetPetAvatarThumbnailRequest.model_validate({"actual_instance": object()})

    def test_bhv_serializationobject_127_resolve_any_of_throws_when_no_variant_matches(
        self,
    ) -> None:
        with pytest.raises(SerializationException):
            ObjectSerializer().deserialize('{"unrelated": 1}', ReserveStockResponse)

    def test_bhv_unions_039_the_constructor_keeps_a_value_no_variant_accepts(
        self,
    ) -> None:
        """F316: building a wrapper never validates; decoding does."""
        value = object()

        assert (
            SetPetAvatarThumbnailRequest(actual_instance=value).actual_instance is value
        )
        assert SetPetAvatarThumbnailRequest(value).actual_instance is value

    def test_the_positional_form_takes_exactly_one_argument(self) -> None:
        with pytest.raises(ValueError, match="only 1 is allowed"):
            SetPetAvatarThumbnailRequest(b"a", b"b")


class TestAllOfPetWithOwner:
    """allOf: PetWithOwner"""

    def test_bhv_unions_004_deserializes_all_properties(self) -> None:
        json_str = '{"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John","ownerEmail":"john@example.com"}'
        result = ObjectSerializer().deserialize(json_str, "PetWithOwner")
        assert result is not None
        assert result.name == "doggie"
        assert result.owner_name == "John"
        assert result.owner_email == "john@example.com"

    def test_email_format_is_a_plain_string_not_validated(self) -> None:
        json_str = '{"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John Doe","ownerEmail":"not-an-email"}'
        result = ObjectSerializer().deserialize(json_str, "PetWithOwner")
        assert result is not None
        assert type(result.owner_email) is str
        assert result.owner_email == "not-an-email"

    def test_bhv_unions_021_serializes_to_json(self) -> None:
        json_str = '{"name":"Fido","photoUrls":["http://example.com/fido.jpg"],"ownerName":"John Doe"}'
        result = ObjectSerializer().deserialize(json_str, "PetWithOwner")
        serialized = ObjectSerializer().serialize(result)
        assert "Fido" in serialized
        assert "John Doe" in serialized

    def test_bhv_unions_017_round_trip_preserves_fields(self) -> None:
        json_str = '{"name":"Buddy","photoUrls":["http://example.com/buddy.jpg"],"ownerName":"Jane Smith"}'
        original = ObjectSerializer().deserialize(json_str, "PetWithOwner")
        serialized = ObjectSerializer().serialize(original)
        restored = ObjectSerializer().deserialize(serialized, "PetWithOwner")
        assert original is not None
        assert restored is not None
        assert restored.name == original.name
        assert restored.owner_name == original.owner_name


class TestUnionWrapperBehaviours:
    def test_bhv_unions_038_an_undecodable_union_body_raises_the_serialization_error(
        self,
    ) -> None:
        with pytest.raises(SerializationException):
            ObjectSerializer().deserialize('{"foodType":3}', "PetFood")
        with pytest.raises(SerializationException):
            ObjectSerializer().deserialize('{"foodType":"fish"}', "PetFood")

    def test_bhv_unions_040_the_wrapper_holds_the_variant_it_was_built_from(
        self,
    ) -> None:
        req = SetPetAvatarThumbnailRequest(actual_instance=b"avatar-bytes")

        assert isinstance(req.actual_instance, bytes)
        assert not isinstance(req.actual_instance, list)

    def test_bhv_unions_041_a_oneof_without_a_discriminator_decodes_each_shape_to_its_variant(
        self,
    ) -> None:
        single = ObjectSerializer().deserialize(
            '"aGk="', "SetPetAvatarThumbnailRequest"
        )
        many = ObjectSerializer().deserialize(
            '["aGk=", "aGk="]', "SetPetAvatarThumbnailRequest"
        )

        assert single.actual_instance == b"hi"
        assert many.actual_instance == [b"hi", b"hi"]

    def test_bhv_unions_042_a_wrapper_built_with_no_variant_holds_no_instance(
        self,
    ) -> None:
        assert SetPetAvatarThumbnailRequest().actual_instance is None
        assert (
            SetPetAvatarThumbnailRequest(actual_instance=None).actual_instance is None
        )

    def test_bhv_unions_043_a_discriminator_set_on_a_subtype_is_written_as_given(
        self,
    ) -> None:
        import json

        food = DryFood(food_type="custom", weight_kg=2.5)

        assert json.loads(ObjectSerializer().serialize(food))["foodType"] == "custom"
