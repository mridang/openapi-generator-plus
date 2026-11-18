"""Integration tests for the Store API endpoints."""

import datetime as dt
import pytest
import threading
from datetime import datetime, timezone
from urllib.parse import parse_qs, urlsplit
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Any, Dict
from petstore_client.api_http_response import ApiHttpResponse
from pydantic import SecretStr
from petstore_client.api.options.get_by_swatch_options import GetBySwatchOptions
from petstore_client.api.options.search_store_options import SearchStoreOptions
from petstore_client.api.store_api import StoreApi
from petstore_client.configuration import Configuration
from petstore_client.default_api_client import DefaultApiClient
from petstore_client.transport_options import TransportOptions
from petstore_client.errors import OpenAPIException
from petstore_client.errors import SerializationException
from petstore_client.errors.bad_gateway_exception import BadGatewayException
from petstore_client.errors.bad_request_exception import BadRequestException
from petstore_client.errors.client_exception import ClientException
from petstore_client.errors.conflict_exception import ConflictException
from petstore_client.errors.forbidden_exception import ForbiddenException
from petstore_client.errors.server_exception import ServerException
from petstore_client.errors.unauthorized_exception import UnauthorizedException
from petstore_client.errors.unprocessable_entity_exception import (
    UnprocessableEntityException,
)
from petstore_client.errors.internal_server_error_exception import (
    InternalServerErrorException,
)
from petstore_client.errors.not_found_exception import NotFoundException
from petstore_client.models.account import Account
from petstore_client.models.availability import Availability
from petstore_client.models.category import Category
from petstore_client.models.priority import Priority
from petstore_client.models.reservation import Reservation
from petstore_client.models.reservation_receipt import ReservationReceipt
from petstore_client.models.swatch import Swatch
from petstore_client.models.order import Order, OrderStatusEnum
from petstore_client.models.photo_metadata import PhotoMetadata
from petstore_client.models.photo_metadata_location import PhotoMetadataLocation


class TestStoreApi:
    """Test suite for StoreApi operations."""

    @pytest.fixture(autouse=True)
    def setup(self, api_base_url: Any) -> None:
        config = (
            Configuration.builder()
            .base_url(api_base_url)
            .default_header("Authorization", "Bearer test-token")
            .build()
        )
        self.api = StoreApi(config=config)

    async def test_bhv_operations_102_get_inventory(self) -> None:
        result = await self.api.get_inventory()

        assert isinstance(result, dict)

    async def test_bhv_misc_013_a_no_parameter_operation_returns_its_result(
        self,
    ) -> None:
        api, _ = _create_mock_server(200, "application/json", '{"available":3}')
        assert await api.get_inventory() == {"available": 3}

    async def test_bhv_operations_117_place_order(self) -> None:
        order = Order(
            id=1,
            petId=12345,
            quantity=1,
            shipDate=datetime.now(timezone.utc),
            status=OrderStatusEnum.PLACED,
            complete=False,
        )

        result = await self.api.place_order(order)

        assert result is not None
        assert result.id is not None

    async def test_bhv_operations_126_place_order_without_an_order_sends_no_body(
        self,
    ) -> None:
        client = _RecordingApiClient()
        api = StoreApi(
            api_client=client,
            config=Configuration.builder().base_url("http://localhost").build(),
        )
        await api.place_order_with_http_info()
        assert client.body is None
        assert "Content-Type" not in client.headers

    async def test_bhv_operations_103_get_inventory_with_http_info(self) -> None:
        result = await self.api.get_inventory_with_http_info()

        assert result is not None
        assert 200 <= result.status_code < 300
        assert result.data is not None

    async def test_bhv_operations_119_place_order_with_http_info(self) -> None:
        order = Order(
            id=1,
            petId=12345,
            quantity=1,
            shipDate=datetime.now(timezone.utc),
            status=OrderStatusEnum.PLACED,
            complete=False,
        )

        result = await self.api.place_order_with_http_info(order)

        assert result is not None
        assert 200 <= result.status_code < 300
        assert result.data is not None

    async def test_bhv_operations_104_get_order_by_id(self) -> None:
        result = await self.api.get_order_by_id(1)

        assert result is not None
        assert result.id is not None

    async def test_bhv_operations_105_get_order_by_id_with_http_info(self) -> None:
        result = await self.api.get_order_by_id_with_http_info(1)

        assert result is not None
        assert 200 <= result.status_code < 300
        assert result.data is not None

    async def test_bhv_operations_099_delete_order(self) -> None:
        await self.api.delete_order(1)

        assert True

    async def test_bhv_operations_131_a_request_with_no_body_sends_no_content_type(
        self,
    ) -> None:
        client = _RecordingApiClient()
        api = StoreApi(
            api_client=client,
            config=Configuration.builder().base_url("http://localhost").build(),
        )
        await api.delete_order_with_http_info(1)
        assert client.body is None
        assert "Content-Type" not in client.headers

    async def test_bhv_operations_132_a_missing_required_body_names_the_first_required_field(
        self,
    ) -> None:
        api = StoreApi(
            config=Configuration.builder().base_url("http://localhost").build()
        )
        missing: Any = None
        with pytest.raises(
            ValueError,
            match="Missing the required parameter 'account' when calling StoreApi.create_account",
        ):
            await api.create_account(missing)


class _RecordingApiClient:
    """In-memory transport that records the outbound request."""

    def __init__(self) -> None:
        self.headers: Dict[str, str] = {}
        self.body: Any = None
        self.reply = "{}"

    def send_request(
        self,
        method: str,
        url: str,
        headers: Dict[str, str],
        body: Any = None,
        no_redirect: bool = False,
    ) -> ApiHttpResponse:
        self.headers = headers
        self.body = body
        return ApiHttpResponse(
            status_code=200,
            body=self.reply,
            headers={"content-type": "application/json"},
        )


def _create_mock_server(
    status: int, content_type: str, body: str
) -> tuple[StoreApi, HTTPServer]:
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.end_headers()
            self.wfile.write(body.encode("utf-8"))

        def do_POST(self) -> None:
            self.do_GET()

        def do_DELETE(self) -> None:
            self.do_GET()

        def log_message(self, format: str, *args: object) -> None:
            pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.handle_request)
    thread.daemon = True
    thread.start()

    config = Configuration.builder().base_url(f"http://127.0.0.1:{port}").build()
    api = StoreApi(
        api_client=DefaultApiClient(TransportOptions.builder().max_retries(0).build()),
        config=config,
    )
    return api, server


class TestStoreApiErrorHandling:
    """Test suite for StoreApi error handling."""

    async def test_bhv_operations_106_get_order_not_found(self) -> None:
        api, server = _create_mock_server(
            404, "application/json", '{"message":"Order not found"}'
        )
        with pytest.raises(NotFoundException):
            await api.get_order_by_id(99999)

    async def test_bhv_operations_118_place_order_server_error(self) -> None:
        api, server = _create_mock_server(
            500, "application/json", '{"message":"Internal server error"}'
        )
        order = Order(
            id=1, petId=12345, quantity=1, status=OrderStatusEnum.PLACED, complete=False
        )
        with pytest.raises(InternalServerErrorException):
            await api.place_order(order)

    async def test_bhv_operations_100_delete_order_not_found(self) -> None:
        api, server = _create_mock_server(
            404, "application/json", '{"message":"Order not found"}'
        )
        with pytest.raises(NotFoundException):
            await api.delete_order(99999)

    @pytest.mark.parametrize(
        ("status", "error_class"),
        [
            (400, BadRequestException),
            (401, UnauthorizedException),
            (403, ForbiddenException),
            (409, ConflictException),
            (418, ClientException),
            (422, UnprocessableEntityException),
            (502, BadGatewayException),
        ],
    )
    async def test_bhv_operations_110_get_order_by_id_picks_the_status_specific_error_class(
        self, status: int, error_class: type
    ) -> None:
        api, _ = _create_mock_server(
            status, "application/json", '{"message":"refused"}'
        )
        with pytest.raises(OpenAPIException) as exc_info:
            await api.get_order_by_id(1)
        assert type(exc_info.value) is error_class

    @pytest.mark.parametrize(
        ("status", "error_class"),
        [
            (400, BadRequestException),
            (403, ForbiddenException),
            (404, NotFoundException),
            (409, ConflictException),
            (418, ClientException),
            (500, InternalServerErrorException),
            (507, ServerException),
        ],
    )
    async def test_bhv_operations_138_delete_order_picks_the_status_specific_error_class(
        self, status: int, error_class: type
    ) -> None:
        api, _ = _create_mock_server(
            status, "application/json", '{"message":"refused"}'
        )
        with pytest.raises(OpenAPIException) as exc_info:
            await api.delete_order(1)
        assert type(exc_info.value) is error_class

    async def test_bhv_operations_127_a_malformed_success_body_fails_loud_with_a_serialization_error(
        self,
    ) -> None:
        api, _ = _create_mock_server(200, "application/json", '{"id":1,"name":')
        with pytest.raises(SerializationException):
            await api.get_order_by_id(1)

    async def test_bhv_operations_130_a_malformed_json_success_body_propagates_the_deserialization_error(
        self,
    ) -> None:
        api, _ = _create_mock_server(200, "application/json", "{ not json")
        with pytest.raises(SerializationException):
            await api.get_inventory()

    async def test_bhv_operations_136_a_bare_json_null_body_throws_an_api_error_naming_the_operation(
        self,
    ) -> None:
        from petstore_client.errors import ApiException

        api, _ = _create_mock_server(200, "application/json", "null")
        with pytest.raises(ApiException) as exc_info:
            await api.get_order_by_id(1)
        assert "Expected a response body for get_order_by_id but received none" in str(
            exc_info.value.message or ""
        )


class TestStoreApiNestedContainer:
    """nested-container-deserialize: an operation returning array-of-map-of-model
    must decode the leaves into typed Category instances, not raw dicts."""

    async def test_bhv_operations_101_get_grouped_categories_decodes_typed_leaves(
        self,
    ) -> None:
        body = '[{"a":{"id":1,"name":"Dogs"}},{"b":{"id":2,"name":"Cats"}}]'
        api, server = _create_mock_server(200, "application/json", body)

        result = await api.get_grouped_categories()

        assert isinstance(result[0]["a"], Category)
        assert result[0]["a"].id == 1
        assert result[0]["a"].name == "Dogs"


def _recording_server(
    reply: str, content_type: str = "application/json"
) -> tuple[StoreApi, dict[str, Any]]:
    seen: dict[str, Any] = {}

    class Handler(BaseHTTPRequestHandler):
        def _answer(self) -> None:
            length = int(self.headers.get("Content-Length", 0))
            seen["method"] = self.command
            seen["path"] = self.path
            seen["headers"] = {
                name.lower(): value for name, value in self.headers.items()
            }
            seen["body"] = self.rfile.read(length).decode() if length else ""
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.end_headers()
            self.wfile.write(reply.encode("utf-8"))

        def do_GET(self) -> None:
            self._answer()

        def do_POST(self) -> None:
            self._answer()

        def log_message(self, format: str, *args: object) -> None:
            pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.handle_request)
    thread.daemon = True
    thread.start()
    config = Configuration.builder().base_url(f"http://127.0.0.1:{port}").build()
    return StoreApi(config=config), seen


class TestStoreApiFixtureOperations:
    """Operations whose only purpose is to pin the generated return type and
    request body: the reply is served by a local listener."""

    async def test_bhv_operations_124_get_order_by_tracking_id_puts_the_lowercase_uuid_in_the_path(
        self,
    ) -> None:
        import uuid

        api, seen = _recording_server('{"id":1}')

        await api.get_order_by_tracking_id(
            uuid.UUID("E621E1F8-C36C-495A-93FC-0C247A3E6E5F")
        )

        assert (
            seen["path"]
            == "/store/order/by-tracking/e621e1f8-c36c-495a-93fc-0c247a3e6e5f"
        )

    async def test_bhv_operations_098_create_account_sends_the_password_and_keeps_the_read_only_id(
        self,
    ) -> None:
        import json

        api, seen = _recording_server('{"id":7,"username":"u"}')

        account = await api.create_account(
            Account(username="u", password=SecretStr("p"))
        )

        assert seen["method"] == "POST"
        assert seen["path"] == "/store/account"
        assert seen["headers"]["content-type"] == "application/json"
        assert json.loads(seen["body"]) == {"username": "u", "password": "p"}
        assert account.id == 7

    async def test_bhv_operations_129_reserve_stock_decodes_a_200_body_as_the_existing_reservation(
        self,
    ) -> None:
        api, _ = _recording_server('{"reservationId":31,"quantity":4}')

        result = await api.reserve_stock()

        assert isinstance(result.actual_instance, Reservation)
        assert result.actual_instance.reservation_id == 31

    async def test_bhv_operations_154_a_201_body_that_only_fits_the_200_schema_is_refused(
        self,
    ) -> None:
        api, _ = _create_mock_server(
            201, "application/json", '{"reservationId":31,"quantity":4}'
        )

        with pytest.raises(SerializationException):
            await api.reserve_stock()

    async def test_bhv_operations_148_an_operation_uses_the_first_scheme_of_its_security_requirement_the_client_holds(
        self,
    ) -> None:
        from petstore_client.auth.basic_authenticator import BasicAuthenticator
        from petstore_client.auth.bearer_authenticator import BearerAuthenticator

        bearer = BearerAuthenticator("http://localhost", "tok")
        basic = BasicAuthenticator("http://localhost", "user", "pa" + "ss")
        config = Configuration.builder().base_url("http://localhost").build()
        client = _RecordingApiClient()

        only_bearer = StoreApi(
            api_client=client,
            config=config,
            scheme_authenticators={"petStoreBearer": bearer},
        )
        await only_bearer.delete_order(1)
        assert client.headers["Authorization"] == "Bearer tok"

        both = StoreApi(
            api_client=client,
            config=config,
            scheme_authenticators={"petStoreBearer": bearer, "petStoreBasic": basic},
        )
        await both.delete_order(1)
        assert client.headers["Authorization"].startswith("Basic ")

    async def test_bhv_operations_153_an_operation_with_no_chosen_server_is_sent_to_the_client_base_url(
        self,
    ) -> None:
        api, seen = _recording_server('{"id":1}')

        await api.get_order_by_id(1)

        assert seen["path"] == "/store/order/1"

    async def test_bhv_operations_113_get_swatch_decodes_a_bare_enum(self) -> None:
        api, _ = _recording_server('"green"')

        assert await api.get_swatch() == Swatch.GREEN

    async def test_bhv_operations_114_get_swatch_groups_decodes_an_array_of_enum_maps(
        self,
    ) -> None:
        api, _ = _recording_server('[{"a":"red"},{"b":"blue"}]')

        assert await api.get_swatch_groups() == [{"a": Swatch.RED}, {"b": Swatch.BLUE}]

    async def test_bhv_operations_115_get_timestamp_groups_decodes_date_time_leaves(
        self,
    ) -> None:
        api, _ = _recording_server('[{"at":"2024-01-02T03:04:05.000+00:00"}]')

        result = await api.get_timestamp_groups()

        assert result[0]["at"] == datetime(2024, 1, 2, 3, 4, 5, tzinfo=timezone.utc)

    async def test_bhv_operations_108_get_department_decodes_the_mutually_recursive_models(
        self,
    ) -> None:
        body = '{"name":"d","lead":{"name":"e","department":{"name":"d2"}}}'
        api, _ = _recording_server(body)

        department = await api.get_department()

        assert department.lead is not None
        assert department.lead.name == "e"
        assert department.lead.department is not None
        assert department.lead.department.name == "d2"

    async def test_bhv_operations_116_get_tree_decodes_the_self_recursive_model(
        self,
    ) -> None:
        api, _ = _recording_server(
            '{"value":"a","child":{"value":"b","child":{"value":"c"}}}'
        )

        tree = await api.get_tree()

        assert tree.child is not None
        assert tree.child.child is not None
        assert tree.child.child.value == "c"

    async def test_bhv_operations_123_get_defaults_applies_the_exact_typed_schema_defaults(
        self,
    ) -> None:
        from decimal import Decimal

        api, _ = _recording_server("{}")

        defaults = await api.get_defaults()

        assert defaults.retries == 3
        assert defaults.label == "untitled"
        assert defaults.ratio == Decimal("1.5")
        assert defaults.scale == 0.25
        assert defaults.weight == 2.0
        assert defaults.ceiling == 9000000000

    async def test_bhv_operations_112_get_stock_item_decodes_the_int_enum_and_the_nested_arrays(
        self,
    ) -> None:
        api, _ = _recording_server('{"priority":2,"matrix":[[1,2],[3]]}')

        item = await api.get_stock_item()

        assert item.priority == Priority.NUMBER_2
        assert item.availability == Availability.ON_HOLD
        assert item.matrix == [[1, 2], [3]]

    async def test_bhv_operations_109_get_matrix_decodes_an_array_of_integer_arrays(
        self,
    ) -> None:
        api, _ = _recording_server("[[1,2],[3]]")

        result = await api.get_matrix()

        assert result == [[1, 2], [3]]
        assert all(isinstance(number, int) for row in result for number in row)

    async def test_bhv_operations_120_reserve_stock_decodes_a_201_body_as_the_new_receipt(
        self,
    ) -> None:
        api, _ = _create_mock_server(
            201,
            "application/json",
            '{"receiptCode":"R-1","expiresAt":"2030-01-01T00:00:00Z"}',
        )

        result = await api.reserve_stock()

        assert isinstance(result.actual_instance, ReservationReceipt)
        assert result.actual_instance.receipt_code == "R-1"

    async def test_bhv_misc_114_a_success_response_is_decoded_for_the_status_actually_received(
        self,
    ) -> None:
        api, _ = _create_mock_server(
            201,
            "application/json",
            '{"receiptCode":"R-1","expiresAt":"2030-01-01T00:00:00Z"}',
        )

        result = await api.reserve_stock()

        assert isinstance(result.actual_instance, ReservationReceipt)
        assert result.actual_instance.receipt_code == "R-1"

    async def test_bhv_operations_122_get_by_swatch_sends_the_enum_wire_value_in_path_query_and_header(
        self,
    ) -> None:
        api, seen = _recording_server('{"id":1,"name":"echo"}')

        await api.get_by_swatch(
            Swatch.RED,
            GetBySwatchOptions(query_swatch=Swatch.GREEN, preferred_swatch=Swatch.BLUE),
        )

        url = urlsplit(seen["path"])
        assert url.path == "/store/by-swatch/red"
        assert parse_qs(url.query)["querySwatch"] == ["green"]
        assert seen["headers"]["preferred-swatch"] == "blue"


def _capture_server() -> tuple[StoreApi, dict[str, Any]]:
    seen: dict[str, Any] = {}

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            seen["path"] = self.path
            seen["x_filter"] = self.headers.get("X-Filter")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b"[]")

        def log_message(self, format: str, *args: object) -> None:
            pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.handle_request)
    thread.daemon = True
    thread.start()
    config = Configuration.builder().base_url(f"http://127.0.0.1:{port}").build()
    return StoreApi(config=config), seen


class TestStoreApiContentParameters:
    """A `content: application/json` parameter goes through the SDK's own
    object serializer in every location."""

    async def test_bhv_operations_155_an_operation_sends_its_query_parameters_in_spec_order(
        self,
    ) -> None:
        api, seen = _recording_server("[]")

        await api.search_store(SearchStoreOptions(labels={"red"}, at=dt.time(10, 30)))

        assert list(parse_qs(urlsplit(seen["path"]).query).keys()) == ["labels", "at"]

    async def test_bhv_operations_097_query_content_uses_the_wire_form(self) -> None:
        api, seen = _capture_server()
        photo = PhotoMetadata(
            caption="a<b>&c",
            takenAt=datetime(2024, 1, 2, 3, 4, 5, tzinfo=timezone.utc),
            location=PhotoMetadataLocation(lat=1.5),
        )

        await api.search_store(SearchStoreOptions(q=photo))

        query = parse_qs(urlsplit(seen["path"]).query)
        assert query["q"] == [
            '{"caption":"a<b>&c","takenAt":"2024-01-02T03:04:05.000+00:00","location":{"lat":1.5}}'
        ]

    async def test_bhv_operations_121_header_content_is_sent_not_ignored(self) -> None:
        api, seen = _capture_server()

        await api.search_store(SearchStoreOptions(x_filter={"k": "v"}))

        assert seen["x_filter"] == '{"k":"v"}'

    async def test_bhv_serialization_009_content_that_cannot_be_serialized_sends_no_request(
        self,
    ) -> None:
        api, seen = _capture_server()

        with pytest.raises(SerializationException):
            await api.search_store(SearchStoreOptions(x_filter={"k": float("nan")}))  # type: ignore[dict-item]

        assert seen == {}

    async def test_bhv_operations_139_time_parameter_keeps_a_non_zero_millisecond_part(
        self,
    ) -> None:
        api, seen = _capture_server()

        await api.search_store(SearchStoreOptions(at=dt.time(10, 30, 0, 123456)))

        assert parse_qs(urlsplit(seen["path"]).query)["at"] == ["10:30:00.123"]

    async def test_bhv_operations_107_time_parameter_drops_a_zero_fraction(
        self,
    ) -> None:
        api, seen = _capture_server()

        await api.search_store(SearchStoreOptions(at=dt.time(10, 30, 0)))

        assert parse_qs(urlsplit(seen["path"]).query)["at"] == ["10:30:00"]

    async def test_bhv_operations_128_an_unserializable_content_query_parameter_sends_no_request(
        self,
    ) -> None:
        api, seen = _capture_server()
        photo = PhotoMetadata(location=PhotoMetadataLocation(lat=float("nan")))

        with pytest.raises(SerializationException):
            await api.search_store(SearchStoreOptions(q=photo))

        assert seen == {}
