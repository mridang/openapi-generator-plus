import pytest
from petstore_client.value_serializer import ValueSerializer


class TestValueSerializerPath:
    def test_null_returns_empty_string(self):
        assert ValueSerializer.serialize(None, 'path', 'string') == ''

    def test_string_returns_url_encoded_value(self):
        assert ValueSerializer.serialize('hello', 'path', 'string') == 'hello'

    def test_string_with_spaces_is_url_encoded(self):
        assert ValueSerializer.serialize('hello world', 'path', 'string') == 'hello%20world'

    def test_string_with_slash_is_url_encoded(self):
        assert ValueSerializer.serialize('a/b', 'path', 'string') == 'a%2Fb'

    def test_integer_returns_string(self):
        assert ValueSerializer.serialize(42, 'path', 'integer') == '42'

    def test_boolean_true_returns_true(self):
        assert ValueSerializer.serialize(True, 'path', 'boolean') == 'true'

    def test_boolean_false_returns_false(self):
        assert ValueSerializer.serialize(False, 'path', 'boolean') == 'false'


class TestValueSerializerQuery:
    def test_null_returns_none(self):
        assert ValueSerializer.serialize(None, 'query', 'string') is None

    def test_string_returns_as_is(self):
        assert ValueSerializer.serialize('hello', 'query', 'string') == 'hello'

    def test_integer_returns_string(self):
        assert ValueSerializer.serialize(42, 'query', 'integer') == '42'

    def test_boolean_true_returns_true(self):
        assert ValueSerializer.serialize(True, 'query', 'boolean') == 'true'

    def test_boolean_false_returns_false(self):
        assert ValueSerializer.serialize(False, 'query', 'boolean') == 'false'

    def test_array_joins_with_comma_by_default(self):
        assert ValueSerializer.serialize(['a', 'b', 'c'], 'query', 'array') == 'a,b,c'

    def test_array_joins_with_comma_for_csv(self):
        assert ValueSerializer.serialize(['a', 'b', 'c'], 'query', 'array', 'csv') == 'a,b,c'

    def test_array_joins_with_space_for_ssv(self):
        assert ValueSerializer.serialize(['a', 'b', 'c'], 'query', 'array', 'ssv') == 'a b c'

    def test_array_joins_with_tab_for_tsv(self):
        assert ValueSerializer.serialize(['a', 'b', 'c'], 'query', 'array', 'tsv') == 'a\tb\tc'

    def test_array_joins_with_pipe_for_pipes(self):
        assert ValueSerializer.serialize(['a', 'b', 'c'], 'query', 'array', 'pipes') == 'a|b|c'

    def test_array_returns_list_for_multi(self):
        assert ValueSerializer.serialize(['a', 'b', 'c'], 'query', 'array', 'multi') == ['a', 'b', 'c']

    def test_empty_array_returns_empty_string_for_csv(self):
        assert ValueSerializer.serialize([], 'query', 'array') == ''

    def test_empty_array_returns_empty_list_for_multi(self):
        assert ValueSerializer.serialize([], 'query', 'array', 'multi') == []

    def test_single_element_array_returns_single_value(self):
        assert ValueSerializer.serialize(['a'], 'query', 'array') == 'a'

    def test_array_of_integers_stringifies_elements(self):
        assert ValueSerializer.serialize([1, 2, 3], 'query', 'array') == '1,2,3'

    def test_array_of_booleans_stringifies_elements(self):
        assert ValueSerializer.serialize([True, False], 'query', 'array') == 'true,false'


class TestValueSerializerHeader:
    def test_null_returns_empty_string(self):
        assert ValueSerializer.serialize(None, 'header', 'string') == ''

    def test_string_returns_as_is(self):
        assert ValueSerializer.serialize('hello', 'header', 'string') == 'hello'

    def test_integer_returns_string(self):
        assert ValueSerializer.serialize(42, 'header', 'integer') == '42'

    def test_boolean_true_returns_true(self):
        assert ValueSerializer.serialize(True, 'header', 'boolean') == 'true'

    def test_array_joins_with_comma(self):
        assert ValueSerializer.serialize(['a', 'b', 'c'], 'header', 'array') == 'a,b,c'

    def test_empty_array_joins_to_empty_string(self):
        assert ValueSerializer.serialize([], 'header', 'array') == ''

    def test_array_of_integers_stringifies_and_joins(self):
        assert ValueSerializer.serialize([1, 2, 3], 'header', 'array') == '1,2,3'


class TestValueSerializerForm:
    def test_null_returns_empty_string(self):
        assert ValueSerializer.serialize(None, 'form', 'string') == ''

    def test_string_returns_as_is(self):
        assert ValueSerializer.serialize('hello', 'form', 'string') == 'hello'

    def test_integer_returns_string(self):
        assert ValueSerializer.serialize(42, 'form', 'integer') == '42'

    def test_boolean_true_returns_true(self):
        assert ValueSerializer.serialize(True, 'form', 'boolean') == 'true'

    def test_boolean_false_returns_false(self):
        assert ValueSerializer.serialize(False, 'form', 'boolean') == 'false'
