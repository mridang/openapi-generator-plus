import datetime
import pytest
from petstore_client.object_serializer import ObjectSerializer, SerializationError
from petstore_client.models.category import Category

class TestDateTimeOffsetPreservation:
    def test_utc_datetime_serializes_containing_offset(self) -> None:
        dt = datetime.datetime.fromisoformat('2024-01-01T12:30:45+00:00')
        result = ObjectSerializer.stringify(dt)
        assert '2024-01-01T12:30:45' in result
        assert '+00:00' in result or result.endswith('Z')

    def test_positive_offset_preserved(self) -> None:
        dt = datetime.datetime.fromisoformat('2024-01-01T12:30:45+05:30')
        result = ObjectSerializer.stringify(dt)
        assert '+05:30' in result

    def test_negative_offset_preserved(self) -> None:
        dt = datetime.datetime.fromisoformat('2024-01-01T12:30:45-08:00')
        result = ObjectSerializer.stringify(dt)
        assert '-08:00' in result

    def test_subseconds_dropped_from_serialized_datetime(self) -> None:
        dt = datetime.datetime.fromisoformat('2024-01-01T12:30:45.123000+00:00')
        result = ObjectSerializer.stringify(dt)
        assert '.123' not in result

    def test_date_only_serializes_as_iso8601_date(self) -> None:
        d = datetime.date(2024, 1, 1)
        result = ObjectSerializer.stringify(d)
        assert result == '2024-01-01'

    def test_serialized_datetime_ends_with_offset(self) -> None:
        dt = datetime.datetime(2024, 1, 1, 12, 30, 45, tzinfo=datetime.timezone.utc)
        result = ObjectSerializer.stringify(dt)
        import re
        assert re.search(r'[+-]\d{2}:\d{2}$|Z$', result), f'should end with offset: {result}'

    def test_round_trip_datetime_yields_equivalent_instant(self) -> None:
        original = datetime.datetime.fromisoformat('2024-01-01T12:30:45+05:30')
        serialized = ObjectSerializer.stringify(original)
        parsed = datetime.datetime.fromisoformat(serialized)
        assert original.utctimetuple() == parsed.utctimetuple()

class TestNonAsciiSerialization:
    def test_accented_character_not_unicode_escaped(self) -> None:
        result = ObjectSerializer().serialize('café')
        assert 'é' in result

    def test_cjk_characters_not_unicode_escaped(self) -> None:
        result = ObjectSerializer().serialize('日本')
        assert '日本' in result

    def test_tab_character_escaped_properly_in_json(self) -> None:
        result = ObjectSerializer().serialize('a\tb')
        assert r'\t' in result

class TestDeserializationErrorWrapping:
    def test_truncated_json_raises_serialization_error(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize('{', 'Category')

    def test_invalid_json_structure_raises_serialization_error(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize('"hello"', 'int')

    def test_thrown_serialization_error_has_cause(self) -> None:
        with pytest.raises(SerializationError) as exc_info:
            ObjectSerializer().deserialize('{', 'Category')
        assert exc_info.value.cause is not None

class TestStringify:
    def test_none_returns_empty_string(self) -> None:
        assert ObjectSerializer.stringify(None) == ''

    def test_boolean_true_returns_lowercase_string(self) -> None:
        assert ObjectSerializer.stringify(True) == 'true'

    def test_boolean_false_returns_lowercase_string(self) -> None:
        assert ObjectSerializer.stringify(False) == 'false'

    def test_integer_returns_string_representation(self) -> None:
        assert ObjectSerializer.stringify(42) == '42'

    def test_datetime_returns_iso8601_string(self) -> None:
        dt = datetime.datetime(2024, 1, 15, 10, 30, 0, tzinfo=datetime.timezone.utc)
        result = ObjectSerializer.stringify(dt)
        assert '2024-01-15' in result
        assert '10:30:00' in result

    def test_plain_string_passes_through_unchanged(self) -> None:
        assert ObjectSerializer.stringify('hello') == 'hello'

    def test_float_returns_string_representation(self) -> None:
        assert ObjectSerializer.stringify(3.14) == '3.14'

    def test_date_returns_iso8601_date_string(self) -> None:
        d = datetime.date(2024, 1, 15)
        assert ObjectSerializer.stringify(d) == '2024-01-15'

class TestToPathValue:
    def test_returns_empty_string_for_none(self) -> None:
        assert ObjectSerializer.to_path_value(None) == ''

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_path_value('hello') == 'hello'

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_path_value(42) == '42'

    def test_converts_true_to_true(self) -> None:
        assert ObjectSerializer.to_path_value(True) == 'true'

    def test_converts_false_to_false(self) -> None:
        assert ObjectSerializer.to_path_value(False) == 'false'

class TestToQueryValue:
    def test_returns_none_for_none(self) -> None:
        assert ObjectSerializer.to_query_value(None) is None

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_query_value('hello') == 'hello'

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_query_value(42) == '42'

    def test_converts_true_to_true(self) -> None:
        assert ObjectSerializer.to_query_value(True) == 'true'

    def test_converts_false_to_false(self) -> None:
        assert ObjectSerializer.to_query_value(False) == 'false'

    def test_joins_array_with_comma_by_default(self) -> None:
        assert ObjectSerializer.to_query_value(['a', 'b', 'c']) == 'a,b,c'

    def test_joins_array_with_comma_for_csv(self) -> None:
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'csv') == 'a,b,c'

    def test_joins_array_with_space_for_ssv(self) -> None:
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'ssv') == 'a b c'

    def test_joins_array_with_tab_for_tsv(self) -> None:
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'tsv') == 'a\tb\tc'

    def test_joins_array_with_pipe_for_pipes(self) -> None:
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'pipes') == 'a|b|c'

    def test_returns_array_for_multi(self) -> None:
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'multi') == ['a', 'b', 'c']

    def test_keeps_empty_slot_for_null_array_element_in_csv(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3], 'csv') == '1,,3'

    def test_keeps_empty_slot_for_null_array_element_in_default_csv(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3]) == '1,,3'

    def test_keeps_empty_slot_for_null_array_element_in_ssv(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3], 'ssv') == '1  3'

    def test_keeps_empty_slot_for_null_array_element_in_pipes(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3], 'pipes') == '1||3'

    def test_keeps_empty_slot_for_null_array_element_in_multi(self) -> None:
        assert ObjectSerializer.to_query_value([1, None, 3], 'multi') == ['1', '', '3']

class TestToHeaderValue:
    def test_returns_empty_string_for_none(self) -> None:
        assert ObjectSerializer.to_header_value(None) == ''

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_header_value('hello') == 'hello'

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_header_value(42) == '42'

    def test_joins_array_with_comma(self) -> None:
        assert ObjectSerializer.to_header_value(['a', 'b', 'c']) == 'a,b,c'

class TestToFormValue:
    def test_returns_empty_string_for_none(self) -> None:
        assert ObjectSerializer.to_form_value(None) == ''

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_form_value('hello') == 'hello'

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_form_value(42) == '42'

    def test_converts_true_to_true(self) -> None:
        assert ObjectSerializer.to_form_value(True) == 'true'

    def test_converts_false_to_false(self) -> None:
        assert ObjectSerializer.to_form_value(False) == 'false'

class TestToCookieValue:
    def test_returns_empty_string_for_none(self) -> None:
        assert ObjectSerializer.to_cookie_value(None) == ''

    def test_returns_the_string_for_a_string_value(self) -> None:
        assert ObjectSerializer.to_cookie_value('hello') == 'hello'

    def test_converts_integer_to_string(self) -> None:
        assert ObjectSerializer.to_cookie_value(42) == '42'

class TestSerialize:
    def test_serializes_model_to_valid_json(self) -> None:
        import json
        category = Category(id=1, name='Dogs')
        result = ObjectSerializer().serialize(category)
        data = json.loads(result)
        assert data['id'] == 1
        assert data['name'] == 'Dogs'

    def test_handles_none(self) -> None:
        result = ObjectSerializer().serialize(None)
        assert result == 'null'

    def test_includes_fields_set_to_default_values(self) -> None:
        import json
        category = Category(id=0, name='')
        result = ObjectSerializer().serialize(category)
        data = json.loads(result)
        assert 'id' in data, 'serialized JSON should include id field'
        assert data['id'] == 0
        assert 'name' in data, 'serialized JSON should include name field'
        assert data['name'] == ''

class TestUuidRoundtrip:
    def test_serializes_uuid_to_string(self) -> None:
        import uuid
        u = uuid.UUID('12345678-1234-5678-1234-567812345678')
        assert ObjectSerializer.stringify(u) == '12345678-1234-5678-1234-567812345678'

    def test_deserializes_uuid_string(self) -> None:
        import uuid
        result = ObjectSerializer()._deserialize(
            '12345678-1234-5678-1234-567812345678', 'uuid.UUID'
        )
        assert isinstance(result, uuid.UUID)
        assert str(result) == '12345678-1234-5678-1234-567812345678'

    def test_serializes_uuid_via_sanitize(self) -> None:
        import json
        import uuid
        u = uuid.UUID('12345678-1234-5678-1234-567812345678')
        result = ObjectSerializer().serialize(u)
        assert json.loads(result) == '12345678-1234-5678-1234-567812345678'

class TestExtraFieldsOnDeserialize:
    def test_extra_field_in_json_is_ignored(self) -> None:
        json_str = '{"id":1,"name":"Dogs","unknown_field":"extra"}'
        category = ObjectSerializer().deserialize(json_str, 'Category')
        assert isinstance(category, Category)
        assert category.id == 1
        assert category.name == 'Dogs'

class TestExcludeNoneOnSerialize:
    def test_none_values_are_omitted(self) -> None:
        import json
        # Category with id=None should not include the id key on the wire.
        category = Category(id=None, name='Dogs')
        result = ObjectSerializer().serialize(category)
        data = json.loads(result)
        assert 'id' not in data
        assert data['name'] == 'Dogs'

class TestDeserialize:
    def test_deserializes_json_to_typed_model(self) -> None:
        json_str = '{"id":1,"name":"Dogs"}'
        category = ObjectSerializer().deserialize(json_str, 'Category')
        assert isinstance(category, Category)
        assert category.id == 1
        assert category.name == 'Dogs'

    def test_returns_none_for_empty_input(self) -> None:
        assert ObjectSerializer().deserialize('', 'Category') is None

    def test_returns_none_for_none_input(self) -> None:
        assert ObjectSerializer().deserialize(None, 'Category') is None

class TestRequiredFieldNullRejection:
    """Gap AJ — null or missing on a required non-nullable field must raise.

    Pydantic 2 already rejects None for non-Optional typed fields; this test
    locks in that behaviour as a regression guard."""

    def test_null_on_required_field_raises(self) -> None:
        json_str = '{"name": null, "photoUrls": ["x"]}'
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize(json_str, 'Pet')

    def test_missing_required_field_raises(self) -> None:
        json_str = '{"photoUrls": ["x"]}'
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize(json_str, 'Pet')

class TestNanInfinityRejection:
    """Gap V — RFC 8259 §6 forbids NaN/Infinity/-Infinity in JSON.

    Python's default json module accepts them; ObjectSerializer must
    override that to align with Java/Go/Node/etc which reject."""

    def test_serialize_nan_raises(self) -> None:
        import math
        with pytest.raises(SerializationError):
            ObjectSerializer().serialize({'val': math.nan})

    def test_serialize_infinity_raises(self) -> None:
        import math
        with pytest.raises(SerializationError):
            ObjectSerializer().serialize({'val': math.inf})

    def test_serialize_negative_infinity_raises(self) -> None:
        import math
        with pytest.raises(SerializationError):
            ObjectSerializer().serialize({'val': -math.inf})

    def test_deserialize_nan_raises(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize('{"val": NaN}', 'object')

    def test_deserialize_infinity_raises(self) -> None:
        with pytest.raises(SerializationError):
            ObjectSerializer().deserialize('{"val": Infinity}', 'object')
