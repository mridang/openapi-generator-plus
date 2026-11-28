import pytest

from petstore_client.errors import OpenAPIException
from petstore_client.server_configuration import ServerConfiguration, ServerVariable
from petstore_client.servers import ALL, by_name


class TestServerConfiguration:
    def test_bhv_servers_009_resolves_url_template_using_default_variable_values(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com/v{version}",
            description="primary server",
            variables={
                "env": ServerVariable(
                    default_value="prod", enum_values=["prod", "staging"]
                ),
                "version": ServerVariable(default_value="1"),
            },
        )

        assert config.get_url() == "https://prod.api.example.com/v1"

    def test_bhv_servers_001_applies_overrides_and_falls_back_to_defaults_for_unset_variables(
        self,
    ) -> None:
        # "env" is overridden, "version" is left to its default — the resolved
        # URL must mix both.
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com/v{version}",
            variables={
                "env": ServerVariable(
                    default_value="prod", enum_values=["prod", "staging"]
                ),
                "version": ServerVariable(default_value="1"),
            },
        )

        assert (
            config.get_url({"env": "staging"}) == "https://staging.api.example.com/v1"
        )

    def test_bhv_servers_015_allows_a_null_description(self) -> None:
        config = ServerConfiguration(
            url_template="https://api.example.com", description=None
        )

        assert config.description is None
        assert config.get_url() == "https://api.example.com"

    def test_bhv_servers_006_exposes_raw_url_template_and_description(self) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com",
            description="primary server",
            variables={"env": ServerVariable(default_value="prod")},
        )

        assert config.url_template == "https://{env}.api.example.com"
        assert config.description == "primary server"

    def test_bhv_servers_008_rejects_override_value_outside_the_variable_enum(
        self,
    ) -> None:
        # server-variable-enum-validation: an override that is not in the
        # variable's enum is rejected at resolution time.
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com",
            variables={
                "env": ServerVariable(
                    default_value="prod", enum_values=["prod", "staging"]
                ),
            },
        )

        with pytest.raises(ValueError) as exc_info:
            config.get_url({"env": "dev"})
        assert type(exc_info.value) is ValueError
        assert not isinstance(exc_info.value, OpenAPIException)

    def test_bhv_servers_011_template_without_variables_is_returned_verbatim(
        self,
    ) -> None:
        config = ServerConfiguration(url_template="https://api.example.com")

        assert config.get_url() == "https://api.example.com"

    def test_bhv_servers_025_variables_cannot_be_changed_through_the_dict_they_were_built_from(
        self,
    ) -> None:
        declared = {"env": ServerVariable(default_value="prod")}
        config = ServerConfiguration(
            url_template="https://{env}.example.com", variables=declared
        )

        declared["extra"] = ServerVariable(default_value="x")

        assert list(config.variables) == ["env"]

    def test_bhv_lang_python_194_variables_are_read_only(self) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.example.com",
            variables={"env": ServerVariable(default_value="prod")},
        )

        with pytest.raises(TypeError):
            config.variables["env"] = ServerVariable(default_value="evil")  # type: ignore[index]


class TestGeneratedServers:
    def test_bhv_lang_python_195_all_is_an_immutable_tuple(self) -> None:
        assert isinstance(ALL, tuple)

    def test_bhv_servers_010_generated_servers_substitute_their_default_values(
        self,
    ) -> None:
        for server in ALL:
            defaults = {
                name: variable.default_value
                for name, variable in server.variables.items()
            }

            assert server.get_url() == server.url_template.format(**defaults)

    def test_bhv_servers_028_generated_server_variables_are_read_only_and_their_enums_immutable(
        self,
    ) -> None:
        for server in ALL:
            with pytest.raises(TypeError):
                server.variables["injected"] = ServerVariable(default_value="x")  # type: ignore[index]
            for variable in server.variables.values():
                assert isinstance(variable.enum_values, tuple)


class TestServerVariableResolution:
    def test_bhv_servers_003_accepts_an_override_value_that_is_in_the_variable_enum(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com",
            variables={
                "env": ServerVariable(
                    default_value="prod", enum_values=["prod", "staging"]
                )
            },
        )

        assert config.get_url({"env": "staging"}) == "https://staging.api.example.com"

    def test_bhv_servers_007_leaves_an_unknown_placeholder_in_the_template_untouched(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com/{unknown}",
            variables={"env": ServerVariable(default_value="prod")},
        )

        assert config.get_url() == "https://prod.api.example.com/{unknown}"

    def test_bhv_servers_014_variable_without_an_enum_accepts_any_override(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com",
            variables={"env": ServerVariable(default_value="prod")},
        )

        assert config.get_url({"env": "anything-goes"}) == (
            "https://anything-goes.api.example.com"
        )

    def test_bhv_servers_004_default_value_outside_the_enum_is_rejected(self) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com",
            variables={
                "env": ServerVariable(
                    default_value="dev", enum_values=["prod", "staging"]
                )
            },
        )

        with pytest.raises(ValueError) as exc_info:
            config.get_url()

        assert str(exc_info.value) == (
            "Invalid value 'dev' for server variable 'env'. "
            "Allowed values: [prod, staging]"
        )

    def test_bhv_servers_012_rejection_names_the_value_the_variable_and_the_allowed_values(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com/v{version}",
            variables={
                "env": ServerVariable(default_value="prod"),
                "version": ServerVariable(default_value="1", enum_values=["1", "2"]),
            },
        )

        with pytest.raises(ValueError) as exc_info:
            config.get_url({"version": "3"})

        assert str(exc_info.value) == (
            "Invalid value '3' for server variable 'version'. Allowed values: [1, 2]"
        )

    def test_bhv_servers_501_a_server_keeps_its_name_and_one_without_a_name_has_none(
        self,
    ) -> None:
        named = ServerConfiguration(
            url_template="https://api.example.com", name="primary"
        )
        unnamed = ServerConfiguration(url_template="https://api.example.com")

        assert named.name == "primary"
        assert unnamed.name is None

    def test_bhv_servers_502_an_unknown_server_name_is_rejected_with_the_known_names(
        self,
    ) -> None:
        with pytest.raises(ValueError) as exc_info:
            by_name("missing")

        assert str(exc_info.value) == "Unknown server name 'missing'. Known names: []"

    def test_bhv_servers_029_a_placeholder_from_a_default_stays_literal_in_the_url(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com/{version}",
            variables={
                "env": ServerVariable(default_value="{version}"),
                "version": ServerVariable(default_value="v2"),
            },
        )

        assert config.get_url() == "https://{version}.api.example.com/v2"

    def test_bhv_servers_005_a_placeholder_introduced_by_a_value_is_not_expanded(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com/{version}",
            variables={
                "env": ServerVariable(default_value="{version}"),
                "version": ServerVariable(default_value="v2"),
            },
        )

        assert config.get_url() == "https://{version}.api.example.com/v2"

    def test_bhv_servers_023_a_value_with_replacement_patterns_is_substituted_literally(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com",
            variables={"env": ServerVariable(default_value="a\\1b$&")},
        )

        assert config.get_url() == "https://a\\1b$&.api.example.com"

    def test_bhv_servers_002_a_variable_repeated_in_the_template_is_replaced_everywhere(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{region}.a.example.com/{region}/v1",
            variables={"region": ServerVariable(default_value="eu")},
        )

        assert config.get_url({"region": "us"}) == "https://us.a.example.com/us/v1"


class TestHostileGlobalServer:
    """The third global server carries quotes, an ampersand, a dollar sign and
    an interpolation opener in every text field."""

    def test_bhv_servers_013_url_description_and_variable_round_trip_unchanged(
        self,
    ) -> None:
        server = ALL[2]

        assert server.url_template == "https://{tenant}.example.com/api"
        assert server.description == 'Bob\'s "sandbox" & co: $5 #{x} a*/b'
        tenant = server.variables["tenant"]
        assert tenant.default_value == "o'neil"
        assert tenant.description == 'Tenant\'s "id"'
        assert tuple(tenant.enum_values) == ("o'neil", "a&b")

    def test_bhv_servers_021_the_url_is_resolved_with_the_raw_characters(self) -> None:
        server = ALL[2]

        assert server.get_url() == "https://o'neil.example.com/api"
        assert server.get_url({"tenant": "a&b"}) == "https://a&b.example.com/api"


class TestServerVariableOrderAndResolution:
    def test_bhv_misc_154_editing_the_variables_map_does_not_change_the_configuration(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{region}.example.com",
            variables={"region": ServerVariable(default_value="eu")},
        )
        with pytest.raises(TypeError):
            config.variables["region"] = ServerVariable(default_value="us")  # type: ignore[index]
        assert config.get_url() == "https://eu.example.com"

    def test_bhv_misc_155_an_unset_enum_variable_with_a_disallowed_default_is_refused_naming_it(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{region}.example.com",
            variables={
                "region": ServerVariable(default_value="apac", enum_values=("eu", "us"))
            },
        )
        with pytest.raises(ValueError, match="'region'"):
            config.get_url()

    def test_bhv_misc_156_the_list_of_all_servers_keeps_the_declared_order(
        self,
    ) -> None:
        import petstore_client.servers as servers

        for index, server in enumerate(ALL):
            assert server is getattr(servers, f"SERVER_{index}")

    def test_bhv_misc_157_explicit_variable_values_are_resolved_into_the_url(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.api.example.com/v{version}",
            variables={
                "env": ServerVariable(default_value="prod"),
                "version": ServerVariable(default_value="2"),
            },
        )
        assert config.get_url() == "https://prod.api.example.com/v2"

    def test_bhv_misc_257_a_variable_with_an_empty_enum_accepts_any_value(self) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.example.com",
            variables={"env": ServerVariable(default_value="x", enum_values=[])},
        )

        assert config.get_url({"env": "anything"}) == "https://anything.example.com"

    def test_bhv_misc_258_a_variable_without_an_override_uses_its_default(self) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.example.com",
            variables={"env": ServerVariable(default_value="prod")},
        )

        assert config.get_url() == "https://prod.example.com"

    def test_bhv_misc_259_a_placeholder_with_no_declared_variable_stays_in_the_url(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.{region}.example.com",
            variables={"env": ServerVariable(default_value="prod")},
        )

        assert config.get_url() == "https://prod.{region}.example.com"

    def test_bhv_misc_260_a_none_value_for_an_enum_variable_is_rejected(self) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.example.com",
            variables={
                "env": ServerVariable(
                    default_value="prod", enum_values=["prod", "staging"]
                )
            },
        )

        with pytest.raises(ValueError):
            config.get_url({"env": None})  # type: ignore[dict-item]

    def test_bhv_misc_261_a_value_with_backslashes_is_inserted_without_escape_processing(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://example.com/{path}",
            variables={"path": ServerVariable(default_value="x")},
        )

        assert config.get_url({"path": "a\\b\\n"}) == "https://example.com/a\\b\\n"

    def test_bhv_misc_278_a_templated_server_url_resolves_each_default_when_no_overrides_are_given(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.example.com/v{version}",
            variables={
                "env": ServerVariable(default_value="prod"),
                "version": ServerVariable(default_value="2"),
            },
        )

        assert config.get_url() == "https://prod.example.com/v2"

    def test_bhv_misc_297_every_variable_in_a_multi_variable_url_is_replaced_when_the_url_is_built(
        self,
    ) -> None:
        config = ServerConfiguration(
            url_template="https://{env}.{region}.example.com/v{version}",
            variables={
                "env": ServerVariable(default_value="prod"),
                "region": ServerVariable(default_value="eu"),
                "version": ServerVariable(default_value="3"),
            },
        )

        assert config.get_url({"region": "us"}) == "https://prod.us.example.com/v3"


class TestFixedServerUrl:
    def test_bhv_misc_158_a_server_with_a_fixed_url_reports_that_url_unchanged(
        self,
    ) -> None:
        config = ServerConfiguration(url_template="https://fixed.example.com/v1/items")
        assert config.get_url() == "https://fixed.example.com/v1/items"
