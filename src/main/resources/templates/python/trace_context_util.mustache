"""Utility for injecting W3C Trace Context headers into outgoing API requests."""

from typing import Dict


def inject_trace_context(headers: Dict[str, str]) -> None:
    """Inject the current OpenTelemetry trace context into the given headers dict.

    If the OpenTelemetry API is not installed or no active span exists,
    this function does nothing.

    :param headers: mutable dict of request headers
    """
    try:
        from opentelemetry.propagate import inject  # type: ignore[import-not-found]

        inject(carrier=headers)
    except ImportError:
        pass
