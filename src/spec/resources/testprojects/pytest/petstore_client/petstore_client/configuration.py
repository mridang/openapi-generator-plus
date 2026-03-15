from typing import ClassVar, Dict, Optional
from typing_extensions import Self


class Configuration:
    """Configuration for API clients.

    Holds settings that apply to all API requests such as the base URL,
    default headers, TLS options, proxy, timeout, and retry policy.
    """

    _default: ClassVar[Optional[Self]] = None

    def __init__(
        self,
        base_url: str = '/api/v3',
        *,
        debug: bool = False,
        verify_ssl: bool = True,
        ssl_ca_cert: Optional[str] = None,
        cert_file: Optional[str] = None,
        key_file: Optional[str] = None,
        proxy: Optional[str] = None,
        timeout: Optional[int] = None,
        retries: Optional[int] = None,
    ) -> None:
        # Base URL for all API requests.
        self.base_url: str = base_url

        # Headers to include in every API request. Use this for authentication
        # (e.g. Authorization header) and other custom headers.
        self.default_headers: Dict[str, str] = {}

        # Enable debug logging of HTTP requests and responses.
        self.debug: bool = debug

        # Enable SSL/TLS certificate verification.
        self.verify_ssl: bool = verify_ssl

        # Path to a CA certificate file for SSL/TLS verification.
        self.ssl_ca_cert: Optional[str] = ssl_ca_cert

        # Path to a client certificate file for mutual TLS authentication.
        self.cert_file: Optional[str] = cert_file

        # Path to a client private key file for mutual TLS authentication.
        self.key_file: Optional[str] = key_file

        # Proxy URL for all API requests.
        self.proxy: Optional[str] = proxy

        # Request timeout in seconds. None means no timeout.
        self.timeout: Optional[int] = timeout

        # Number of retry attempts for failed requests. None means no retries.
        self.retries: Optional[int] = retries

    @classmethod
    def get_default(cls) -> Self:
        """Return the default configuration instance, creating it lazily if needed."""
        if cls._default is None:
            cls._default = cls()
        return cls._default

    @classmethod
    def set_default(cls, configuration: Optional[Self]) -> None:
        """Set the default configuration instance."""
        cls._default = configuration
