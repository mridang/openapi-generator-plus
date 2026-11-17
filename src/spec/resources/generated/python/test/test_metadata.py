from typing import Any, cast
import ast
import dataclasses
import json
import os
import sys

import pytest

if sys.version_info >= (3, 11):
    import tomllib
else:
    import tomli as tomllib

from petstore_client.object_serializer import ObjectSerializer


class TestMetadataTypedAdditionalProperties:
    def test_serialize_empty_metadata(self) -> None:
        from petstore_client.models import Metadata

        metadata = Metadata()
        result = ObjectSerializer().serialize(metadata)
        assert result is not None

    def test_deserialize_empty_object(self) -> None:
        metadata = ObjectSerializer().deserialize("{}", "Metadata")
        assert metadata is not None

    def test_deserializes_known_properties(self) -> None:
        json_str = '{"createdAt":"2024-01-01T00:00:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, "Metadata")
        assert metadata is not None
        assert metadata.created_at is not None

    def test_round_trip_preserves_known_properties(self) -> None:
        json_str = '{"createdAt":"2024-01-15T10:30:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, "Metadata")
        assert metadata is not None
        serialized = ObjectSerializer().serialize(metadata)
        data = json.loads(serialized)
        assert data.get("createdAt") is not None

    def test_additional_properties_field_exists(self) -> None:
        json_str = '{"createdAt":"2024-01-01T00:00:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, "Metadata")
        assert metadata is not None
        assert hasattr(metadata, "additional_properties")

    def test_wire_key_named_additional_properties_with_dict_value_preserved(
        self,
    ) -> None:
        # M11 — a real wire property literally named `additional_properties`
        # whose value is itself a dict must NOT be mistaken for the internal
        # capture bucket and dropped. The internal bucket is distinguished by
        # the call PATH (declared field / field-name kwargs), not by the value
        # being a dict. A wire payload addresses `createdAt` by its alias, so
        # the `additional_properties` entry here is a genuine wire property and
        # survives the round trip under its real name.
        from petstore_client.models import Metadata

        wire = {
            "createdAt": "2024-01-01T00:00:00Z",
            "additional_properties": {"foo": "bar"},
        }
        metadata = Metadata.model_validate(wire)
        assert metadata.additional_properties == {
            "additional_properties": {"foo": "bar"},
        }

    def test_wire_key_named_additional_properties_alongside_other_extras(self) -> None:
        # The dict-valued `additional_properties` wire key coexists with other
        # genuine extras; none are lost.
        from petstore_client.models import Metadata

        wire = {
            "createdAt": "2024-01-01T00:00:00Z",
            "additional_properties": {"foo": "bar"},
            "region": "eu",
        }
        metadata = Metadata.model_validate(wire)
        assert metadata.additional_properties["additional_properties"] == {"foo": "bar"}
        assert metadata.additional_properties["region"] == "eu"

    def test_field_name_kwargs_bucket_is_spread_not_nested(self) -> None:
        # The internal bucket on the field-name path (direct kwargs) is spread
        # into `additional_properties`, never re-nested under itself.
        from petstore_client.models import Metadata

        metadata = Metadata(additional_properties={"k": "v"})
        assert metadata.additional_properties == {"k": "v"}

    def test_dict_valued_wire_key_survives_full_round_trip(self) -> None:
        # End-to-end: deserialize -> serialize -> deserialize keeps the
        # dict-valued `additional_properties` wire property intact. The
        # deserializing convenience method is wrapped so an unexpected
        # SerializationException surfaces as a clear test failure.
        try:
            wire = (
                '{"createdAt":"2024-01-01T00:00:00Z",'
                '"additional_properties":{"foo":"bar"}}'
            )
            metadata = ObjectSerializer().deserialize(wire, "Metadata")
            assert metadata is not None
            restored = ObjectSerializer().deserialize(
                ObjectSerializer().serialize(metadata), "Metadata"
            )
        except Exception as exc:  # pragma: no cover - defensive surface
            pytest.fail(f"round-trip raised unexpectedly: {exc}")
        assert restored.additional_properties["additional_properties"] == {"foo": "bar"}


class TestModelDocstringNoExampleNull:
    def test_generated_model_has_no_example_null_doc_line(self) -> None:
        # L1 parity — a generated model file must not carry a literal
        # "Example: null" / "@example null" documentation line (a JS/Java-style
        # null-example doc artefact). Python is not affected, but assert it so a
        # regression in the shared codegen surfaces in this SDK too.
        import petstore_client.models.metadata as metadata_module

        source_path = metadata_module.__file__
        assert source_path is not None
        with open(source_path, "r", encoding="utf-8") as handle:
            source = handle.read()
        assert "Example: null" not in source
        assert "@example null" not in source

    def test_the_file_header_prints_the_description_without_backslash_escapes(
        self,
    ) -> None:
        import petstore_client.models.metadata as metadata_module

        source_path = metadata_module.__file__
        assert source_path is not None
        with open(source_path, "r", encoding="utf-8") as handle:
            header = handle.read().split("\n\n", 1)[0]
        assert '"quoted"' in header
        assert '\\"' not in header


class TestPackageManifest:
    def test_pyproject_declares_non_empty_description(self) -> None:
        # manifest-description-missing: the published pyproject.toml must carry
        # a non-empty description (wired from the spec's appDescription) so the
        # package does not publish with an empty description on PyPI.
        pyproject_path = os.path.join(os.getcwd(), "pyproject.toml")
        assert os.path.exists(pyproject_path)
        with open(pyproject_path, "rb") as handle:
            manifest = tomllib.load(handle)
        description = manifest["project"]["description"]
        assert isinstance(description, str)
        assert description.strip() != ""

    def test_pyproject_description_keeps_quotes_and_markup_unescaped(self) -> None:
        with open(os.path.join(os.getcwd(), "pyproject.toml"), "rb") as handle:
            description = tomllib.load(handle)["project"]["description"]
        assert '"quoted"' in description
        assert "&" + "amp;" not in description
        assert "&" + "#39;" not in description


class TestMakefileTestTarget:
    def test_make_test_runs_the_configured_testpaths_not_only_the_test_directory(
        self,
    ) -> None:
        with open(os.path.join(os.getcwd(), "pyproject.toml"), "rb") as handle:
            testpaths = tomllib.load(handle)["tool"]["pytest"]["ini_options"][
                "testpaths"
            ]
        with open(os.path.join(os.getcwd(), "Makefile"), encoding="utf-8") as handle:
            pytest_lines = [line for line in handle if "python -m pytest" in line]

        assert "spec" in testpaths
        assert pytest_lines == ["\tpython -m pytest -v\n"]


class TestReadmeAndSkillsText:
    @staticmethod
    def _read(name: str) -> str:
        with open(os.path.join(os.getcwd(), name), "r", encoding="utf-8") as handle:
            return handle.read()

    @classmethod
    def _manifest_name(cls) -> str:
        with open(os.path.join(os.getcwd(), "pyproject.toml"), "rb") as handle:
            return str(tomllib.load(handle)["project"]["name"])

    def test_readme_package_name_is_the_manifest_name(self) -> None:
        assert f"- Name: `{self._manifest_name()}`\n" in self._read("README.md")

    def test_skills_install_command_uses_the_manifest_name(self) -> None:
        assert f"pip install {self._manifest_name()}\n" in self._read("SKILLS.md")

    def test_readme_opens_with_installation_and_usage_that_link_the_skills(
        self,
    ) -> None:
        readme = self._read("README.md")
        assert (
            readme.index("## Installation")
            < readme.index("## Usage")
            < readme.index("## Configuration")
        )
        assert f"pip install {self._manifest_name()}\n" in readme
        assert "[SKILLS.md](SKILLS.md)" in readme

    def test_readme_files_supported_features_outside_not_supported(self) -> None:
        readme = self._read("README.md")
        not_supported = readme.index("## Not supported")
        for heading in (
            "### Custom HTTP client",
            "### Raw responses",
            "### Cancellation",
        ):
            assert readme.index(heading) < not_supported

    def test_readme_documents_credentials_per_call_options_and_the_behaviours_to_know(
        self,
    ) -> None:
        readme = self._read("README.md")
        not_supported = readme.index("## Not supported")
        for heading in (
            "### Credentials",
            "### Per-call options",
            "### Errors",
            "### Operations with several success responses",
            "### Models are immutable",
            "### Logging and tracing",
            "### Versioning and supported Python",
            "### Behaviours to know",
        ):
            assert readme.index(heading) < not_supported
        assert "Unknown values fail loudly" in readme
        assert "Streaming is not supported" in readme
        assert "stay separate objects" in readme

    def test_readme_not_supported_lists_xml_links_and_mutual_tls(self) -> None:
        readme = self._read("README.md")
        for heading in ("### XML bodies", "### Link Objects", "### Mutual TLS"):
            assert readme.index(heading) > readme.index("## Not supported")

    def test_skills_models_sample_passes_the_required_fields(self) -> None:
        skills = self._read("SKILLS.md")
        sample = skills[skills.index("## Models") : skills.index("## Binary")]
        assert "Required fields must be passed to the constructor" in sample

    def test_readme_socks_note_names_the_builder_setter(self) -> None:
        readme = self._read("README.md")
        assert (
            "`TransportOptions.builder().proxy()` accepts only `http://` and `https://` URLs."
            in readme
        )
        assert "`TransportOptions.proxy()`" not in readme

    def test_skills_call_the_authenticator_an_abstract_base_class(self) -> None:
        skills = self._read("SKILLS.md")
        assert "The `Authenticator` abstract base class is the seam for tests" in skills
        assert "`Authenticator` protocol" not in skills

    def test_skills_error_handling_example_passes_real_arguments(self) -> None:
        skills = self._read("SKILLS.md")
        section = skills.split("## Error Handling", 1)[1].split("\n## ", 1)[0]
        block = section.split("```python\n", 1)[1].split("```", 1)[0]
        tree = ast.parse(block)
        awaits = [node for node in ast.walk(tree) if isinstance(node, ast.Await)]
        assert len(awaits) == 1
        assert (
            ast.unparse(awaits[0].value) == "client.pet.add_pet(Pet.model_construct())"
        )
        for node in tree.body:
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                exec(
                    compile(
                        ast.Module(body=[node], type_ignores=[]), "<skills>", "exec"
                    ),
                    {},
                )


class TestOptionsImmutability:
    def test_options_are_frozen(self) -> None:
        # Per-operation Options classes are frozen dataclasses so a caller
        # cannot mutate an Options instance after constructing it.
        from petstore_client.api.options.find_pets_by_status_options import (
            FindPetsByStatusOptions,
        )

        options = FindPetsByStatusOptions(status="available")

        with pytest.raises(dataclasses.FrozenInstanceError):
            cast(Any, options).status = "sold"


class TestOptionsPerCallSettings:
    @staticmethod
    def _options() -> Any:
        from petstore_client.api.options.find_pets_by_status_options import (
            FindPetsByStatusOptions,
        )

        return FindPetsByStatusOptions

    def test_the_per_call_settings_default_to_the_clients(self) -> None:
        options = self._options()()

        assert options.timeout is None
        assert options.headers is None
        assert options.max_retries is None

    @pytest.mark.parametrize("timeout", [0, -5, True, "10"])
    def test_a_timeout_below_one_millisecond_or_not_a_number_is_rejected(
        self, timeout: Any
    ) -> None:
        with pytest.raises(ValueError, match="timeout"):
            self._options()(timeout=timeout)

    @pytest.mark.parametrize("max_retries", [-1, True, 1.5])
    def test_a_retry_budget_below_zero_or_not_a_whole_number_is_rejected(
        self, max_retries: Any
    ) -> None:
        with pytest.raises(ValueError, match="max_retries"):
            self._options()(max_retries=max_retries)

    def test_zero_retries_is_allowed(self) -> None:
        assert self._options()(max_retries=0).max_retries == 0

    def test_the_headers_are_a_read_only_copy(self) -> None:
        source = {"X-A": "1"}
        options = self._options()(headers=source)
        source["X-A"] = "2"

        assert options.headers["X-A"] == "1"
        with pytest.raises(TypeError):
            cast(Any, options.headers)["X-B"] = "3"


class TestMetadataEquality:
    @staticmethod
    def _decode(raw: str) -> Any:
        return ObjectSerializer().deserialize(raw, "Metadata")

    def test_equal_known_and_extra_properties_are_equal(self) -> None:
        raw = '{"createdAt":"2024-01-01T00:00:00Z","origin":"a"}'

        assert self._decode(raw) == self._decode(raw)

    def test_instances_differing_only_in_additional_properties_are_not_equal(
        self,
    ) -> None:
        first = self._decode('{"createdAt":"2024-01-01T00:00:00Z","origin":"a"}')
        second = self._decode('{"createdAt":"2024-01-01T00:00:00Z","origin":"b"}')

        assert first.created_at == second.created_at
        assert first != second

    def test_an_extra_property_makes_instances_unequal(self) -> None:
        bare = self._decode('{"createdAt":"2024-01-01T00:00:00Z"}')
        extra = self._decode('{"createdAt":"2024-01-01T00:00:00Z","origin":"a"}')

        assert bare != extra
