from typing import Any, Dict, List, Optional, Type, TypeVar
from urllib.parse import quote, urlencode

from ..api_client import ApiClient
from ..api_response import ApiResponse
from ..default_api_client import DefaultApiClient
from ..configuration import Configuration
from ..object_serializer import ObjectSerializer
from ..header_selector import HeaderSelector
from ..trace_context_util import inject_trace_context
from ..exceptions import ApiException
from ..auth.authenticator import Authenticator

T = TypeVar('T')


class BaseApi:
    """Base class for all API classes.

    Provides the invoke_api method that handles URL construction, header
    selection, body serialization, request dispatch, and response
    deserialization.
    """

    def __init__(
        self,
        api_client: Optional[ApiClient] = None,
        config: Optional[Configuration] = None,
    ):
        self._config = config or Configuration.get_default()
        self._api_client = api_client or DefaultApiClient(self._config)
        self._object_serializer = ObjectSerializer()
        self._header_selector = HeaderSelector()

    def _invoke_api(
        self,
        method: str,
        path: str,
        query_params: Dict[str, Any],
        header_params: Dict[str, str],
        body: Any,
        accepts: List[str],
        content_type: Optional[str],
        return_type: Optional[str],
        auth: Optional[Authenticator] = None,
    ) -> Any:
        """Invoke an API operation.

        :param method: HTTP method (GET, POST, PUT, DELETE, etc.)
        :param path: URL path (with path params already substituted)
        :param query_params: query parameters
        :param header_params: custom header parameters
        :param body: request body (model object or None)
        :param accepts: acceptable response content types
        :param content_type: request content type
        :param return_type: return type for deserialization (None for void)
        :param auth: optional authenticator for operation-specific auth
        :return: deserialized response or None
        :raises ApiException: if the API call fails
        """
        url = self._config.base_url + path

        if auth is not None:
            query_params.update(auth.get_query_params())

        if query_params:
            filtered = {k: v for k, v in query_params.items() if v is not None}
            if filtered:
                url += '?' + urlencode(filtered)

        is_multipart = content_type == 'multipart/form-data'
        headers = self._header_selector.select_headers(accepts, content_type or '', is_multipart)
        headers.update(self._config.default_headers)
        if header_params:
            headers.update(header_params)
        if auth is not None:
            headers.update(auth.get_auth_headers())
            cookies = auth.get_cookie_params()
            if cookies:
                cookie_str = '; '.join(f'{k}={v}' for k, v in cookies.items())
                existing = headers.get('Cookie', '')
                if existing:
                    headers['Cookie'] = existing + '; ' + cookie_str
                else:
                    headers['Cookie'] = cookie_str
        inject_trace_context(headers)

        serialized_body = None
        if body is not None:
            if content_type == 'multipart/form-data':
                serialized_body = body
            elif content_type is not None and (
                content_type.startswith('image/') or content_type == 'application/octet-stream'
            ):
                serialized_body = body
            else:
                serialized_body = self._object_serializer.serialize(body)

        response = self._api_client.send_request(method, url, headers, serialized_body)

        if response.status_code < 200 or response.status_code >= 300:
            raise ApiException(
                status=response.status_code,
                reason=f'API returned status code {response.status_code}',
                body=response.body,
            )

        if return_type is not None and response.body:
            resp_content_type = ''
            for k, v in response.headers.items():
                if k.lower() == 'content-type':
                    resp_content_type = v.split(';')[0].strip()
                    break
            if resp_content_type and not resp_content_type.startswith('application/json'):
                return response.body
            return self._object_serializer.deserialize(response.body, return_type)

        return None
