from typing import Any, Dict, Optional, Protocol, runtime_checkable

from petstore_client.api_response import ApiResponse


@runtime_checkable
class ApiClient(Protocol):
    """Interface for API HTTP transport.

    Implementations handle the actual HTTP request/response cycle.
    The default implementation uses urllib3.
    """

    def send_request(self, method: str, url: str, headers: Dict[str, str], body: Any = None) -> ApiResponse:
        """Send an HTTP request and return the response.

        :param method: HTTP method (GET, POST, PUT, DELETE, etc.)
        :param url: Fully qualified URL
        :param headers: HTTP headers
        :param body: Request body (serialized JSON string, bytes, dict for multipart, or None)
        :return: ApiResponse containing status code, body, and headers
        """
        ...
