from typing import Any, Dict, List, Optional, Type, TypeVar
from urllib.parse import quote, urlencode

from ..api_client import ApiClient
from ..api_response import ApiResponse
from ..api_result import ApiResult
from ..default_api_client import DefaultApiClient
from ..configuration import Configuration
from ..object_serializer import ObjectSerializer
from ..header_selector import HeaderSelector
from ..trace_context_util import inject_trace_context
from ..exceptions import ApiException
from ..exceptions.client_exception import ClientException
from ..exceptions.server_exception import ServerException
from ..exceptions.bad_request_exception import BadRequestException
from ..exceptions.unauthorized_exception import UnauthorizedException
from ..exceptions.forbidden_exception import ForbiddenException
from ..exceptions.not_found_exception import NotFoundException
from ..exceptions.conflict_exception import ConflictException
from ..exceptions.unprocessable_entity_exception import UnprocessableEntityException
from ..exceptions.internal_server_error_exception import InternalServerErrorException
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
        """Create an API instance.

        When called with no arguments, uses the default configuration and
        a default-constructed :class:`DefaultApiClient` (with default
        transport settings).

        When called with a configuration only, creates a
        :class:`DefaultApiClient` with default transport settings.

        When called with both an api_client and config, uses the provided
        instances directly (this is the path used by :class:`Client`).

        Args:
            api_client: The HTTP transport client. If ``None``, a
                :class:`DefaultApiClient` with default transport is created.
            config: API-level configuration (base URL and default headers).
                If ``None``, the default configuration is used.
        """
        self._config = config or Configuration.get_default()
        self._api_client = api_client or DefaultApiClient()
        self._object_serializer = ObjectSerializer()
        self._header_selector = HeaderSelector()

    def _invoke_api_for_result(
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
    ) -> 'ApiResult[Any]':
        """Invoke an API operation and return the full result.

        Args:
            method: HTTP method (GET, POST, PUT, DELETE, etc.).
            path: URL path (with path params already substituted).
            query_params: Query parameters.
            header_params: Custom header parameters.
            body: Request body (model object or None).
            accepts: Acceptable response content types.
            content_type: Request content type.
            return_type: Return type for deserialization (None for void).
            auth: Optional authenticator for operation-specific auth.

        Returns:
            ApiResult containing deserialized data, status code, raw body,
            and headers.

        Raises:
            ApiException: If the API call fails.
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
            elif content_type == 'text/plain':
                serialized_body = str(body)
            elif content_type == 'application/x-www-form-urlencoded':
                serialized_body = urlencode(body)
            else:
                serialized_body = self._object_serializer.serialize(body)

        response = self._api_client.send_request(method, url, headers, serialized_body)

        if response.status_code < 200 or response.status_code >= 300:
            self._throw_api_exception(response)

        data = None
        if return_type is not None and response.body:
            resp_content_type = ''
            for k, v in response.headers.items():
                if k.lower() == 'content-type':
                    resp_content_type = v.split(';')[0].strip()
                    break
            if resp_content_type and not resp_content_type.startswith('application/json'):
                data = response.body
            else:
                data = self._object_serializer.deserialize(response.body, return_type)

        return ApiResult(
            status_code=response.status_code,
            data=data,
            raw_body=response.body,
            headers=response.headers,
        )

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

        Args:
            method: HTTP method (GET, POST, PUT, DELETE, etc.).
            path: URL path (with path params already substituted).
            query_params: Query parameters.
            header_params: Custom header parameters.
            body: Request body (model object or None).
            accepts: Acceptable response content types.
            content_type: Request content type.
            return_type: Return type for deserialization (None for void).
            auth: Optional authenticator for operation-specific auth.

        Returns:
            Deserialized response or None.

        Raises:
            ApiException: If the API call fails.
        """
        return self._invoke_api_for_result(
            method,
            path,
            query_params,
            header_params,
            body,
            accepts,
            content_type,
            return_type,
            auth,
        ).data

    @staticmethod
    def _throw_api_exception(response: 'ApiResponse') -> None:
        """Throw the appropriate exception subclass for the given error response."""
        code = response.status_code
        message = f'API returned status code {code}'
        body = response.body

        if 400 <= code < 500:
            if code == 400:
                raise BadRequestException(reason=message, body=body)
            if code == 401:
                raise UnauthorizedException(reason=message, body=body)
            if code == 403:
                raise ForbiddenException(reason=message, body=body)
            if code == 404:
                raise NotFoundException(reason=message, body=body)
            if code == 409:
                raise ConflictException(reason=message, body=body)
            if code == 422:
                raise UnprocessableEntityException(reason=message, body=body)
            raise ClientException(status=code, reason=message, body=body)
        if code >= 500:
            if code == 500:
                raise InternalServerErrorException(reason=message, body=body)
            raise ServerException(status=code, reason=message, body=body)
        raise ApiException(status=code, reason=message, body=body)
