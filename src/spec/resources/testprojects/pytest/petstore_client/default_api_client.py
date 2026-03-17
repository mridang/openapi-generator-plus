from typing import Any, Dict, Optional
import gzip
import uuid
import zlib

import urllib3

from petstore_client.api_client import ApiClient
from petstore_client.api_response import ApiResponse
from petstore_client.configuration import Configuration

try:
    import brotli as _brotli  # type: ignore[import-untyped]
except ImportError:
    _brotli = None

try:
    import zstandard as _zstandard
except ImportError:
    _zstandard = None  # type: ignore[assignment]


def _supported_encodings() -> str:
    encodings = ['gzip', 'deflate']
    if _brotli is not None:
        encodings.append('br')
    if _zstandard is not None:
        encodings.append('zstd')
    return ', '.join(encodings)


class DefaultApiClient:
    """Default implementation of ApiClient using urllib3."""

    def __init__(self, config: Optional[Configuration] = None, pool_manager: Optional[Any] = None) -> None:
        if pool_manager is not None:
            self._pool_manager = pool_manager
        elif config is not None:
            kwargs = {}
            if not config.verify_ssl:
                kwargs['cert_reqs'] = 'CERT_NONE'
            elif config.ssl_ca_cert:
                kwargs['ca_certs'] = config.ssl_ca_cert
                kwargs['cert_reqs'] = 'CERT_REQUIRED'
            if config.proxy:
                self._pool_manager = urllib3.ProxyManager(config.proxy, **kwargs)
            else:
                self._pool_manager = urllib3.PoolManager(**kwargs)
        else:
            self._pool_manager = urllib3.PoolManager()

    def send_request(self, method: str, url: str, headers: Dict[str, str], body: Any = None) -> ApiResponse:
        """Send an HTTP request and return the response.

        :param method: HTTP method (GET, POST, PUT, DELETE, etc.)
        :param url: Fully qualified URL
        :param headers: HTTP headers
        :param body: Request body (serialized JSON string, bytes, dict for multipart, or None)
        :return: ApiResponse containing status code, body, and headers
        """
        headers.setdefault('Accept-Encoding', _supported_encodings())

        if isinstance(body, dict):
            boundary = str(uuid.uuid4())
            headers['Content-Type'] = f'multipart/form-data; boundary={boundary}'
            encoded_body = self._build_multipart_body(body, boundary)
        elif isinstance(body, bytes):
            encoded_body = body
        elif isinstance(body, str):
            encoded_body = body.encode('utf-8')
        else:
            encoded_body = None

        response = self._pool_manager.request(
            method, url, headers=headers, body=encoded_body, preload_content=False, decode_content=False
        )

        raw_data = response.read()
        content_encoding = (response.headers.get('content-encoding') or '').lower()
        decompressed = self._decompress_body(raw_data, content_encoding)
        response_body = decompressed.decode('utf-8') if decompressed else ''
        response_headers = dict(response.headers) if response.headers else {}

        return ApiResponse(status_code=response.status, body=response_body, headers=response_headers)

    def _build_multipart_body(self, form_parts: Dict[str, Any], boundary: str) -> bytes:
        """Build a multipart/form-data body from a dict of form parts.

        :param form_parts: dict mapping field names to values
        :param boundary: the multipart boundary string
        :return: encoded multipart body as bytes
        """
        parts: list[bytes] = []
        for name, value in form_parts.items():
            if isinstance(value, list):
                for item in value:
                    parts.append(self._multipart_part(name, item, boundary))
            else:
                parts.append(self._multipart_part(name, value, boundary))
        parts.append(f'--{boundary}--\r\n'.encode('utf-8'))
        return b''.join(parts)

    @staticmethod
    def _multipart_part(name: str, value: Any, boundary: str) -> bytes:
        """Build a single MIME part for multipart encoding.

        :param name: the field name
        :param value: the field value (str, bytes, file-like, or model object)
        :param boundary: the multipart boundary string
        :return: encoded MIME part as bytes
        """
        if hasattr(value, 'read'):
            raw_data = value.read()
            file_data: bytes = raw_data.encode('utf-8') if isinstance(raw_data, str) else raw_data
            header = (
                f'--{boundary}\r\n'
                f'Content-Disposition: form-data; name="{name}"; filename="{name}"\r\n'
                f'Content-Type: application/octet-stream\r\n\r\n'
            )
            return header.encode('utf-8') + file_data + b'\r\n'
        elif isinstance(value, bytes):
            header = (
                f'--{boundary}\r\n'
                f'Content-Disposition: form-data; name="{name}"; filename="{name}"\r\n'
                f'Content-Type: application/octet-stream\r\n\r\n'
            )
            return header.encode('utf-8') + value + b'\r\n'
        elif hasattr(value, 'model_dump_json'):
            json_str: str = value.model_dump_json(by_alias=True, exclude_none=True)
            header = (
                f'--{boundary}\r\n'
                f'Content-Disposition: form-data; name="{name}"\r\n'
                f'Content-Type: application/json\r\n\r\n'
            )
            return header.encode('utf-8') + json_str.encode('utf-8') + b'\r\n'
        else:
            part = f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'
            return part.encode('utf-8')

    @staticmethod
    def _decompress_body(data: bytes, encoding: str) -> bytes:
        """Decompress response body based on Content-Encoding header.

        Supports gzip and deflate natively. Brotli and zstd are supported
        when their respective packages (brotli, zstandard) are installed.

        :param data: raw response bytes
        :param encoding: Content-Encoding header value
        :return: decompressed bytes
        """
        if not data:
            return data
        if encoding in ('gzip', 'x-gzip'):
            return gzip.decompress(data)
        if encoding == 'deflate':
            return zlib.decompress(data)
        if encoding == 'br' and _brotli is not None:
            br_result: bytes = _brotli.decompress(data)
            return br_result
        if encoding == 'zstd' and _zstandard is not None:
            zstd_result: bytes = _zstandard.ZstdDecompressor().decompress(data, max_output_size=len(data) * 16)
            return zstd_result
        return data
