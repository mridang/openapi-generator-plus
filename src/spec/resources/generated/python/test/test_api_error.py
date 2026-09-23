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
    "InternalServerErrorException",
    "NetworkException",
    "NetworkTimeoutException",
    "SerializationException",
    "OAuth2ServerException",
    "OAuth2TokenException",
]


class TestApiErrorShape:
    def test_exposes_status_message_body_headers_error_body(self) -> None:
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

    def test_none_headers_and_body_mark_transport_no_response(self) -> None:
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

    def test_is_an_exception_subclass(self) -> None:
        err = ApiException(status_code=500, message="boom")

        assert isinstance(err, Exception)
        assert str(err) != ""


class TestExceptionHierarchy:
    def test_typed_error_inherits_through_to_branded_root(self) -> None:
        # BadRequest -> ClientException -> ApiException -> OpenAPIException
        err = BadRequestException(message="bad request")

        assert isinstance(err, ClientException)
        assert isinstance(err, ApiException)
        assert isinstance(err, OpenAPIException)
        assert issubclass(BadRequestException, ClientException)
        assert issubclass(ClientException, ApiException)
        assert issubclass(ApiException, OpenAPIException)

    def test_network_errors_inherit_through_to_api_exception(self) -> None:
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

    def test_serialization_error_inherits_from_branded_root(self) -> None:
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


class TestErrorTreeExports:
    def test_every_error_lives_in_the_errors_package(self) -> None:
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
            (418, ClientException),
            (500, InternalServerErrorException),
            (503, ServerException),
            (302, ApiException),
        ],
    )
    def test_maps_each_status_to_its_exception(
        self, status: int, expected: type
    ) -> None:
        err = ApiException.from_response(status, {"x-request-id": "abc"}, '{"code":7}')

        assert type(err) is expected
        assert isinstance(err, OpenAPIException)
        assert err.status_code == status
        assert err.response_headers == {"x-request-id": "abc"}
        assert err.response_body == '{"code":7}'
        assert err.error_body == {"code": 7}

    def test_keeps_a_non_json_body_raw_without_an_error_body(self) -> None:
        err = ApiException.from_response(500, {}, "<html>oops</html>")

        assert type(err) is InternalServerErrorException
        assert err.response_headers == {}
        assert err.response_body == "<html>oops</html>"
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
    def test_get_typed_error_body_deserializes_body(self) -> None:
        err = ApiException(
            status_code=400,
            message="bad request",
            response_body='{"id":42,"name":"Dogs"}',
        )

        typed = err.get_typed_error_body(Category)
        assert isinstance(typed, Category)
        assert typed.id == 42
        assert typed.name == "Dogs"

    def test_get_typed_error_body_returns_none_when_no_body(self) -> None:
        err = ApiException(status_code=500, message="oops", response_body=None)

        assert err.get_typed_error_body(Category) is None

    def test_get_typed_error_body_ignores_extraneous_fields(self) -> None:
        err = ApiException(
            status_code=422,
            message="unprocessable",
            response_body='{"id":1,"name":"Cat","extra":"drop-me"}',
        )

        typed = err.get_typed_error_body(Category)
        assert isinstance(typed, Category)
        assert typed.id == 1
        assert typed.name == "Cat"
