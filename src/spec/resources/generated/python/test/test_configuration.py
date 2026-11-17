import pytest
from types import MappingProxyType

from petstore_client.configuration import Configuration, ConfigurationBuilder
from petstore_client.server_configuration import ServerConfiguration, ServerVariable


class TestConfigurationDefaults:
    def test_default_constructor_uses_spec_base_url(self) -> None:
        config = Configuration()

        assert config.base_url == "/api/v3"
        assert len(config.default_headers) == 0

    def test_bhv_config_005_builder_produces_correct_defaults(self) -> None:
        config = Configuration.builder().build()

        assert config.base_url == "/api/v3"
        assert len(config.default_headers) == 0

    def test_bhv_config_007_builder_returns_configuration_builder_instance(
        self,
    ) -> None:
        builder = Configuration.builder()

        assert isinstance(builder, ConfigurationBuilder)


class TestConfigurationBuilder:
    def test_bhv_config_011_builder_sets_base_url(self) -> None:
        config = Configuration.builder().base_url("https://custom.example.com").build()

        assert config.base_url == "https://custom.example.com"

    def test_bhv_config_013_builder_sets_single_default_header(self) -> None:
        config = (
            Configuration.builder()
            .default_header("Authorization", "Bearer token123")
            .build()
        )

        assert config.default_headers["Authorization"] == "Bearer token123"

    def test_bhv_config_012_builder_sets_multiple_default_headers(self) -> None:
        config = (
            Configuration.builder()
            .default_headers(
                {
                    "Authorization": "Bearer token123",
                    "X-Custom": "value",
                }
            )
            .build()
        )

        assert config.default_headers["Authorization"] == "Bearer token123"
        assert config.default_headers["X-Custom"] == "value"

    def test_bhv_config_002_builder_accumulates_headers(self) -> None:
        config = (
            Configuration.builder()
            .default_header("X-First", "one")
            .default_header("X-Second", "two")
            .default_headers({"X-Third": "three"})
            .build()
        )

        assert len(config.default_headers) == 3
        assert config.default_headers["X-First"] == "one"
        assert config.default_headers["X-Second"] == "two"
        assert config.default_headers["X-Third"] == "three"

    def test_bhv_config_004_builder_merges_single_and_bulk_headers(self) -> None:
        config = (
            Configuration.builder()
            .default_header("X-First", "one")
            .default_headers({"X-Second": "two"})
            .default_header("X-Third", "three")
            .build()
        )

        assert len(config.default_headers) == 3
        assert config.default_headers["X-First"] == "one"
        assert config.default_headers["X-Second"] == "two"
        assert config.default_headers["X-Third"] == "three"

    def test_bhv_config_006_builder_produces_independent_instances(self) -> None:
        builder = Configuration.builder().base_url("https://example.com")
        first = builder.build()
        second = builder.build()

        assert first is not second
        assert first.base_url == second.base_url

    def test_bhv_config_010_builder_sets_all_fields(self) -> None:
        config = (
            Configuration.builder()
            .base_url("https://api.example.com")
            .default_header("Authorization", "Bearer token")
            .default_headers({"X-Custom": "value"})
            .build()
        )

        assert config.base_url == "https://api.example.com"
        assert config.default_headers["Authorization"] == "Bearer token"
        assert config.default_headers["X-Custom"] == "value"

    def test_bhv_config_003_builder_is_fluent(self) -> None:
        builder = Configuration.builder()

        assert builder is builder.base_url("https://example.com")
        assert builder is builder.default_header("X-Key", "value")
        assert builder is builder.default_headers({"X-Other": "val"})

        server = ServerConfiguration(url_template="https://example.com")
        assert builder is builder.server(server)


class TestConfigurationServerResolution:
    def test_bhv_config_008_builder_server_resolves_url(self) -> None:
        server = ServerConfiguration(
            url_template="https://{env}.example.com/api/{version}",
            description="Test server",
            variables={
                "env": ServerVariable(
                    default_value="api",
                    enum_values=["api", "staging"],
                ),
                "version": ServerVariable(
                    default_value="v3",
                    enum_values=["v2", "v3"],
                ),
            },
        )

        config = Configuration.builder().server(server).build()

        assert config.base_url == "https://api.example.com/api/v3"

    def test_bhv_config_009_builder_server_with_variable_overrides(self) -> None:
        server = ServerConfiguration(
            url_template="https://{env}.example.com/api/{version}",
            variables={
                "env": ServerVariable(
                    default_value="api",
                    enum_values=["api", "staging"],
                ),
                "version": ServerVariable(
                    default_value="v3",
                    enum_values=["v2", "v3"],
                ),
            },
        )

        config = (
            Configuration.builder()
            .server(server, {"env": "staging", "version": "v2"})
            .build()
        )

        assert config.base_url == "https://staging.example.com/api/v2"

    def test_bhv_config_016_invalid_server_variable_enum_value_throws(self) -> None:
        server = ServerConfiguration(
            url_template="https://{env}.example.com/api",
            variables={
                "env": ServerVariable(
                    default_value="api",
                    enum_values=["api", "staging"],
                ),
            },
        )

        with pytest.raises(ValueError) as exc_info:
            Configuration.builder().server(server, {"env": "dev"})

        assert type(exc_info.value) is ValueError

    def test_bhv_config_001_builder_base_url_overrides_server(self) -> None:
        server = ServerConfiguration(url_template="https://api.example.com")

        config = (
            Configuration.builder()
            .server(server)
            .base_url("https://override.example.com")
            .build()
        )

        assert config.base_url == "https://override.example.com"


class TestDefaultConfiguration:
    def test_bhv_config_014_default_configuration_returns_instance(self) -> None:
        config = Configuration.default_configuration()

        assert isinstance(config, Configuration)
        assert config.base_url == "/api/v3"

    def test_default_configuration_returns_a_fresh_instance(self) -> None:
        # There is no settable process-wide default: every call builds a new
        # Configuration, so nothing one caller does can change what another
        # gets.
        first = Configuration.default_configuration()
        second = Configuration.default_configuration()

        assert first is not second
        assert first.base_url == second.base_url

    def test_configuration_has_no_settable_process_wide_default(self) -> None:
        assert not hasattr(Configuration, "set_default")
        assert not hasattr(Configuration, "get_default")


class TestConfigurationImmutability:
    def test_bhv_config_017_default_headers_is_immutable(self) -> None:
        config = Configuration.builder().default_header("X-Key", "value").build()

        assert isinstance(config.default_headers, MappingProxyType)
        assert config.default_headers["X-Key"] == "value"


class TestConfigurationRepr:
    @pytest.mark.parametrize(
        "name",
        [
            "Authorization",
            "proxy-authorization",
            "Cookie",
            "X-Api-Key",
            "X-Auth-Token",
            "x-client-SECRET",
        ],
    )
    def test_bhv_config_015_repr_masks_sensitive_default_header_values(
        self, name: str
    ) -> None:
        config = Configuration.builder().default_header(name, "hunter2").build()

        assert "hunter2" not in repr(config)
        assert "hunter2" not in str(config)
        assert f"{name!r}: '***'" in repr(config)

    def test_repr_keeps_the_base_url_and_other_header_values(self) -> None:
        config = (
            Configuration.builder()
            .base_url("https://api.example.com")
            .default_header("Accept", "text/plain")
            .build()
        )

        assert "base_url='https://api.example.com'" in repr(config)
        assert "'Accept': 'text/plain'" in repr(config)
