from typing import Any, cast
import pytest

import petstore_client
import petstore_client.errors
from petstore_client.errors import (
    ApiException,
    BadRequestException,
    ClientException,
    ConflictException,
    ForbiddenException,
    InternalServerErrorException,
    NetworkException,
    NetworkTimeoutException,
    NotFoundException,
    OAuth2ServerException,
    OAuth2TokenException,
    SerializationException,
    ServerException,
    TooManyRequestsException,
    UnauthorizedException,
    UnprocessableEntityException,
    OpenAPIException,
)
from petstore_client.models import Category

ERROR_TREE = [
    "OpenAPIException",
    "ApiException",
    "ClientException",
    "ServerException",
    "BadRequestException",
    "UnauthorizedException",
    "ForbiddenException",
    "NotFoundException",
    "ConflictException",
    "UnprocessableEntityException",
    "TooManyRequestsException",
    "InternalServerErrorException",
    "NetworkException",
    "NetworkTimeoutException",
    "SerializationException",
    "OAuth2ServerException",
    "OAuth2TokenException",
]


class TestApiErrorShape:
    def test_bhv_errors_047_name_is_api_exception(self) -> None:
        err = ApiException(status_code=500, message="boom")

        assert type(err).__name__ == "ApiException"

    def test_bhv_errors_004_exposes_status_message_body_headers_error_body(
        self,
    ) -> None:
        err = ApiException(
            status_code=404,
            message="not found",
            response_headers={"content-type": "application/json"},
            response_body='{"id":7,"name":"missing"}',
            error_body=None,
        )

        assert err.status_code == 404
        assert err.message == "not found"
        assert err.response_body == '{"id":7,"name":"missing"}'
        assert err.response_headers is not None
        assert err.response_headers["content-type"] == "application/json"
        assert err.error_body is None

    def test_bhv_errors_021_none_headers_and_body_mark_transport_no_response(
        self,
    ) -> None:
        # apierror-responsebody-headers-nullable-split: None is distinct from an
        # empty header map / empty body so a pre-response transport failure can
        # be encoded.
        err = ApiException(
            status_code=0,
            message="connection reset",
            response_headers=None,
            response_body=None,
        )

        assert err.response_headers is None
        assert err.response_body is None

    def test_bhv_errors_019_is_an_exception_subclass(self) -> None:
        err = ApiException(status_code=500, message="boom")

        assert isinstance(err, Exception)
        assert str(err) != ""

    def test_none_message_renders_the_default_text(self) -> None:
        err = ApiException(status_code=0, message=None)

        assert str(err) == "Error message: the server returns an error"

    def test_bhv_errors_064_empty_message_renders_the_default_text(self) -> None:
        err = ApiException(status_code=0, message="")

        assert str(err) == "Error message: the server returns an error"

    def test_bhv_errors_051_empty_message_keeps_the_status_line_after_the_default_text(
        self,
    ) -> None:
        err = ApiException(status_code=502, message="")

        assert (
            str(err)
            == "Error message: the server returns an error\nHTTP status code: 502"
        )

    def test_bhv_errors_055_status_error_constructors_default_the_response_context_to_none(
        self,
    ) -> None:
        err = BadRequestException(message="bad request")

        assert err.response_headers is None
        assert err.response_body is None
        assert err.error_body is None


class TestExceptionHierarchy:
    def test_bhv_errors_018_typed_error_inherits_through_to_branded_root(self) -> None:
        # BadRequest -> ClientException -> ApiException -> OpenAPIException
        err = BadRequestException(message="bad request")

        assert isinstance(err, ClientException)
        assert isinstance(err, ApiException)
        assert isinstance(err, OpenAPIException)
        assert issubclass(BadRequestException, ClientException)
        assert issubclass(ClientException, ApiException)
        assert issubclass(ApiException, OpenAPIException)

    def test_bhv_errors_052_a_typed_client_error_is_caught_by_a_base_api_error_catch(
        self,
    ) -> None:
        with pytest.raises(ApiException) as exc_info:
            raise BadRequestException(message="bad request")

        assert isinstance(exc_info.value, OpenAPIException)
        assert exc_info.value.status_code == 400

    def test_bhv_errors_053_each_named_error_pins_its_own_status_code(self) -> None:
        pinned = [
            (BadRequestException, 400),
            (UnauthorizedException, 401),
            (ForbiddenException, 403),
            (NotFoundException, 404),
            (ConflictException, 409),
            (UnprocessableEntityException, 422),
            (TooManyRequestsException, 429),
        ]

        for error_type, status in pinned:
            assert error_type().status_code == status

    def test_bhv_errors_061_a_bad_request_error_is_a_client_error_an_api_error_and_an_openapi_error(
        self,
    ) -> None:
        err = BadRequestException(message="bad request")

        assert isinstance(err, ClientException)
        assert isinstance(err, ApiException)
        assert isinstance(err, OpenAPIException)

    def test_bhv_errors_020_network_errors_inherit_through_to_api_exception(
        self,
    ) -> None:
        # NetworkTimeoutException -> NetworkException -> ApiException
        err = NetworkTimeoutException(message="timed out")

        assert isinstance(err, NetworkException)
        assert isinstance(err, ApiException)
        assert isinstance(err, OpenAPIException)
        assert err.status_code == 0
        assert NetworkException(message="refused").status_code == 0

    def test_bhv_errors_084_defaults_status_code_to_0_when_no_http_response_was_received(
        self,
    ) -> None:
        assert ApiException().status_code == 0
        assert ApiException(message="connection reset").status_code == 0

    def test_bhv_errors_025_serialization_error_inherits_from_branded_root(
        self,
    ) -> None:
        err = SerializationException("boom")

        assert isinstance(err, OpenAPIException)
        assert issubclass(SerializationException, OpenAPIException)

    def test_bhv_errors_045_oauth2_errors_inherit_from_branded_root_not_api_exception(
        self,
    ) -> None:
        server = OAuth2ServerException(400, "invalid_grant", None, None, "{}")
        token = OAuth2TokenException("no access_token")

        assert isinstance(server, OpenAPIException)
        assert isinstance(token, OpenAPIException)
        assert not isinstance(server, ApiException)
        assert not isinstance(token, ApiException)


class TestCause:
    @pytest.mark.parametrize(
        "error_type",
        [
            ApiException,
            BadRequestException,
            UnauthorizedException,
            ForbiddenException,
            NotFoundException,
            ConflictException,
            UnprocessableEntityException,
            TooManyRequestsException,
            InternalServerErrorException,
            NetworkException,
            NetworkTimeoutException,
        ],
    )
    def test_every_constructor_chains_the_given_cause(self, error_type: Any) -> None:
        cause = ValueError("root cause")

        err = error_type(message="boom", cause=cause)

        assert err.__cause__ is cause

    def test_bhv_errors_003_cause_defaults_to_none(self) -> None:
        assert NotFoundException(message="missing").__cause__ is None


class TestErrorTreeExports:
    def test_bhv_errors_044_every_error_lives_in_the_errors_package(self) -> None:
        for name in ERROR_TREE:
            assert name in petstore_client.errors.__all__
            assert getattr(petstore_client.errors, name).__module__.startswith(
                "petstore_client.errors"
            )

    def test_package_root_exports_the_whole_error_tree(self) -> None:
        for name in ERROR_TREE:
            assert name in petstore_client.__all__
            assert getattr(petstore_client, name) is getattr(
                petstore_client.errors, name
            )


class TestFromResponse:
    @pytest.mark.parametrize(
        ("status", "expected"),
        [
            (400, BadRequestException),
            (401, UnauthorizedException),
            (403, ForbiddenException),
            (404, NotFoundException),
            (409, ConflictException),
            (422, UnprocessableEntityException),
            (429, TooManyRequestsException),
            (418, ClientException),
            (500, InternalServerErrorException),
            (503, ServerException),
            (302, ApiException),
        ],
    )
    def test_bhv_errors_006_maps_each_status_to_its_exception(
        self, status: int, expected: type
    ) -> None:
        err = ApiException.from_response(status, {"x-request-id": "abc"}, '{"code":7}')

        assert type(err) is expected
        assert isinstance(err, OpenAPIException)
        assert err.status_code == status
        assert err.response_headers == {"x-request-id": "abc"}
        assert err.response_body == '{"code":7}'
        assert err.error_body == {"code": 7}

    def test_bhv_errors_048_a_status_418_throws_client_exception(self) -> None:
        err = ApiException.from_response(418, None, None)

        assert type(err) is ClientException
        assert err.status_code == 418

    def test_bhv_errors_071_a_401_response_surfaces_as_an_unauthorized_error(
        self,
    ) -> None:
        err = ApiException.from_response(401, None, None)

        assert type(err) is UnauthorizedException
        assert err.status_code == 401

    def test_bhv_errors_058_a_400_response_maps_to_the_bad_request_error(self) -> None:
        err = ApiException.from_response(400, None, None)

        assert type(err) is BadRequestException
        assert err.status_code == 400

    def test_bhv_errors_059_a_403_response_maps_to_the_forbidden_error(self) -> None:
        err = ApiException.from_response(403, None, None)

        assert type(err) is ForbiddenException
        assert err.status_code == 403

    def test_bhv_errors_060_a_404_response_maps_to_the_not_found_error(self) -> None:
        err = ApiException.from_response(404, None, None)

        assert type(err) is NotFoundException
        assert err.status_code == 404

    def test_bhv_errors_066_a_429_response_becomes_a_too_many_requests_error_that_is_a_client_error(
        self,
    ) -> None:
        err = ApiException.from_response(429, None, None)

        assert type(err) is TooManyRequestsException
        assert isinstance(err, ClientException)
        assert err.status_code == 429

    def test_bhv_errors_067_a_409_response_becomes_a_conflict_error(self) -> None:
        err = ApiException.from_response(409, None, None)

        assert type(err) is ConflictException
        assert err.status_code == 409

    def test_bhv_errors_068_a_422_response_becomes_an_unprocessable_entity_error(
        self,
    ) -> None:
        err = ApiException.from_response(422, None, None)

        assert type(err) is UnprocessableEntityException
        assert err.status_code == 422

    def test_bhv_errors_069_a_500_response_becomes_an_internal_server_error(
        self,
    ) -> None:
        err = ApiException.from_response(500, None, None)

        assert type(err) is InternalServerErrorException
        assert err.status_code == 500

    def test_bhv_errors_070_a_502_response_becomes_a_server_error(self) -> None:
        err = ApiException.from_response(502, None, None)

        assert type(err) is ServerException
        assert err.status_code == 502

    def test_bhv_errors_056_status_at_or_above_600_is_not_a_server_error(self) -> None:
        # The 5xx arm is bounded at < 600: a status outside 400-599 yields the
        # base ApiException, never ServerException.
        err = ApiException.from_response(600, {"x-request-id": "abc"}, None)

        assert type(err) is ApiException
        assert not isinstance(err, ServerException)
        assert err.status_code == 600

    def test_bhv_errors_005_keeps_a_non_json_body_raw_without_an_error_body(
        self,
    ) -> None:
        err = ApiException.from_response(500, {}, "<html>oops</html>")

        assert type(err) is InternalServerErrorException
        assert err.response_headers == {}
        assert err.response_body == "<html>oops</html>"
        assert err.error_body is None

    def test_keeps_absent_response_headers_as_none(self) -> None:
        # None means no response arrived at all and is never coerced to an
        # empty dict, which would claim a response with no headers.
        err = ApiException.from_response(0, None, None)

        assert err.response_headers is None
        assert err.response_body is None

    def test_bhv_errors_008_parses_a_bom_prefixed_error_body(self) -> None:
        err = ApiException.from_response(400, {}, '\ufeff{"code": 7}')

        assert type(err) is BadRequestException
        assert err.error_body == {"code": 7}

    def test_bhv_errors_009_refuses_a_deeply_nested_error_body(self) -> None:
        # The error body is attacker-controlled, so parsing is depth-capped:
        # an over-deep payload leaves error_body None instead of exhausting
        # the interpreter stack.
        body = "[" * 2000 + "]" * 2000

        err = ApiException.from_response(400, {}, body)

        assert type(err) is BadRequestException
        assert err.response_body == body
        assert err.error_body is None


class TestApiErrorImmutability:
    def test_fields_are_read_only(self) -> None:
        err = ApiException(
            status_code=404,
            message="not found",
            response_headers={"content-type": "application/json"},
            response_body='{"id":7}',
            error_body=None,
        )

        # A caught exception's fields must not be reassignable -- the @property
        # getters have no setters, so assignment raises AttributeError.
        with pytest.raises(AttributeError):
            cast(Any, err).status_code = 500
        with pytest.raises(AttributeError):
            cast(Any, err).message = "tampered"
        with pytest.raises(AttributeError):
            cast(Any, err).response_body = "tampered"

        assert err.status_code == 404
        assert err.message == "not found"


class TestApiErrorTypedBody:
    def test_bhv_errors_013_get_typed_error_body_deserializes_body(self) -> None:
        err = ApiException(
            status_code=400,
            message="bad request",
            response_body='{"id":42,"name":"Dogs"}',
        )

        typed = err.get_typed_error_body(Category)
        assert isinstance(typed, Category)
        assert typed.id == 42
        assert typed.name == "Dogs"

    def test_bhv_errors_015_get_typed_error_body_returns_none_when_no_body(
        self,
    ) -> None:
        err = ApiException(status_code=500, message="oops", response_body=None)

        assert err.get_typed_error_body(Category) is None

    def test_bhv_errors_014_get_typed_error_body_ignores_extraneous_fields(
        self,
    ) -> None:
        err = ApiException(
            status_code=422,
            message="unprocessable",
            response_body='{"id":1,"name":"Cat","extra":"drop-me"}',
        )

        typed = err.get_typed_error_body(Category)
        assert isinstance(typed, Category)
        assert typed.id == 1
        assert typed.name == "Cat"


class TestApiErrorRendering:
    def test_bhv_errors_024_renders_the_message_status_headers_and_body_in_order(
        self,
    ) -> None:
        err = ApiException(
            status_code=404,
            message="not found",
            response_headers={"x-id": "7"},
            response_body='{"a":1}',
        )

        assert str(err) == (
            "not found\nHTTP status code: 404"
            '\nResponse headers: {"x-id":"7"}\nResponse body: ' + '{"a":1}'
        )

    def test_bhv_errors_050_response_headers_render_as_compact_sorted_json_with_unescaped_slashes(
        self,
    ) -> None:
        err = ApiException(
            status_code=302,
            message="moved",
            response_headers={
                "x-b": "2",
                "location": "https://example.com/a",
                "x-a": "1",
            },
        )

        assert str(err) == (
            "moved\nHTTP status code: 302"
            '\nResponse headers: {"location":"https://example.com/a","x-a":"1","x-b":"2"}'
        )

    def test_bhv_errors_022_omits_empty_headers_and_an_empty_body(self) -> None:
        err = ApiException(
            status_code=502,
            message="bad gateway",
            response_headers={},
            response_body="",
        )

        assert str(err) == "bad gateway\nHTTP status code: 502"

    def test_bhv_errors_074_omits_the_status_line_when_no_response_arrived(
        self,
    ) -> None:
        err = ApiException(status_code=0, message="boom")

        assert str(err) == "boom"

    def test_bhv_errors_065_cuts_a_long_body_at_1_kib_and_counts_the_bytes_left_out(
        self,
    ) -> None:
        err = ApiException(
            status_code=502, message="bad gateway", response_body="a" * 3000
        )

        assert str(err) == (
            "bad gateway\nHTTP status code: 502\nResponse body: "
            + "a" * 1024
            + "... (1976 more bytes)"
        )
        assert err.response_body == "a" * 3000

    def test_keeps_a_body_of_exactly_1_kib_whole(self) -> None:
        err = ApiException(status_code=500, message="boom", response_body="a" * 1024)

        assert str(err).endswith("a" * 1024)
        assert "more bytes" not in str(err)

    def test_bhv_errors_001_escapes_control_characters_in_the_rendered_body(
        self,
    ) -> None:
        err = ApiException(
            status_code=500, message="boom", response_body="a\r\nb\tc\x01\x7f"
        )

        assert (
            str(err)
            == "boom\nHTTP status code: 500\nResponse body: a\\r\\nb\\tc\\x01\\x7F"
        )
        assert err.response_body == "a\r\nb\tc\x01\x7f"

    def test_bhv_errors_023_masks_credential_headers_in_the_rendered_headers(
        self,
    ) -> None:
        headers = {
            "Authorization": "Bearer abc",
            "Proxy-Authorization": "Basic abc",
            "Cookie": "sid=abc",
            "Set-Cookie": "sid=abc",
            "WWW-Authenticate": 'Bearer realm="x"',
            "Proxy-Authenticate": 'Basic realm="x"',
            "X-Api-Key": "abc",
            "X-Session-Token": "abc",
            "X-Client-Secret": "abc",
            "X-Id": "7",
        }
        err = ApiException(status_code=401, message="no", response_headers=headers)

        rendered = str(err)
        assert "abc" not in rendered
        assert "realm" not in rendered
        assert '"X-Id":"7"' in rendered
        assert rendered.count("***") == 9
        assert err.response_headers is not None
        assert err.response_headers["Authorization"] == "Bearer abc"


class TestRequestContext:
    """F830: every API error names the request it answers."""

    @pytest.mark.parametrize(
        "status,expected",
        [
            (400, BadRequestException),
            (401, UnauthorizedException),
            (403, ForbiddenException),
            (404, NotFoundException),
            (409, ConflictException),
            (422, UnprocessableEntityException),
            (429, TooManyRequestsException),
            (418, ClientException),
            (500, InternalServerErrorException),
            (503, ServerException),
            (302, ApiException),
        ],
    )
    def test_bhv_errors_007_from_response_records_the_request(
        self, status: int, expected: type
    ) -> None:
        err = ApiException.from_response(status, {}, None, "GET", "/items/7")

        assert type(err) is expected
        assert err.request_method == "GET"
        assert err.request_path == "/items/7"

    @pytest.mark.parametrize(
        "make",
        [
            lambda: ApiException(
                status_code=500, request_method="PUT", request_path="/a"
            ),
            lambda: ClientException(
                status_code=400, request_method="PUT", request_path="/a"
            ),
            lambda: ServerException(
                status_code=500, request_method="PUT", request_path="/a"
            ),
            lambda: BadRequestException(request_method="PUT", request_path="/a"),
            lambda: UnauthorizedException(request_method="PUT", request_path="/a"),
            lambda: ForbiddenException(request_method="PUT", request_path="/a"),
            lambda: NotFoundException(request_method="PUT", request_path="/a"),
            lambda: ConflictException(request_method="PUT", request_path="/a"),
            lambda: UnprocessableEntityException(
                request_method="PUT", request_path="/a"
            ),
            lambda: TooManyRequestsException(request_method="PUT", request_path="/a"),
            lambda: InternalServerErrorException(
                request_method="PUT", request_path="/a"
            ),
            lambda: NetworkException(request_method="PUT", request_path="/a"),
            lambda: NetworkTimeoutException(request_method="PUT", request_path="/a"),
        ],
    )
    def test_bhv_errors_002_every_constructor_takes_the_request_context(
        self, make: Any
    ) -> None:
        err = make()

        assert err.request_method == "PUT"
        assert err.request_path == "/a"

    def test_bhv_errors_012_from_response_without_a_request_keeps_the_plain_message(
        self,
    ) -> None:
        err = ApiException.from_response(404, {}, None)

        assert err.request_method is None
        assert err.request_path is None
        assert str(err) == "API returned status code 404\nHTTP status code: 404"

    def test_the_request_is_unknown_by_default(self) -> None:
        err = ApiException(status_code=500, message="boom")

        assert err.request_method is None
        assert err.request_path is None
        assert str(err) == "boom\nHTTP status code: 500"

    def test_bhv_errors_011_the_message_names_the_request_before_the_status(
        self,
    ) -> None:
        err = ApiException.from_response(502, {}, "down", "POST", "/items")

        assert str(err) == (
            "API returned status code 502\nRequest: POST /items"
            "\nHTTP status code: 502\nResponse body: down"
        )

    def test_bhv_errors_026_a_query_string_is_stripped_from_the_recorded_path(
        self,
    ) -> None:
        err = NetworkException(message="boom")._with_request(
            "GET", "/items?token=secret"
        )

        assert err.request_path == "/items"
        assert "secret" not in str(err)


class TestFromResponseBodyShapes:
    def test_bhv_errors_010_a_scalar_json_body_becomes_the_error_body(self) -> None:
        err = ApiException.from_response(400, {}, '"just a string"')

        assert type(err) is BadRequestException
        assert err.error_body == "just a string"
        assert err.response_body == '"just a string"'

    def test_a_bom_prefixed_body_is_parsed_into_an_error_body(self) -> None:
        body = '﻿{"a":1}'

        err = ApiException.from_response(400, {}, body)

        assert type(err) is BadRequestException
        assert err.error_body == {"a": 1}
        assert err.response_body == body


class TestApiErrorTypedBodyFailures:
    def test_bhv_errors_017_a_body_that_does_not_fit_the_type_raises_a_serialization_exception(
        self,
    ) -> None:
        err = ApiException(
            status_code=400, message="bad request", response_body='{"id":"abc"}'
        )

        with pytest.raises(SerializationException) as excinfo:
            err.get_typed_error_body(Category)

        assert type(excinfo.value) is SerializationException

    def test_bhv_errors_016_an_empty_string_body_yields_no_typed_body(self) -> None:
        err = ApiException(status_code=400, message="bad request", response_body="")

        assert err.get_typed_error_body(Category) is None


class TestOAuth2ServerExceptionMessage:
    def test_bhv_errors_049_names_the_status_and_the_error_code(self) -> None:
        err = OAuth2ServerException(400, "invalid_grant", None, None, "{}")

        assert str(err) == "Token request failed with status 400: invalid_grant"

    @pytest.mark.parametrize(
        ("code", "description", "raw_body", "expected"),
        [
            (None, None, "oops", "Token request failed with status 400: oops"),
            (
                "invalid_grant",
                None,
                "{}",
                "Token request failed with status 400: invalid_grant",
            ),
            (
                "invalid_grant",
                "refresh token expired",
                "{}",
                "Token request failed with status 400: invalid_grant -- refresh token expired",
            ),
        ],
    )
    def test_bhv_errors_046_message_has_three_forms(
        self,
        code: Any,
        description: Any,
        raw_body: str,
        expected: str,
    ) -> None:
        err = OAuth2ServerException(400, code, description, None, raw_body)

        assert str(err) == expected
        assert err.raw_body == raw_body

    def test_bhv_authoauthtwotokenmgr_047_an_oauth2_server_error_without_a_code_names_the_status_and_the_raw_body(
        self,
    ) -> None:
        err = OAuth2ServerException(502, None, None, None, "Bad Gateway")

        assert str(err) == "Token request failed with status 502: Bad Gateway"


class TestErrorBehaviours:
    def test_bhv_errors_072_a_byte_order_mark_before_an_error_body_is_ignored(
        self,
    ) -> None:
        plain = ApiException.from_response(400, {}, '{"id": 7, "name": "Dogs"}')
        marked = ApiException.from_response(400, {}, '﻿{"id": 7, "name": "Dogs"}')

        typed = marked.get_typed_error_body(Category)

        assert typed == plain.get_typed_error_body(Category)
        assert typed is not None
        assert typed.id == 7

    def test_bhv_errors_073_an_error_body_nested_past_common_parser_limits_is_kept(
        self,
    ) -> None:
        depth = 150
        error = ApiException.from_response(400, {}, "[" * depth + "]" * depth)

        value: Any = error.error_body
        for _ in range(depth - 1):
            assert isinstance(value, list)
            assert len(value) == 1
            value = value[0]
        assert value == []

    def test_bhv_errors_075_a_rendered_body_is_never_cut_inside_a_multi_byte_character(
        self,
    ) -> None:
        error = ApiException(
            status_code=500, message="boom", response_body="a" + "é" * 600
        )

        text = str(error)

        assert "... (177 more bytes)" in text
        assert "�" not in text

    def test_bhv_errors_076_an_error_the_sdk_did_not_raise_is_not_one_of_its_own(
        self,
    ) -> None:
        assert not isinstance(ValueError("boom"), OpenAPIException)
        try:
            try:
                raise ValueError("inner")
            except ValueError as inner:
                raise RuntimeError("outer") from inner
        except RuntimeError as wrapped:
            assert not isinstance(wrapped, OpenAPIException)
            assert not isinstance(wrapped.__cause__, OpenAPIException)

    def test_bhv_errors_079_the_first_complete_request_attachment_is_kept(self) -> None:
        error = ApiException(status_code=500)._with_request("GET", "/pets?limit=2")
        error._with_request("POST", "/orders")

        assert error.request_method == "GET"
        assert error.request_path == "/pets"

    def test_an_incomplete_request_attachment_is_ignored(self) -> None:
        error = ApiException(status_code=500)._with_request("", "/pets")

        assert error.request_method is None
        assert error.request_path is None

    def test_bhv_errors_080_a_typed_http_error_is_caught_by_the_root_and_keeps_its_status(
        self,
    ) -> None:
        try:
            raise ApiException.from_response(404, {}, None)
        except OpenAPIException as caught:
            assert isinstance(caught, NotFoundException)
            assert caught.status_code == 404
