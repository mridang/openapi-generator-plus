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

    def test_empty_message_renders_the_default_text(self) -> None:
        err = ApiException(status_code=0, message="")

        assert str(err) == "Error message: the server returns an error"

    def test_empty_message_keeps_the_status_line_after_the_default_text(self) -> None:
        err = ApiException(status_code=502, message="")

        assert (
            str(err)
            == "Error message: the server returns an error\nHTTP status code: 502"
        )


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

    def test_defaults_status_code_to_0_when_no_http_response_was_received(self) -> None:
        assert ApiException().status_code == 0
        assert ApiException(message="connection reset").status_code == 0

    def test_bhv_errors_025_serialization_error_inherits_from_branded_root(
        self,
    ) -> None:
        err = SerializationException("boom")

        assert isinstance(err, OpenAPIException)
        assert issubclass(SerializationException, OpenAPIException)

    def test_oauth2_errors_inherit_from_branded_root_not_api_exception(self) -> None:
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

    def test_status_at_or_above_600_is_not_a_server_error(self) -> None:
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

    def test_bhv_errors_022_omits_empty_headers_and_an_empty_body(self) -> None:
        err = ApiException(
            status_code=502,
            message="bad gateway",
            response_headers={},
            response_body="",
        )

        assert str(err) == "bad gateway\nHTTP status code: 502"

    def test_omits_the_status_line_when_no_response_arrived(self) -> None:
        err = ApiException(status_code=0, message="boom")

        assert str(err) == "boom"

    def test_cuts_a_long_body_at_1_kib_and_counts_the_bytes_left_out(self) -> None:
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
    def test_message_has_three_forms(
        self,
        code: Any,
        description: Any,
        raw_body: str,
        expected: str,
    ) -> None:
        err = OAuth2ServerException(400, code, description, None, raw_body)

        assert str(err) == expected
        assert err.raw_body == raw_body
