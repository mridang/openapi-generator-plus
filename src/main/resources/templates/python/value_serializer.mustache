import datetime
from typing import Any, Dict, List, Optional, Union
from urllib.parse import quote


class ValueSerializer:
    """Serializes parameter values for different HTTP locations (path, query, header, form)."""

    @classmethod
    def serialize(
        cls,
        value: Any,
        location: str,
        schema_type: str,
        collection_format: Optional[str] = None,
    ) -> Union[str, List[str], None]:
        """Serialize a value for inclusion in an HTTP request.

        Args:
            value: The value to serialize.
            location: Where the parameter appears ('path', 'query', 'header', 'form').
            schema_type: The OpenAPI schema type of the parameter.
            collection_format: For array parameters, how to format the collection
                ('multi', 'csv', 'ssv', 'tsv', 'pipes'). Defaults to 'csv'.

        Returns:
            The serialized string, a list of strings (for 'multi' format), or None.
        """
        if value is None:
            if location == 'query':
                return None
            return ''

        if isinstance(value, list):
            if location == 'query':
                if collection_format == 'multi':
                    return [cls._stringify(v) for v in value]
                elif collection_format == 'ssv':
                    return ' '.join(cls._stringify(v) for v in value)
                elif collection_format == 'tsv':
                    return '\t'.join(cls._stringify(v) for v in value)
                elif collection_format == 'pipes':
                    return '|'.join(cls._stringify(v) for v in value)
                else:
                    return ','.join(cls._stringify(v) for v in value)
            if location == 'header':
                return ','.join(cls._stringify(v) for v in value)

        str_val = cls._stringify(value)

        if location == 'path':
            return quote(str_val, safe='')

        return str_val

    @classmethod
    def serialize_deep_object(
        cls,
        param_name: str,
        value: Optional[Dict[str, Any]],
    ) -> Dict[str, str]:
        """Serialize a deepObject-style query parameter.

        Produces a dict of flattened keys in the form ``param_name[key]`` to
        stringified values, suitable for inclusion in a query string.

        Args:
            param_name: The parameter name (e.g. 'filter').
            value: The dict value to serialize.

        Returns:
            A dict of expanded keys to serialized values.
        """
        result: Dict[str, str] = {}
        if value is None:
            return result
        for key, val in value.items():
            result[f'{param_name}[{key}]'] = cls._stringify(val)
        return result

    @classmethod
    def _stringify(cls, value: Any) -> str:
        """Convert a scalar value to its string representation.

        Args:
            value: The value to convert.

        Returns:
            The string representation of the value.
        """
        if value is None:
            return ''
        if isinstance(value, bool):
            return 'true' if value else 'false'
        if isinstance(value, datetime.datetime):
            return value.isoformat()
        if isinstance(value, datetime.date):
            return value.isoformat()
        return str(value)
