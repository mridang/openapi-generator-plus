from types import MappingProxyType
from typing import Any

import pytest
from typing import Optional

from petstore_client.call_hook import CallInfo, CallObserver
from petstore_client.configuration import Configuration, ConfigurationBuilder
from petstore_client.server_configuration import ServerConfiguration, ServerVariable


class TestConfigurationDefaults:
    def test_bhv_config_018_default_constructor_uses_spec_base_url(self) -> None:
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

    def test_bhv_misc_276_the_default_configuration_and_builder_use_the_spec_base_url(
        self,
    ) -> None:
        assert Configuration.builder().build().base_url == "/api/v3"

    def test_bhv_misc_277_each_call_to_the_default_configuration_returns_a_distinct_object(
        self,
    ) -> None:
        assert Configuration() is not Configuration()

    def test_bhv_misc_279_changing_headers_returned_from_a_configuration_leaves_its_own_headers_unchanged(
        self,
    ) -> None:
        config = Configuration(
            base_url="https://example.com", default_headers={"X-Keep": "yes"}
        )

        headers = config.default_headers
        headers["X-New"] = "no"

        assert "X-New" not in config.default_headers


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

    def test_bhv_config_020_builder_chain_sets_base_url_and_headers_in_one_configuration(
        self,
    ) -> None:
        config = (
            Configuration.builder()
            .base_url("https://api.example.com")
            .default_header("Authorization", "Bearer token")
            .build()
        )

        assert config.base_url == "https://api.example.com"
        assert config.default_headers["Authorization"] == "Bearer token"

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

    def test_bhv_config_021_server_url_template_resolves_its_variables_to_their_defaults(
        self,
    ) -> None:
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

        config = Configuration.builder().server(server).build()

        assert config.base_url == "https://api.example.com/api/v3"

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

    def test_bhv_config_019_default_configuration_returns_spec_base_url_and_no_headers(
        self,
    ) -> None:
        config = Configuration.default_configuration()

        assert config.base_url == "/api/v3"
        assert len(config.default_headers) == 0

    def test_bhv_config_023_default_configuration_returns_a_fresh_instance(
        self,
    ) -> None:
        # There is no settable process-wide default: every call builds a new
        # Configuration, so nothing one caller does can change what another
        # gets.
        first = Configuration.default_configuration()
        second = Configuration.default_configuration()

        assert first is not second
        assert first.base_url == second.base_url

    def test_bhv_config_024_configuration_has_no_settable_process_wide_default(
        self,
    ) -> None:
        assert not hasattr(Configuration, "set_default")
        assert not hasattr(Configuration, "get_default")


class TestConfigurationImmutability:
    def test_bhv_config_022_mutating_default_headers_does_not_change_the_configuration(
        self,
    ) -> None:
        config = Configuration.builder().default_header("X-Test", "value").build()
        headers: Any = config.default_headers

        with pytest.raises(TypeError):
            headers["X-Mutated"] = "should-not-affect-config"

        assert "X-Mutated" not in config.default_headers

    def test_bhv_config_017_default_headers_is_immutable(self) -> None:
        config = Configuration.builder().default_header("X-Key", "value").build()

        assert isinstance(config.default_headers, MappingProxyType)
        assert config.default_headers["X-Key"] == "value"


class TestConfigurationRepr:
    def test_bhv_config_026_the_builder_returns_the_base_url_that_was_set(self) -> None:
        config = Configuration.builder().base_url("https://builder.example.com").build()

        assert config.base_url == "https://builder.example.com"

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

    def test_bhv_lang_python_055_repr_keeps_the_base_url_and_other_header_values(
        self,
    ) -> None:
        config = (
            Configuration.builder()
            .base_url("https://api.example.com")
            .default_header("Accept", "text/plain")
            .build()
        )

        assert "base_url='https://api.example.com'" in repr(config)
        assert "'Accept': 'text/plain'" in repr(config)

    def test_bhv_misc_136_the_debug_text_shows_the_base_url_and_non_sensitive_headers(
        self,
    ) -> None:
        config = (
            Configuration.builder()
            .base_url("https://api.example.com")
            .default_header("Accept", "text/plain")
            .build()
        )

        text = repr(config)
        assert "https://api.example.com" in text
        assert "'Accept': 'text/plain'" in text


class _Hook:
    def start(self, info: CallInfo, headers: dict[str, str]) -> Optional[CallObserver]:
        return None


class TestConfigurationCallHook:
    def test_bhv_lang_python_056_there_is_no_call_hook_by_default(self) -> None:
        assert Configuration().call_hook is None
        assert Configuration.builder().build().call_hook is None

    def test_bhv_lang_python_057_the_builder_sets_the_call_hook_and_the_configuration_reads_it_back(
        self,
    ) -> None:
        hook = _Hook()

        config = Configuration.builder().call_hook(hook).build()

        assert config.call_hook is hook

    def test_bhv_lang_python_058_the_string_form_does_not_print_the_call_hook(
        self,
    ) -> None:
        config = Configuration.builder().call_hook(_Hook()).build()

        assert "_Hook" not in repr(config)
        assert "call_hook" not in repr(config)
