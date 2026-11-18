import asyncio
import logging
import threading
from typing import Any, Optional

import pytest
from opentelemetry import metrics, trace
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import InMemoryMetricReader
from opentelemetry.sdk.trace import ReadableSpan, TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter
from opentelemetry.trace import SpanKind, StatusCode

from petstore_client.api.base_api import BaseApi
from petstore_client.api_http_response import ApiHttpResponse
from petstore_client.call_hook import CallHook
from petstore_client.configuration import Configuration
from petstore_client.default_api_client import DefaultApiClient
from petstore_client.errors import (
    NetworkException,
    NetworkTimeoutException,
    NotFoundException,
)
from petstore_client.opentelemetry_call_hook import OpenTelemetryCallHook
from petstore_client.transport_options import TransportOptions

_JSON = {"content-type": "application/json"}
_OK = ApiHttpResponse(status_code=200, body="{}", headers=_JSON)


class _Transport(DefaultApiClient):
    """A transport that plays back replies (or raises them) and records what it sent."""

    def __init__(self, *replies: Any, max_retries: int = 0) -> None:
        super().__init__(TransportOptions.builder().max_retries(max_retries).build())
        self._replies = list(replies)
        self.sent: list[dict[str, str]] = []
        self.started = threading.Event()
        self.release = threading.Event()
        self.blocking = False

    def send_request(
        self,
        method: str,
        url: str,
        headers: dict[str, str],
        body: Any = None,
        no_redirect: bool = False,
        handle: Any = None,
        deadline: Optional[float] = None,
    ) -> ApiHttpResponse:
        self.sent.append(dict(headers))
        if self.blocking:
            self.started.set()
            self.release.wait(5)
        reply = self._replies.pop(0) if len(self._replies) > 1 else self._replies[0]
        if isinstance(reply, BaseException):
            raise reply
        assert isinstance(reply, ApiHttpResponse)
        return reply


class _Api(BaseApi):
    async def get(self, **extra: Any) -> Any:
        return await self._invoke_api_for_result(
            "GET",
            "/items/7",
            extra.pop("query_params", {}),
            {},
            None,
            ["application/json"],
            "application/json",
            extra.pop("return_type", None),
            operation_id="getItem",
            url_template="/items/{id}",
        )


class _Recorded:
    def __init__(self) -> None:
        self.spans = InMemorySpanExporter()
        self.tracer_provider = TracerProvider()
        self.tracer_provider.add_span_processor(SimpleSpanProcessor(self.spans))
        self.reader = InMemoryMetricReader()
        self.meter_provider = MeterProvider(metric_readers=[self.reader])

    def hook(self) -> OpenTelemetryCallHook:
        return OpenTelemetryCallHook(self.tracer_provider, self.meter_provider)

    def api(
        self,
        transport: DefaultApiClient,
        hook: Optional[CallHook] = None,
        **config: Any,
    ) -> _Api:
        return _Api(
            api_client=transport,
            config=Configuration(
                base_url="https://Example.test:8443/v1",
                call_hook=hook or self.hook(),
                **config,
            ),
        )

    def has_metrics(self) -> bool:
        data = self.reader.get_metrics_data()
        return data is not None and bool(data.resource_metrics)

    def finished(self) -> list[ReadableSpan]:
        return list(self.spans.get_finished_spans())

    def metric(self, name: str) -> Any:
        data = self.reader.get_metrics_data()
        assert data is not None
        found = [
            (scope, metric)
            for resource in data.resource_metrics
            for scope in resource.scope_metrics
            for metric in scope.metrics
            if metric.name == name
        ]
        assert len(found) == 1
        return found[0]


@pytest.fixture
def recorded() -> _Recorded:
    return _Recorded()


async def _fail(api: _Api, error: type, **extra: Any) -> BaseException:
    with pytest.raises(error) as exc_info:
        await api.get(**extra)
    return exc_info.value


class TestSpan:
    async def test_bhv_telemetry_001_the_span_is_named_with_the_operation_id_and_is_a_client_span(
        self, recorded: _Recorded
    ) -> None:
        await recorded.api(_Transport(_OK)).get()

        (span,) = recorded.finished()
        assert span.name == "getItem"
        assert span.kind == SpanKind.CLIENT

    async def test_bhv_telemetry_002_a_2xx_sets_the_attributes_and_leaves_the_status_unset(
        self, recorded: _Recorded
    ) -> None:
        await recorded.api(_Transport(_OK)).get()

        (span,) = recorded.finished()
        assert dict(span.attributes or {}) == {
            "http.request.method": "GET",
            "url.template": "/items/{id}",
            "server.address": "example.test",
            "http.response.status_code": 200,
        }
        assert span.status.status_code == StatusCode.UNSET

    @pytest.mark.parametrize("status", [404, 503])
    async def test_bhv_telemetry_003_an_error_status_sets_error_type_and_marks_the_span(
        self, recorded: _Recorded, status: int
    ) -> None:
        reply = ApiHttpResponse(status_code=status, body="{}", headers=_JSON)

        with pytest.raises(Exception):
            await recorded.api(_Transport(reply)).get()

        (span,) = recorded.finished()
        assert (span.attributes or {})["error.type"] == str(status)
        assert (span.attributes or {})["http.response.status_code"] == status
        assert span.status.status_code == StatusCode.ERROR

    @pytest.mark.parametrize(
        "reply,expected",
        [
            (NetworkTimeoutException("budget"), "timeout"),
            (NetworkException("refused"), "network_error"),
            (RuntimeError("boom"), "_OTHER"),
            (
                ApiHttpResponse(status_code=200, body='{"id": 1, ', headers=_JSON),
                "serialization_error",
            ),
        ],
    )
    async def test_bhv_telemetry_004_error_type_names_the_failure(
        self, recorded: _Recorded, reply: Any, expected: str
    ) -> None:
        with pytest.raises(Exception):
            await recorded.api(_Transport(reply)).get(return_type="object")

        (span,) = recorded.finished()
        assert (span.attributes or {})["error.type"] == expected
        assert span.status.status_code == StatusCode.ERROR

    async def test_bhv_telemetry_004_a_cancelled_call_is_cancelled(
        self, recorded: _Recorded
    ) -> None:
        transport = _Transport(_OK)
        transport.blocking = True
        task = asyncio.ensure_future(recorded.api(transport).get())
        while not transport.started.is_set():
            await asyncio.sleep(0.01)

        task.cancel()
        try:
            with pytest.raises(asyncio.CancelledError):
                await task
        finally:
            transport.release.set()

        (span,) = recorded.finished()
        assert (span.attributes or {})["error.type"] == "cancelled"
        assert span.status.status_code == StatusCode.ERROR

    async def test_bhv_telemetry_005_a_call_without_a_response_sets_no_status_code(
        self, recorded: _Recorded
    ) -> None:
        await _fail(
            recorded.api(_Transport(NetworkException("refused"))), NetworkException
        )

        (span,) = recorded.finished()
        assert "http.response.status_code" not in (span.attributes or {})

    async def test_bhv_telemetry_006_the_span_is_a_child_of_the_active_span_and_traceparent_names_it(
        self, recorded: _Recorded
    ) -> None:
        transport = _Transport(_OK)
        tracer = recorded.tracer_provider.get_tracer("caller")

        with tracer.start_as_current_span("caller") as parent:
            await recorded.api(transport).get()

        client = next(s for s in recorded.finished() if s.name == "getItem")
        assert client.parent is not None
        assert client.parent.span_id == parent.get_span_context().span_id
        assert client.context is not None
        assert [
            name for name in transport.sent[0] if name.lower() == "traceparent"
        ] == ["traceparent"]
        trace_id = f"{client.context.trace_id:032x}"
        span_id = f"{client.context.span_id:016x}"
        assert transport.sent[0]["traceparent"].split("-")[1:3] == [trace_id, span_id]

    async def test_bhv_telemetry_007_a_retried_call_records_one_span_and_one_count(
        self, recorded: _Recorded
    ) -> None:
        busy = ApiHttpResponse(
            status_code=503, body="{}", headers={**_JSON, "retry-after": "0"}
        )
        transport = _Transport(busy, _OK, max_retries=1)

        await recorded.api(transport).get()

        assert len(transport.sent) == 2
        assert len(recorded.finished()) == 1
        _, count = recorded.metric("http.client.request.count")
        assert [point.value for point in count.data.data_points] == [1]

    async def test_bhv_telemetry_013_the_span_ends_exactly_once_also_when_the_call_throws(
        self, recorded: _Recorded, caplog: pytest.LogCaptureFixture
    ) -> None:
        reply = ApiHttpResponse(status_code=404, body="{}", headers=_JSON)

        with caplog.at_level(logging.WARNING):
            await _fail(recorded.api(_Transport(reply)), NotFoundException)

        assert len(recorded.finished()) == 1
        assert "ended span" not in caplog.text

    async def test_bhv_telemetry_014_no_attribute_carries_a_query_a_header_value_or_a_credential(
        self, recorded: _Recorded
    ) -> None:
        api = recorded.api(
            _Transport(_OK), default_headers={"Authorization": "Bearer s3cret"}
        )

        await api.get(query_params={"token": "s3cret"})

        (span,) = recorded.finished()
        _, count = recorded.metric("http.client.request.count")
        for attributes in (
            dict(span.attributes or {}),
            dict(count.data.data_points[0].attributes or {}),
        ):
            assert set(attributes) == {
                "http.request.method",
                "url.template",
                "server.address",
                "http.response.status_code",
            }
            assert not any(
                "s3cret" in str(value) or "?" in str(value)
                for value in attributes.values()
            )


class TestMetrics:
    async def test_bhv_telemetry_008_the_count_is_incremented_once_with_the_span_attributes(
        self, recorded: _Recorded
    ) -> None:
        await recorded.api(_Transport(_OK)).get()

        (span,) = recorded.finished()
        _, count = recorded.metric("http.client.request.count")
        assert count.unit == "{request}"
        (point,) = count.data.data_points
        assert point.value == 1
        assert dict(point.attributes or {}) == dict(span.attributes or {})

    async def test_bhv_telemetry_009_the_duration_records_one_value_in_seconds(
        self, recorded: _Recorded
    ) -> None:
        await recorded.api(_Transport(_OK)).get()

        (span,) = recorded.finished()
        _, duration = recorded.metric("http.client.request.duration")
        assert duration.unit == "s"
        (point,) = duration.data.data_points
        assert point.count == 1
        assert 0 <= point.sum < 5
        assert dict(point.attributes or {}) == dict(span.attributes or {})

    async def test_bhv_telemetry_010_the_scope_is_the_package_and_its_version(
        self, recorded: _Recorded
    ) -> None:
        await recorded.api(_Transport(_OK)).get()

        (span,) = recorded.finished()
        scope, _ = recorded.metric("http.client.request.count")
        assert span.instrumentation_scope is not None
        for name, version in (
            (span.instrumentation_scope.name, span.instrumentation_scope.version),
            (scope.scope.name, scope.scope.version),
        ):
            assert name == "petstore-client"
            assert version == "1.0.0"


class TestProviders:
    async def test_bhv_telemetry_011_a_given_provider_is_used_instead_of_the_global_one(
        self, recorded: _Recorded, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        elsewhere = _Recorded()
        monkeypatch.setattr(trace, "get_tracer", elsewhere.tracer_provider.get_tracer)
        monkeypatch.setattr(metrics, "get_meter", elsewhere.meter_provider.get_meter)

        await recorded.api(_Transport(_OK)).get()
        assert len(recorded.finished()) == 1
        assert elsewhere.finished() == []
        assert not elsewhere.has_metrics()

        api = _Api(
            api_client=_Transport(_OK),
            config=Configuration(
                base_url="https://example.test", call_hook=OpenTelemetryCallHook()
            ),
        )
        await api.get()
        assert len(elsewhere.finished()) == 1
        assert elsewhere.has_metrics()
        assert len(recorded.finished()) == 1

    async def test_bhv_telemetry_012_with_no_provider_configured_the_call_is_unchanged(
        self,
    ) -> None:
        transport = _Transport(_OK)
        api = _Api(
            api_client=transport,
            config=Configuration(
                base_url="https://example.test", call_hook=OpenTelemetryCallHook()
            ),
        )

        result = await api.get()

        assert result.status_code == 200
        assert "traceparent" not in transport.sent[0]
