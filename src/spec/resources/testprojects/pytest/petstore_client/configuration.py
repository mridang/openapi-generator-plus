from dataclasses import dataclass, field
from types import MappingProxyType
from typing import ClassVar, Dict, Mapping, Optional

from typing_extensions import Self


@dataclass(frozen=True)
class Configuration:
    """Configuration for API clients.

    Holds settings that apply to all API requests such as the base URL,
    default headers, TLS options, proxy, timeout, and retry policy.

    This class is immutable. Use :meth:`builder` to create instances::

        config = Configuration.builder() \\
            .base_url('https://api.example.com') \\
            .default_header('Authorization', 'Bearer token') \\
            .verify_ssl(False) \\
            .build()
    """

    base_url: str = '/api/v3'
    default_headers: Mapping[str, str] = field(default_factory=lambda: MappingProxyType({}))
    debug: bool = False
    verify_ssl: bool = True
    ssl_ca_cert: Optional[str] = None
    cert_file: Optional[str] = None
    key_file: Optional[str] = None
    proxy: Optional[str] = None
    timeout: Optional[int] = None
    retries: Optional[int] = None

    _default: ClassVar[Optional['Configuration']] = None

    def __post_init__(self) -> None:
        if not isinstance(self.default_headers, MappingProxyType):
            object.__setattr__(self, 'default_headers', MappingProxyType(dict(self.default_headers)))

    @classmethod
    def get_default(cls) -> 'Configuration':
        """Return the default configuration instance, creating it lazily if needed."""
        if cls._default is None:
            cls._default = cls()
        return cls._default

    @classmethod
    def set_default(cls, configuration: Optional[Self]) -> None:
        """Set the default configuration instance."""
        cls._default = configuration

    @classmethod
    def builder(cls) -> 'ConfigurationBuilder':
        """Create a new builder for constructing Configuration instances."""
        return ConfigurationBuilder()


class ConfigurationBuilder:
    """Builder for creating immutable :class:`Configuration` instances.

    Example::

        config = Configuration.builder() \\
            .base_url('https://api.example.com') \\
            .verify_ssl(False) \\
            .build()
    """

    def __init__(self) -> None:
        self._base_url: str = '/api/v3'
        self._default_headers: Dict[str, str] = {}
        self._debug: bool = False
        self._verify_ssl: bool = True
        self._ssl_ca_cert: Optional[str] = None
        self._cert_file: Optional[str] = None
        self._key_file: Optional[str] = None
        self._proxy: Optional[str] = None
        self._timeout: Optional[int] = None
        self._retries: Optional[int] = None

    def base_url(self, base_url: str) -> 'ConfigurationBuilder':
        """Set the base URL for all API requests."""
        self._base_url = base_url
        return self

    def default_header(self, name: str, value: str) -> 'ConfigurationBuilder':
        """Add a default header to include in every API request."""
        self._default_headers[name] = value
        return self

    def default_headers(self, headers: Dict[str, str]) -> 'ConfigurationBuilder':
        """Set all default headers to include in every API request."""
        self._default_headers.update(headers)
        return self

    def debug(self, debug: bool) -> 'ConfigurationBuilder':
        """Enable or disable debug logging."""
        self._debug = debug
        return self

    def verify_ssl(self, verify_ssl: bool) -> 'ConfigurationBuilder':
        """Enable or disable SSL/TLS certificate verification."""
        self._verify_ssl = verify_ssl
        return self

    def ssl_ca_cert(self, ssl_ca_cert: Optional[str]) -> 'ConfigurationBuilder':
        """Set the path to a CA certificate file for SSL/TLS verification."""
        self._ssl_ca_cert = ssl_ca_cert
        return self

    def cert_file(self, cert_file: Optional[str]) -> 'ConfigurationBuilder':
        """Set the path to a client certificate file for mutual TLS."""
        self._cert_file = cert_file
        return self

    def key_file(self, key_file: Optional[str]) -> 'ConfigurationBuilder':
        """Set the path to a client private key file for mutual TLS."""
        self._key_file = key_file
        return self

    def proxy(self, proxy: Optional[str]) -> 'ConfigurationBuilder':
        """Set the proxy URL for all API requests."""
        self._proxy = proxy
        return self

    def timeout(self, timeout: Optional[int]) -> 'ConfigurationBuilder':
        """Set the request timeout in seconds."""
        self._timeout = timeout
        return self

    def retries(self, retries: Optional[int]) -> 'ConfigurationBuilder':
        """Set the number of retry attempts for failed requests."""
        self._retries = retries
        return self

    def build(self) -> Configuration:
        """Build and return an immutable Configuration instance."""
        return Configuration(
            base_url=self._base_url,
            default_headers=self._default_headers,
            debug=self._debug,
            verify_ssl=self._verify_ssl,
            ssl_ca_cert=self._ssl_ca_cert,
            cert_file=self._cert_file,
            key_file=self._key_file,
            proxy=self._proxy,
            timeout=self._timeout,
            retries=self._retries,
        )
