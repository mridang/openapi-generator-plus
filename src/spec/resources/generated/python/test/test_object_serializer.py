# ruff: noqa
# mypy: ignore-errors
import datetime
import pytest
from petstore_client.object_serializer import ObjectSerializer, SerializationError
from petstore_client.models.category import Category
from petstore_client.models.order import OrderStatusEnum


class TestDateTimeOffsetPreservation:
    def test_utc_datetime_serializes_containing_offset(self) -> None:
        dt = datetime.datetime.fromisoformat("2024-01-01T12:30:45+00:00")
        result = ObjectSerializer.stringify(dt)
        assert "2024-01-01T12:30:45" in result
        assert "+00:00" in result or result.endswith("Z")

    def test_positive_offset_preserved(self) -> None:
        dt = datetime.datetime.fromisoformat("2024-01-01T12:30:45+05:30")
        result = ObjectSerializer.stringify(dt)
        assert "+05:30" in result

    def test_negative_offset_preserved(self) -> None:
        dt = datetime.datetime.fromisoformat("2024-01-01T12:30:45-08:00")
        result = ObjectSerializer.stringify(dt)
        assert "-08:00" in result

    def test_subseconds_dropped_from_serialized_datetime(self) -> None:
        dt = datetime.datetime.fromisoformat("2024-01-01T12:30:45.123000+00:00")
        result = ObjectSerializer.stringify(dt)
        assert ".123" not in result

    def test_date_only_serializes_as_iso8601_date(self) -> None:
        d = datetime.date(2024, 1, 1)
        result = ObjectSerializer.stringify(d)
        assert result == "2024-01-01"

    def test_serialized_datetime_ends_with_offset(self) -> None:
        dt = datetime.datetime(2024, 1, 1, 12, 30, 45, tzinfo=datetime.timezone.utc)
        result = ObjectSerializer.stringify(dt)
        import re

        assert re.search(r"[+-]\d{2}:\d{2}$|Z$", result), (
            f"should end with offset: {result}"
        )

    def test_round_trip_datetime_yields_equivalent_instant(self) -> None:
        original = datetime.datetime.fromisoformat("2024-01-01T12:30:45+05:30")
        serialized = ObjectSerializer.stringify(original)
        parsed = datetime.datetime.fromisoformat(serialized)
        assert original.utctimetuple() == parsed.utctimetuple()


class TestNonAsciiSerialization:
    def test_accented_character_not_unicode_escaped(self) -> None:
        result = ObjectSerializer().serialize("café")
        assert "é" in result

    def test_cjk_characters_not_unicode_escaped(self) -> None:
        result = ObjectSerializer().serialize("日本")
        assert "日本" in result

    def test_tab_character_escaped_properly_in_json(self) -> None:
        result = ObjectSerializer().serialize("a\tb")
        assert r"\t" in result


class TestDeserializationErrorWrapping:
    def test_truncated_json_raises_serialization_error(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize("{", "Category")

    def test_invalid_json_structure_raises_serialization_error(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize('"hello"', "int")

    def test_thrown_serialization_error_has_cause(self) -> None:
        with pytest.raises(SerializationError) as exc_info:
            ObjectSerializer().deserialize("{", "Category")
        assert exc_info.value.cause is not None


class TestStringify:
    def test_none_returns_empty_string(self) -> None:
        assert ObjectSerializer.stringify(None) == ""

    def test_boolean_true_returns_lowercase_string(self) -> None:
        assert ObjectSerializer.stringify(True) == "true"

    def test_boolean_false_returns_lowercase_string(self) -> None:
        assert ObjectSerializer.stringify(False) == "false"

    def test_integer_returns_string_representation(self) -> None:
        assert ObjectSerializer.stringify(42) == "42"

    def test_datetime_returns_iso8601_string(self) -> None:
        dt = datetime.datetime(2024, 1, 15, 10, 30, 0, tzinfo=datetime.timezone.utc)
        result = ObjectSerializer.stringify(dt)
        assert "2024-01-15" in result
        assert "10:30:00" in result

    def test_plain_string_passes_through_unchanged(self) -> None:
        assert ObjectSerializer.stringify("hello") == "hello"

    def test_float_returns_string_representation(self) -> None:
        assert ObjectSerializer.stringify(3.14) == "3.14"

    def test_date_returns_iso8601_date_string(self) -> None:
        d = datetime.date(2024, 1, 15)
        assert ObjectSerializer.stringify(d) == "2024-01-15"


class TestToPathValue:
    def test_returns_empty_string_for_none(self) -> None:
        assert ObjectSerializer.to_path_value(None) == ""

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_path_value("hello") == "hello"

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_path_value(42) == "42"

    def test_converts_true_to_true(self) -> None:
        assert ObjectSerializer.to_path_value(True) == "true"

    def test_converts_false_to_false(self) -> None:
        assert ObjectSerializer.to_path_value(False) == "false"


class TestToQueryValue:
    def test_returns_none_for_none(self) -> None:
        assert ObjectSerializer.to_query_value(None) is None

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_query_value("hello") == "hello"

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_query_value(42) == "42"

    def test_converts_true_to_true(self) -> None:
        assert ObjectSerializer.to_query_value(True) == "true"

    def test_converts_false_to_false(self) -> None:
        assert ObjectSerializer.to_query_value(False) == "false"

    def test_joins_array_with_comma_by_default(self) -> None:
        assert ObjectSerializer.to_query_value(["a", "b", "c"]) == "a,b,c"

    def test_joins_array_with_comma_for_csv(self) -> None:
        assert ObjectSerializer.to_query_value(["a", "b", "c"], "csv") == "a,b,c"

    def test_joins_array_with_space_for_ssv(self) -> None:
        assert ObjectSerializer.to_query_value(["a", "b", "c"], "ssv") == "a b c"

    def test_joins_array_with_tab_for_tsv(self) -> None:
        assert ObjectSerializer.to_query_value(["a", "b", "c"], "tsv") == "a\tb\tc"

    def test_joins_array_with_pipe_for_pipes(self) -> None:
        assert ObjectSerializer.to_query_value(["a", "b", "c"], "pipes") == "a|b|c"

    def test_returns_array_for_multi(self) -> None:
        assert ObjectSerializer.to_query_value(["a", "b", "c"], "multi") == [
            "a",
            "b",
            "c",
        ]

    def test_keeps_empty_slot_for_null_array_element_in_csv(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3], "csv") == "1,,3"

    def test_keeps_empty_slot_for_null_array_element_in_default_csv(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3]) == "1,,3"

    def test_keeps_empty_slot_for_null_array_element_in_ssv(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3], "ssv") == "1  3"

    def test_keeps_empty_slot_for_null_array_element_in_pipes(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3], "pipes") == "1||3"

    def test_keeps_empty_slot_for_null_array_element_in_multi(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3], "multi") == ["1", "", "3"]


class TestToHeaderValue:
    def test_returns_empty_string_for_none(self) -> None:
        assert ObjectSerializer.to_header_value(None) == ""

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_header_value("hello") == "hello"

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_header_value(42) == "42"

    def test_joins_array_with_comma(self) -> None:
        assert ObjectSerializer.to_header_value(["a", "b", "c"]) == "a,b,c"


class TestToFormValue:
    def test_returns_empty_string_for_none(self) -> None:
        assert ObjectSerializer.to_form_value(None) == ""

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_form_value("hello") == "hello"

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_form_value(42) == "42"

    def test_converts_true_to_true(self) -> None:
        assert ObjectSerializer.to_form_value(True) == "true"

    def test_converts_false_to_false(self) -> None:
        assert ObjectSerializer.to_form_value(False) == "false"


class TestToCookieValue:
    def test_returns_empty_string_for_none(self) -> None:
        assert ObjectSerializer.to_cookie_value(None) == ""

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_cookie_value("hello") == "hello"

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_cookie_value(42) == "42"


class TestSerialize:
    def test_serializes_model_to_valid_json(self) -> None:
        import json

        category = Category(id=1, name="Dogs")
        result = ObjectSerializer().serialize(category)
        data = json.loads(result)
        assert data["id"] == 1
        assert data["name"] == "Dogs"

    def test_handles_none(self) -> None:
        result = ObjectSerializer().serialize(None)
        assert result == "null"

    def test_includes_fields_set_to_default_values(self) -> None:
        import json

        category = Category(id=0, name="")
        result = ObjectSerializer().serialize(category)
        data = json.loads(result)
        assert "id" in data, "serialized JSON should include id field"
        assert data["id"] == 0
        assert "name" in data, "serialized JSON should include name field"
        assert data["name"] == ""


class TestUuidRoundtrip:
    def test_serializes_uuid_to_string(self) -> None:
        import uuid

        u = uuid.UUID("12345678-1234-5678-1234-567812345678")
        assert ObjectSerializer.stringify(u) == "12345678-1234-5678-1234-567812345678"

    def test_deserializes_uuid_string(self) -> None:
        import uuid

        result = ObjectSerializer()._deserialize(
            "12345678-1234-5678-1234-567812345678", "uuid.UUID"
        )
        assert isinstance(result, uuid.UUID)
        assert str(result) == "12345678-1234-5678-1234-567812345678"

    def test_serializes_uuid_via_sanitize(self) -> None:
        import json
        import uuid

        u = uuid.UUID("12345678-1234-5678-1234-567812345678")
        result = ObjectSerializer().serialize(u)
        assert json.loads(result) == "12345678-1234-5678-1234-567812345678"


class TestExtraFieldsOnDeserialize:
    def test_extra_field_in_json_is_ignored(self) -> None:
        json_str = '{"id":1,"name":"Dogs","unknown_field":"extra"}'
        category = ObjectSerializer().deserialize(json_str, "Category")
        assert isinstance(category, Category)
        assert category.id == 1
        assert category.name == "Dogs"


class TestExcludeNoneOnSerialize:
    def test_none_values_are_omitted(self) -> None:
        import json

        # Category with id=None should not include the id key on the wire.
        category = Category(id=None, name="Dogs")
        result = ObjectSerializer().serialize(category)
        data = json.loads(result)
        assert "id" not in data
        assert data["name"] == "Dogs"


class TestDeserialize:
    def test_deserializes_json_to_typed_model(self) -> None:
        json_str = '{"id":1,"name":"Dogs"}'
        category = ObjectSerializer().deserialize(json_str, "Category")
        assert isinstance(category, Category)
        assert category.id == 1
        assert category.name == "Dogs"

    def test_returns_none_for_empty_input(self) -> None:
        assert ObjectSerializer().deserialize("", "Category") is None

    def test_returns_none_for_none_input(self) -> None:
        assert ObjectSerializer().deserialize(None, "Category") is None

    def test_deserialize_applies_schema_default_for_absent_field(self) -> None:
        # default-on-deserialize: Order.status carries a schema `default: placed`.
        # When the JSON omits "status", deserialize must populate the enum
        # default rather than leaving it None. All 12 SDKs converge here.
        json_str = '{"id":10,"petId":198772}'
        order = ObjectSerializer().deserialize(json_str, "Order")
        assert order.status == OrderStatusEnum.PLACED

    def test_deserializes_self_referential_model(self) -> None:
        # A self-referential model (TreeNode.child is itself a TreeNode) must
        # decode the nested level into a typed TreeNode instance, not a raw
        # dict, and the absent grandchild must stay None.
        from petstore_client.models.tree_node import TreeNode

        json_str = '{"value":"root","child":{"value":"leaf"}}'
        top = ObjectSerializer().deserialize(json_str, "TreeNode")
        assert isinstance(top, TreeNode)
        assert top.value == "root"
        assert isinstance(top.child, TreeNode)
        assert top.child.value == "leaf"
        assert top.child.child is None


class TestBomTolerance:
    def test_deserializes_bom_prefixed_json(self) -> None:
        # P2: a UTF-8 BOM (U+FEFF) prefixing the JSON text must be tolerated.
        # RFC 8259 forbids it, but Windows-generated payloads often include
        # one; the serializer strips it transparently (parity with Java
        # Jackson / C# System.Text.Json). GREEN across all 12.
        json_str = '﻿{"id":1,"name":"Dogs"}'
        category = ObjectSerializer().deserialize(json_str, "Category")
        assert isinstance(category, Category)
        assert category.id == 1
        assert category.name == "Dogs"


class TestRequiredFieldNullRejection:
    """Gap AJ — null or missing on a required non-nullable field must raise.

    Pydantic 2 already rejects None for non-Optional typed fields; this test
    locks in that behaviour as a regression guard."""

    def test_null_on_required_field_raises(self) -> None:
        json_str = '{"name": null, "photoUrls": ["x"]}'
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize(json_str, "Pet")

    def test_missing_required_field_raises(self) -> None:
        json_str = '{"photoUrls": ["x"]}'
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize(json_str, "Pet")


class TestUnknownEnumRejection:
    """unknown-enum-deserialize-throws: an enum value not declared in the
    schema must raise the SDK's SerializationError on deserialize, never fall
    back to a silent default or an 'unknown' member. All 12 SDKs converge here.
    """

    def test_unknown_enum_value_on_scalar_raises(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize('"teleported"', OrderStatusEnum)

    def test_known_enum_value_on_scalar_round_trips(self) -> None:
        result = ObjectSerializer().deserialize('"placed"', OrderStatusEnum)
        assert result == OrderStatusEnum.PLACED

    def test_unknown_enum_value_on_model_field_raises(self) -> None:
        # The enum field lives on a model; an out-of-schema value must fail the
        # whole deserialize rather than coerce to the field default.
        json_str = '{"id": 1, "petId": 1, "quantity": 1, "status": "teleported"}'
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize(json_str, "Order")


class TestNanInfinityRejection:
    """Gap V — RFC 8259 §6 forbids NaN/Infinity/-Infinity in JSON.

    Python's default json module accepts them; ObjectSerializer must
    override that to align with Java/Go/Node/etc which reject."""

    def test_serialize_nan_raises(self) -> None:
        import math

        with pytest.raises(SerializationError):
            ObjectSerializer().serialize({"val": math.nan})

    def test_serialize_infinity_raises(self) -> None:
        import math

        with pytest.raises(SerializationError):
            ObjectSerializer().serialize({"val": math.inf})

    def test_serialize_negative_infinity_raises(self) -> None:
        import math

        with pytest.raises(SerializationError):
            ObjectSerializer().serialize({"val": -math.inf})

    def test_deserialize_nan_raises(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize('{"val": NaN}', "object")

    def test_deserialize_infinity_raises(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize('{"val": Infinity}', "object")


class TestTimeFormat:
    """4.8: ``format: time`` round-trips through ``datetime.time``."""

    def test_stringify_time_emits_iso8601(self) -> None:
        t = datetime.time(13, 45, 30)
        assert ObjectSerializer.stringify(t) == "13:45:30"

    def test_stringify_time_drops_microseconds(self) -> None:
        # Like datetime, we serialize with second precision so the wire
        # format matches the other 11 SDKs which all emit HH:MM:SS.
        t = datetime.time(13, 45, 30, 123456)
        assert ObjectSerializer.stringify(t) == "13:45:30"

    def test_serialize_time_in_dict(self) -> None:
        t = datetime.time(9, 0, 0)
        result = ObjectSerializer().serialize({"opens_at": t})
        assert "09:00:00" in result

    def test_deserialize_time_from_iso8601(self) -> None:
        result = ObjectSerializer()._deserialize("13:45:30", "datetime.time")
        assert result == datetime.time(13, 45, 30)

    def test_round_trip_time_yields_same_value(self) -> None:
        original = datetime.time(7, 30, 15)
        serialized = ObjectSerializer.stringify(original)
        parsed = ObjectSerializer()._deserialize(serialized, "datetime.time")
        assert parsed == original


class TestDurationFormat:
    """4.8: ``format: duration`` round-trips through ``datetime.timedelta``
    using the protobuf-JSON duration grammar (``-?\\d+(\\.\\d{1,9})?s``).
    Zitadel validates google.protobuf.Duration, so the wire form is a
    decimal second count with an ``s`` suffix, not ISO-8601."""

    def test_stringify_simple_duration(self) -> None:
        td = datetime.timedelta(hours=1, minutes=30)
        out = ObjectSerializer.stringify(td)
        assert out == "5400s"

    def test_stringify_duration_with_days(self) -> None:
        td = datetime.timedelta(days=3, hours=4)
        out = ObjectSerializer.stringify(td)
        assert out == "273600s"

    def test_stringify_zero_duration(self) -> None:
        td = datetime.timedelta(0)
        out = ObjectSerializer.stringify(td)
        # "0s" is the canonical zero-duration representation.
        assert out == "0s"

    def test_stringify_fractional_duration(self) -> None:
        td = datetime.timedelta(seconds=3600, microseconds=1)
        out = ObjectSerializer.stringify(td)
        # One microsecond is 1000 nanos -> six fractional digits.
        assert out == "3600.000001s"

    def test_stringify_negative_duration(self) -> None:
        td = datetime.timedelta(seconds=-1)
        out = ObjectSerializer.stringify(td)
        assert out == "-1s"

    def test_serialize_duration_in_dict(self) -> None:
        td = datetime.timedelta(minutes=5)
        result = ObjectSerializer().serialize({"ttl": td})
        assert "300s" in result

    def test_deserialize_duration_from_protobuf(self) -> None:
        result = ObjectSerializer()._deserialize("5400s", "datetime.timedelta")
        assert result == datetime.timedelta(hours=1, minutes=30)

    def test_deserialize_fractional_duration(self) -> None:
        result = ObjectSerializer()._deserialize(
            "3600.000000001s", "datetime.timedelta"
        )
        # Nanosecond precision below 1µs is truncated by timedelta resolution.
        assert result == datetime.timedelta(seconds=3600)

    def test_round_trip_duration_yields_same_value(self) -> None:
        original = datetime.timedelta(days=1, hours=2, minutes=3, seconds=4)
        serialized = ObjectSerializer.stringify(original)
        parsed = ObjectSerializer()._deserialize(serialized, "datetime.timedelta")
        assert parsed == original

    def test_iso8601_duration_is_rejected(self) -> None:
        # ISO-8601 durations are not valid protobuf-JSON and Zitadel
        # rejects them with 400; surface the mismatch as a ValueError.
        with pytest.raises(ValueError):
            ObjectSerializer()._deserialize("PT1H", "datetime.timedelta")

    def test_bare_integer_is_rejected(self) -> None:
        # A second count without the trailing 's' is not a valid duration.
        with pytest.raises(ValueError):
            ObjectSerializer()._deserialize("3600", "datetime.timedelta")


class TestModelDurationField:
    """4.8 (model level): a pydantic model carrying a ``format: duration``
    field must itself emit the protobuf-JSON wire form. The request *body*
    is a pydantic model serialized via ``model_dump_json``; pydantic's own
    default would emit ISO-8601 ("PT1H"), which Zitadel rejects with 400.
    The ``ProtobufDuration`` annotated alias forces "3600s" on the model
    path too, so these tests guard the path that the helper-only tests above
    do not cover."""

    def test_model_dump_json_emits_protobuf_duration(self) -> None:
        from petstore_client.models.edge_cases import EdgeCases

        model = EdgeCases(retryAfter=datetime.timedelta(hours=1))
        dumped = model.model_dump_json(by_alias=True, exclude_none=True)
        # The protobuf-JSON form, NOT pydantic's default ISO-8601 "PT1H".
        assert '"3600s"' in dumped
        assert "PT1H" not in dumped
        assert "PT" not in dumped

    def test_serialize_model_emits_protobuf_duration(self) -> None:
        from petstore_client.models.edge_cases import EdgeCases

        # Routed through the production code path (ObjectSerializer.serialize
        # → model_dump_json), exactly as the request body is built.
        model = EdgeCases(retryAfter=datetime.timedelta(minutes=5))
        out = ObjectSerializer().serialize(model)
        assert '"retryAfter":"300s"' in out.replace(" ", "")

    def test_model_validate_parses_protobuf_duration(self) -> None:
        from petstore_client.models.edge_cases import EdgeCases

        model = EdgeCases.model_validate({"retryAfter": "3600s"})
        assert model.retry_after == datetime.timedelta(hours=1)

    def test_model_duration_round_trips(self) -> None:
        from petstore_client.models.edge_cases import EdgeCases

        original = EdgeCases(retryAfter=datetime.timedelta(days=1, seconds=30))
        wire = ObjectSerializer().serialize(original)
        restored = ObjectSerializer().deserialize(wire, EdgeCases)
        assert restored is not None
        assert restored.retry_after == datetime.timedelta(days=1, seconds=30)

    def test_model_fractional_duration_emits_fraction(self) -> None:
        from petstore_client.models.edge_cases import EdgeCases

        model = EdgeCases(retryAfter=datetime.timedelta(seconds=3600, microseconds=1))
        dumped = model.model_dump_json(by_alias=True, exclude_none=True)
        assert '"3600.000001s"' in dumped


class TestDiscriminatorAutoInjection:
    """Gap K — a subtype constructed without the discriminator must still
    emit it on serialize. The generated DryFood/WetFood models default
    ``food_type`` to their oneOf mapping value ("dry"/"wet")."""

    def test_dry_subtype_auto_emits_discriminator(self) -> None:
        import json
        from petstore_client.models.dry_food import DryFood

        dry = DryFood(weightKg=2.5)
        data = json.loads(ObjectSerializer().serialize(dry))
        assert data["foodType"] == "dry"
        assert data["weightKg"] == 2.5

    def test_wet_subtype_auto_emits_discriminator(self) -> None:
        import json
        from petstore_client.models.wet_food import WetFood

        wet = WetFood(volumeMl=350)
        data = json.loads(ObjectSerializer().serialize(wet))
        assert data["foodType"] == "wet"

    def test_deserialize_parent_routes_to_subtype_by_discriminator(self) -> None:
        # A discriminator-tagged payload deserialized against the parent
        # PetFood oneOf must resolve to the concrete DryFood subtype rather
        # than the raw dict. get_discriminator_value maps foodType="dry" to
        # DryFood, which the composed deserializer wraps in PetFood.
        from petstore_client.models.dry_food import DryFood

        json_str = '{"foodType": "dry", "weightKg": 2.5}'
        result = ObjectSerializer().deserialize(json_str, "PetFood")
        assert result is not None
        assert isinstance(result.actual_instance, DryFood)
        assert result.actual_instance.weight_kg == 2.5

    def test_missing_discriminator_field_raises(self) -> None:
        # Gap AU-residual — a PetFood payload missing the `foodType`
        # discriminator must raise, not wrap the raw dict in the union
        # container's `actual_instance`. get_discriminator_value raises a
        # ValueError on the missing field, which the composed deserializer
        # surfaces as a SerializationError. Matches the 10 SDKs that throw.
        json_str = '{"weightKg": 5.0}'
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize(json_str, "PetFood")


class TestResolveOneOf:
    def test_resolve_one_of_returns_first_matching_variant(self) -> None:
        def miss(_: str) -> object:
            raise ValueError("variant A does not match")

        def hit(json_string: str) -> object:
            return "matched:" + json_string

        result = ObjectSerializer()._resolve_one_of("payload", [miss, hit])
        assert result == "matched:payload"

    def test_resolve_one_of_throws_on_no_match(self) -> None:
        # A payload matching none of the declared variants is a contract
        # violation and must fail loudly rather than be silently returned as None.
        def miss_a(_: str) -> object:
            raise ValueError("variant A does not match")

        def miss_b(_: str) -> object:
            raise ValueError("variant B does not match")

        with pytest.raises(SerializationError):
            ObjectSerializer()._resolve_one_of('{"unexpected": true}', [miss_a, miss_b])

    def test_resolve_any_of_throws_on_no_match(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer()._resolve_any_of("{}", [lambda _: None])
