import base64
import datetime
import decimal
import json
import re
from enum import Enum
from typing import Any, Dict, List, Optional, Type, TypeVar, Union

from dateutil.parser import parse
from pydantic import BaseModel, SecretStr

import petstore_client.models


T = TypeVar('T')


class SerializationError(Exception):
    """Exception raised when serialization or deserialization fails."""

    def __init__(self, message: str, cause: Optional[Exception] = None):
        super().__init__(message)
        self.message = message
        self.cause = cause


class ObjectSerializer:
    """Handles JSON serialization and deserialization for API requests and responses.

    All serde operations in the generated client route through this class.
    The parameter encoding methods provide consistent value conversion for
    URL path, query string, header, and form parameters.
    """

    _PRIMITIVE_TYPES = (float, bool, bytes, str, int)

    _NATIVE_TYPES_MAPPING = {
        'int': int,
        'long': int,
        'float': float,
        'str': str,
        'bool': bool,
        'bytes': bytes,
        'date': datetime.date,
        'datetime': datetime.datetime,
        'decimal': decimal.Decimal,
        'object': object,
    }

    _DATETIME_FORMAT = '%Y-%m-%dT%H:%M:%S%z'

    def __init__(self) -> None:
        pass

    def serialize(self, obj: Any) -> str:
        """Serialize an object to a JSON string."""
        try:
            if isinstance(obj, BaseModel):
                if hasattr(obj, 'actual_instance'):
                    return self.serialize(obj.actual_instance)
                return obj.model_dump_json(by_alias=True, exclude_none=True)
            return json.dumps(ObjectSerializer._sanitize_for_serialization(obj), default=str)
        except Exception as e:
            raise SerializationError(f'Failed to serialize object to JSON: {e}', e)

    def deserialize(self, json_string: Optional[str], target_type: Union[str, Type[T]]) -> Optional[T]:
        """Deserialize a JSON string to an object of the specified type."""
        try:
            if json_string is None or json_string == '':
                return None

            data = json.loads(json_string)
            return self._deserialize(data, target_type)  # type: ignore[no-any-return]
        except json.JSONDecodeError as e:
            raise SerializationError(f'Failed to parse JSON: {e}', e)
        except Exception as e:
            raise SerializationError(f'Failed to deserialize JSON to {target_type}: {e}', e)

    @classmethod
    def _sanitize_for_serialization(cls, obj: Any) -> Any:
        """Convert an object to a JSON-safe dict/list/primitive.

        For Pydantic models, delegates to model_dump().
        """
        if obj is None:
            return None
        elif isinstance(obj, BaseModel):
            return obj.model_dump(by_alias=True, exclude_none=True)
        elif isinstance(obj, Enum):
            return obj.value
        elif isinstance(obj, SecretStr):
            return obj.get_secret_value()
        elif isinstance(obj, bytes):
            return base64.b64encode(obj).decode('ascii')
        elif isinstance(obj, cls._PRIMITIVE_TYPES):
            return obj
        elif isinstance(obj, list):
            return [cls._sanitize_for_serialization(item) for item in obj]
        elif isinstance(obj, tuple):
            return tuple(cls._sanitize_for_serialization(item) for item in obj)
        elif isinstance(obj, (datetime.datetime, datetime.date)):
            return obj.isoformat()
        elif isinstance(obj, decimal.Decimal):
            return str(obj)
        elif isinstance(obj, dict):
            return {key: cls._sanitize_for_serialization(val) for key, val in obj.items()}
        else:
            return str(obj)

    def _deserialize(self, data: Any, klass: Any) -> Any:
        """Deserialize parsed data to the target type.

        For Pydantic models, delegates to model_validate().
        """
        if data is None:
            return None

        if isinstance(klass, str):
            if klass.startswith('List['):
                m = re.match(r'List\[(.*)]', klass)
                assert m is not None, 'Malformed List type definition'
                sub_kls = m.group(1)
                return [self._deserialize(item, sub_kls) for item in data]

            if klass.startswith('Dict['):
                m = re.match(r'Dict\[([^,]*), (.*)]', klass)
                assert m is not None, 'Malformed Dict type definition'
                sub_kls = m.group(2)
                return {k: self._deserialize(v, sub_kls) for k, v in data.items()}

            if klass in self._NATIVE_TYPES_MAPPING:
                klass = self._NATIVE_TYPES_MAPPING[klass]
            else:
                klass = getattr(petstore_client.models, klass)

        if isinstance(klass, type) and issubclass(klass, BaseModel):
            if hasattr(klass, 'any_of_schemas') or hasattr(klass, 'one_of_schemas'):
                return self._deserialize_composed(data, klass)
            return klass.model_validate(data)
        elif klass == bytes:
            if isinstance(data, bytes):
                return data
            if isinstance(data, str):
                return base64.b64decode(data)
            return data
        elif klass in self._PRIMITIVE_TYPES:
            try:
                return klass(data)
            except (UnicodeEncodeError, TypeError):
                return data
        elif klass == object:
            return data
        elif klass == datetime.date:
            return parse(data).date()
        elif klass == datetime.datetime:
            return parse(data)
        elif klass == decimal.Decimal:
            return decimal.Decimal(data)
        elif isinstance(klass, type) and issubclass(klass, Enum):
            return klass(data)
        else:
            return data

    def _deserialize_composed(self, data: Any, klass: type) -> Any:
        """Deserialize data for oneOf/anyOf composed schemas.

        Tries each candidate schema and wraps the first successful
        result in the composed model.
        """
        schemas = getattr(klass, 'any_of_schemas', None) or getattr(klass, 'one_of_schemas', set())
        if hasattr(klass, 'discriminator_value_class_map') and isinstance(data, dict):
            disc_prop = getattr(klass, '_discriminator_property_name', None)
            if disc_prop is None:
                disc_prop = getattr(klass, '__discriminator_property_name', None)
            if disc_prop is None:
                for attr in dir(klass):
                    if 'discriminator_property_name' in attr:
                        disc_prop = getattr(klass, attr, None)
                        break
            if disc_prop and disc_prop in data:
                disc_value = data[disc_prop]
                mapped = klass.discriminator_value_class_map.get(str(disc_value))
                if mapped:
                    instance = self._deserialize(data, mapped)
                    return klass(instance)

        for schema_name in schemas:
            try:
                instance = self._deserialize(data, schema_name)
                return klass(instance)
            except Exception:
                continue
        return klass(data)

    @classmethod
    def stringify(cls, value: Any) -> str:
        """Convert a scalar value to its string representation.

        This is the canonical type-conversion method used by all parameter
        encoding helpers and by :class:`ValueSerializer`.

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

    @classmethod
    def to_path_value(cls, value: Any) -> str:
        """Convert a value to a string suitable for use as a URL path parameter."""
        return cls.stringify(value)

    @classmethod
    def to_query_value(cls, value: Any, collection_format: Optional[str] = None) -> Any:
        """Convert a value to a representation suitable for use as a query parameter.

        For collections, joins using the specified collection format delimiter.
        """
        if value is None:
            return None
        if isinstance(value, list):
            items = [cls.stringify(v) for v in value]
            if collection_format == 'multi':
                return items
            if collection_format == 'ssv':
                return ' '.join(items)
            if collection_format == 'tsv':
                return '\t'.join(items)
            if collection_format == 'pipes':
                return '|'.join(items)
            return ','.join(items)
        return cls.stringify(value)

    @classmethod
    def to_header_value(cls, value: Any) -> str:
        """Convert a value to a string suitable for use as an HTTP header value."""
        if value is None:
            return ''
        if isinstance(value, list):
            return ','.join(cls.stringify(v) for v in value)
        return cls.stringify(value)

    @classmethod
    def to_form_value(cls, value: Any) -> Any:
        """Convert a value to a representation suitable for use as a form parameter."""
        if value is None:
            return ''
        return cls.stringify(value)
