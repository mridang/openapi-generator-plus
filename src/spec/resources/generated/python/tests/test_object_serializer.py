import datetime
from petstore_client.object_serializer import ObjectSerializer
from petstore_client.models.category import Category


class TestStringify:
    def test_none_returns_empty_string(self):
        assert ObjectSerializer.stringify(None) == ''

    def test_boolean_true_returns_lowercase_string(self):
        assert ObjectSerializer.stringify(True) == 'true'

    def test_boolean_false_returns_lowercase_string(self):
        assert ObjectSerializer.stringify(False) == 'false'

    def test_integer_returns_string_representation(self):
        assert ObjectSerializer.stringify(42) == '42'

    def test_datetime_returns_iso8601_string(self):
        dt = datetime.datetime(2024, 1, 15, 10, 30, 0, tzinfo=datetime.timezone.utc)
        result = ObjectSerializer.stringify(dt)
        assert '2024-01-15' in result
        assert '10:30:00' in result

    def test_plain_string_passes_through_unchanged(self):
        assert ObjectSerializer.stringify('hello') == 'hello'

    def test_float_returns_string_representation(self):
        assert ObjectSerializer.stringify(3.14) == '3.14'

    def test_date_returns_iso8601_date_string(self):
        d = datetime.date(2024, 1, 15)
        assert ObjectSerializer.stringify(d) == '2024-01-15'


class TestToPathValue:
    def test_returns_empty_string_for_none(self):
        assert ObjectSerializer.to_path_value(None) == ''

    def test_returns_the_string_for_a_string_value(self):
        assert ObjectSerializer.to_path_value('hello') == 'hello'

    def test_converts_integer_to_string(self):
        assert ObjectSerializer.to_path_value(42) == '42'

    def test_converts_true_to_true(self):
        assert ObjectSerializer.to_path_value(True) == 'true'

    def test_converts_false_to_false(self):
        assert ObjectSerializer.to_path_value(False) == 'false'


class TestToQueryValue:
    def test_returns_none_for_none(self):
        assert ObjectSerializer.to_query_value(None) is None

    def test_returns_the_string_for_a_string_value(self):
        assert ObjectSerializer.to_query_value('hello') == 'hello'

    def test_converts_integer_to_string(self):
        assert ObjectSerializer.to_query_value(42) == '42'

    def test_converts_true_to_true(self):
        assert ObjectSerializer.to_query_value(True) == 'true'

    def test_joins_array_with_comma_by_default(self):
        assert ObjectSerializer.to_query_value(['a', 'b', 'c']) == 'a,b,c'

    def test_joins_array_with_comma_for_csv(self):
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'csv') == 'a,b,c'

    def test_joins_array_with_space_for_ssv(self):
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'ssv') == 'a b c'

    def test_joins_array_with_tab_for_tsv(self):
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'tsv') == 'a\tb\tc'

    def test_joins_array_with_pipe_for_pipes(self):
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'pipes') == 'a|b|c'

    def test_returns_array_for_multi(self):
        assert ObjectSerializer.to_query_value(['a', 'b', 'c'], 'multi') == ['a', 'b', 'c']


class TestToHeaderValue:
    def test_returns_empty_string_for_none(self):
        assert ObjectSerializer.to_header_value(None) == ''

    def test_returns_the_string_for_a_string_value(self):
        assert ObjectSerializer.to_header_value('hello') == 'hello'

    def test_converts_integer_to_string(self):
        assert ObjectSerializer.to_header_value(42) == '42'

    def test_joins_array_with_comma(self):
        assert ObjectSerializer.to_header_value(['a', 'b', 'c']) == 'a,b,c'


class TestToFormValue:
    def test_returns_empty_string_for_none(self):
        assert ObjectSerializer.to_form_value(None) == ''

    def test_returns_the_string_for_a_string_value(self):
        assert ObjectSerializer.to_form_value('hello') == 'hello'

    def test_converts_integer_to_string(self):
        assert ObjectSerializer.to_form_value(42) == '42'

    def test_converts_true_to_true(self):
        assert ObjectSerializer.to_form_value(True) == 'true'

    def test_converts_false_to_false(self):
        assert ObjectSerializer.to_form_value(False) == 'false'


class TestSerialize:
    def test_serializes_model_to_valid_json(self):
        import json

        category = Category(id=1, name='Dogs')
        result = ObjectSerializer().serialize(category)
        data = json.loads(result)
        assert data['id'] == 1
        assert data['name'] == 'Dogs'

    def test_handles_none(self):
        result = ObjectSerializer().serialize(None)
        assert result == 'null'


class TestDeserialize:
    def test_deserializes_json_to_typed_model(self):
        json_str = '{"id":1,"name":"Dogs"}'
        category = ObjectSerializer().deserialize(json_str, 'Category')
        assert isinstance(category, Category)
        assert category.id == 1
        assert category.name == 'Dogs'

    def test_returns_none_for_empty_input(self):
        assert ObjectSerializer().deserialize('', 'Category') is None

    def test_returns_none_for_none_input(self):
        assert ObjectSerializer().deserialize(None, 'Category') is None
