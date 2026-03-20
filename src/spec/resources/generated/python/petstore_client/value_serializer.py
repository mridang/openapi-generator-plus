import datetime
from typing import Any, List, Optional, Union
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
