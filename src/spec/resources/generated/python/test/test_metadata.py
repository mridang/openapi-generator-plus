from typing import Any, cast
import ast
import dataclasses
import importlib
import inspect
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
    def test_bhv_models_008_serialize_empty_metadata(self) -> None:
        from petstore_client.models import Metadata

        metadata = Metadata()
        result = ObjectSerializer().serialize(metadata)
        assert result is not None

    def test_bhv_models_003_deserialize_empty_object(self) -> None:
        metadata = ObjectSerializer().deserialize("{}", "Metadata")
        assert metadata is not None

    def test_bhv_models_004_deserializes_known_properties(self) -> None:
        json_str = '{"createdAt":"2024-01-01T00:00:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, "Metadata")
        assert metadata is not None
        assert metadata.created_at is not None

    def test_bhv_models_011_round_trip_preserves_known_properties(self) -> None:
        json_str = '{"createdAt":"2024-01-15T10:30:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, "Metadata")
        assert metadata is not None
        serialized = ObjectSerializer().serialize(metadata)
        data = json.loads(serialized)
        assert data.get("createdAt") is not None

    def test_bhv_models_009_additional_properties_field_exists(self) -> None:
        json_str = '{"createdAt":"2024-01-01T00:00:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, "Metadata")
        assert metadata is not None
        assert hasattr(metadata, "additional_properties")

    def test_bhv_models_023_wire_key_named_additional_properties_with_dict_value_preserved(
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

    def test_bhv_models_001_undeclared_wire_keys_are_captured_into_additional_properties(
        self,
    ) -> None:
        from petstore_client.models import Metadata

        metadata = Metadata.model_validate(
            {"createdAt": "2024-01-01T00:00:00Z", "region": "eu"}
        )

        assert metadata.additional_properties == {"region": "eu"}

    def test_bhv_models_010_a_declared_property_wins_over_a_colliding_additional_property(
        self,
    ) -> None:
        from datetime import datetime, timezone

        from petstore_client.models import Metadata

        stamp = datetime(2024, 1, 15, 10, 30, tzinfo=timezone.utc)
        declared_only = json.loads(
            ObjectSerializer().serialize(Metadata(createdAt=stamp))
        )
        # Field-name keys select the internal bucket path, so the colliding extras
        # are spread into additional_properties; a wire-alias keyword would make
        # that dict a real wire property. mypy checks the alias keyword only.
        colliding = Metadata.model_validate(
            {
                "created_at": stamp,
                "additional_properties": {"createdAt": "x", "extra": 1},
            }
        )

        data = json.loads(ObjectSerializer().serialize(colliding))

        assert data["createdAt"] == declared_only["createdAt"]
        assert data["extra"] == 1


class TestModelDocstringNoExampleNull:
    def test_bhv_models_007_generated_model_has_no_example_null_doc_line(self) -> None:
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

    def test_bhv_models_006_the_file_header_prints_the_description_without_backslash_escapes(
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
    def test_bhv_models_002_pyproject_declares_non_empty_description(self) -> None:
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
            "### Closing the client",
        ):
            assert readme.index(heading) < not_supported

    def test_readme_documents_credentials_per_call_options_and_the_behaviours_to_know(
        self,
    ) -> None:
        readme = self._read("README.md")
        not_supported = readme.index("## Not supported")
        for heading in (
            "### Credentials",
            "### Credentials per security scheme",
            "### Cookie parameters",
            "### Per-call options",
            "### Errors",
            "### Operations with several success responses",
            "### Constructing and decoding models",
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

    def test_readme_and_skills_name_the_credential_of_each_security_scheme(
        self,
    ) -> None:
        for name in [
            "pet_store_basic",
            "admin_basic",
            "pet_store_bearer",
            "service_token",
            "api_key_header",
            "internal_api_key",
            "api_key_query",
            "session_cookie",
            "machine_auth",
            "legacy_auth",
            "user_auth",
            "browser_auth",
            "sso_auth",
        ]:
            assert f"`{name}`" in self._read("README.md")
            assert f"`{name}`" in self._read("SKILLS.md")

    def test_readme_and_skills_describe_a_graceful_close_and_raw_cookies(self) -> None:
        for document in ("README.md", "SKILLS.md"):
            text = self._read(document)
            assert "graceful" in text
            assert "cancels every request" not in text
            assert "cookie-octet" in text

    def test_bhv_options_044_readme_socks_note_names_the_builder_setter(self) -> None:
        readme = self._read("README.md")
        assert (
            "`TransportOptions.builder().proxy()` accepts only `http://` and `https://` URLs."
            in readme
        )
        assert "`TransportOptions.proxy()`" not in readme

    def test_bhv_skills_001_the_error_handling_section_names_the_root_and_the_api_error_siblings(
        self,
    ) -> None:
        section = (
            self._read("SKILLS.md")
            .split("## Error Handling", 1)[1]
            .split("\n## ", 1)[0]
        )
        assert "- `OpenAPIException` (root)" in section
        assert "  - `SerializationException`" in section
        assert "  - `OAuth2ServerException`" in section
        assert "  - `OAuth2TokenException`" in section
        assert "  - `ApiException`" in section

    def test_bhv_skills_002_the_configuration_section_documents_the_retries(
        self,
    ) -> None:
        section = (
            self._read("SKILLS.md").split("## Configuration", 1)[1].split("\n## ", 1)[0]
        )
        assert "### Automatic retries" in section
        assert "max_retries(0)" in section
        assert "`Retry-After`" in section

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
    def test_bhv_options_063_options_are_frozen(self) -> None:
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

    def test_bhv_options_055_the_per_call_settings_default_to_the_clients(self) -> None:
        options = self._options()()

        assert options.timeout is None
        assert options.headers is None
        assert options.max_retries is None

    @pytest.mark.parametrize("timeout", [0, -5, True, "10"])
    def test_bhv_options_057_a_timeout_below_one_millisecond_or_not_a_number_is_rejected(
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


class TestEveryOperationTakesOptions:
    """F827: every operation, with or without parameters, takes the Options object that
    carries the per-call timeout, headers and retry budget."""

    OPERATIONS = [
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "add_pet",
            "AddPetOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "add_pet_photos",
            "AddPetPhotosOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "add_pet_treatment",
            "AddPetTreatmentOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "delete_pet",
            "DeletePetOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "download_pet_document",
            "DownloadPetDocumentOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "find_pets_born_on",
            "FindPetsBornOnOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "find_pets_by_status",
            "FindPetsByStatusOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_external_pet_info",
            "GetExternalPetInfoOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_multi_server_pet_info",
            "GetMultiServerPetInfoOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_avatar",
            "GetPetAvatarOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_avatar_thumbnail",
            "GetPetAvatarThumbnailOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_by_id",
            "GetPetByIdOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_by_name",
            "GetPetByNameOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_by_note",
            "GetPetByNoteOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_passport",
            "GetPetPassportOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_photo",
            "GetPetPhotoOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_summary",
            "GetPetSummaryOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_pet_tag",
            "GetPetTagOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "get_staging_pet_info",
            "GetStagingPetInfoOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "set_pet_avatar",
            "SetPetAvatarOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "set_pet_avatar_thumbnail",
            "SetPetAvatarThumbnailOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "set_pet_preferences",
            "SetPetPreferencesOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "update_pet",
            "UpdatePetOptions",
            True,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "upload_pet_certificate",
            "UploadPetCertificateOptions",
            False,
        ),
        (
            "petstore_client.api.pet_api",
            "PetApi",
            "upload_pet_document",
            "UploadPetDocumentOptions",
            False,
        ),
        (
            "petstore_client.api.problems_api",
            "ProblemsApi",
            "get_fallback",
            "GetFallbackOptions",
            True,
        ),
        (
            "petstore_client.api.problems_api",
            "ProblemsApi",
            "get_member",
            "GetMemberOptions",
            True,
        ),
        (
            "petstore_client.api.problems_api",
            "ProblemsApi",
            "get_project",
            "GetProjectOptions",
            True,
        ),
        (
            "petstore_client.api.problems_api",
            "ProblemsApi",
            "get_range",
            "GetRangeOptions",
            True,
        ),
        (
            "petstore_client.api.problems_api",
            "ProblemsApi",
            "get_upstream",
            "GetUpstreamOptions",
            True,
        ),
        (
            "petstore_client.api.problems_api",
            "ProblemsApi",
            "take_lock",
            "TakeLockOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "create_account",
            "CreateAccountOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "delete_order",
            "DeleteOrderOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_by_swatch",
            "GetBySwatchOptions",
            False,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_defaults",
            "GetDefaultsOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_department",
            "GetDepartmentOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_grouped_categories",
            "GetGroupedCategoriesOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_inventory",
            "GetInventoryOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_matrix",
            "GetMatrixOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_native_values",
            "GetNativeValuesOptions",
            False,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_order_by_id",
            "GetOrderByIdOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_order_by_tracking_id",
            "GetOrderByTrackingIdOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_stock_item",
            "GetStockItemOptions",
            False,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_swatch",
            "GetSwatchOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_swatch_groups",
            "GetSwatchGroupsOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_timestamp_groups",
            "GetTimestampGroupsOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "get_tree",
            "GetTreeOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "place_order",
            "PlaceOrderOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "reserve_stock",
            "ReserveStockOptions",
            True,
        ),
        (
            "petstore_client.api.store_api",
            "StoreApi",
            "search_store",
            "SearchStoreOptions",
            False,
        ),
    ]

    @pytest.mark.parametrize(
        "module,api,operation,options_name,call_settings_only", OPERATIONS
    )
    def test_bhv_options_054_the_operation_has_an_options_argument(
        self,
        module: str,
        api: str,
        operation: str,
        options_name: str,
        call_settings_only: bool,
    ) -> None:
        method = getattr(getattr(importlib.import_module(module), api), operation)

        assert "options" in inspect.signature(method).parameters

    @pytest.mark.parametrize(
        "module,api,operation,options_name,call_settings_only", OPERATIONS
    )
    def test_the_options_object_carries_the_per_call_settings(
        self,
        module: str,
        api: str,
        operation: str,
        options_name: str,
        call_settings_only: bool,
    ) -> None:
        options = getattr(
            importlib.import_module("petstore_client.api.options"), options_name
        )
        names = [field.name for field in dataclasses.fields(options)]

        assert {"timeout", "headers", "max_retries"} <= set(names)
        if call_settings_only:
            assert "auth" not in names

    @pytest.mark.parametrize(
        "module,api,operation,options_name,call_settings_only",
        [entry for entry in OPERATIONS if entry[4]],
    )
    def test_an_operation_without_parameters_builds_its_options_from_the_call_settings(
        self,
        module: str,
        api: str,
        operation: str,
        options_name: str,
        call_settings_only: bool,
    ) -> None:
        options = getattr(
            importlib.import_module("petstore_client.api.options"), options_name
        )

        built = options(timeout=5, headers={"X-A": "1"}, max_retries=0)

        assert (built.timeout, built.max_retries) == (5, 0)
        assert built.headers["X-A"] == "1"
        assert options().timeout is None

    @pytest.mark.parametrize(
        "module,api,operation,options_name,call_settings_only", OPERATIONS
    )
    def test_bhv_authintegration_011_has_no_per_call_authenticator_on_any_options_object(
        self,
        module: str,
        api: str,
        operation: str,
        options_name: str,
        call_settings_only: bool,
    ) -> None:
        options = getattr(
            importlib.import_module("petstore_client.api.options"), options_name
        )
        names = {field.name for field in dataclasses.fields(options)}

        assert not names & {"auth", "authenticator"}


class TestMetadataEquality:
    @staticmethod
    def _decode(raw: str) -> Any:
        return ObjectSerializer().deserialize(raw, "Metadata")

    def test_bhv_models_005_equal_known_and_extra_properties_are_equal(self) -> None:
        raw = '{"createdAt":"2024-01-01T00:00:00Z","origin":"a"}'

        assert self._decode(raw) == self._decode(raw)

    def test_bhv_models_012_to_string_dumps_the_fields_and_the_additional_properties_map(
        self,
    ) -> None:
        from petstore_client.models import Metadata

        metadata = Metadata(additional_properties={"customField": "hello"})

        assert "created_at=None" in repr(metadata)
        assert "additional_properties={'customField': 'hello'}" in repr(metadata)

    def test_bhv_models_018_models_whose_decimal_fields_differ_only_in_trailing_zeros_compare_equal(
        self,
    ) -> None:
        from decimal import Decimal

        from petstore_client.models.defaults import Defaults

        assert Defaults(ratio=Decimal("1.5")) == Defaults(ratio=Decimal("1.50"))

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


class TestMetadataBehaviours:
    def test_bhv_models_013_a_newly_built_model_has_an_empty_additional_properties_collection(
        self,
    ) -> None:
        from petstore_client.models import Metadata

        assert Metadata().additional_properties == {}

    def test_bhv_models_015_a_changed_copy_keeps_the_additional_properties(
        self,
    ) -> None:
        from petstore_client.models import Metadata

        original = ObjectSerializer().deserialize('{"origin":"a","extra":1}', Metadata)
        assert original is not None
        changed = original.model_copy(update={"origin": "b"})

        assert changed.additional_properties == original.additional_properties
        assert changed.additional_properties != {}

    def test_bhv_models_016_a_copy_clears_an_optional_field_only_when_none_is_passed(
        self,
    ) -> None:
        from petstore_client.models import Metadata

        original = ObjectSerializer().deserialize(
            '{"createdAt":"2024-01-01T00:00:00Z"}', Metadata
        )
        assert original is not None
        kept = original.model_copy()
        cleared = original.model_copy(update={"created_at": None})

        assert kept.created_at == original.created_at
        assert cleared.created_at is None

    def test_bhv_models_017_a_copy_can_set_or_clear_an_optional_field_and_the_original_keeps_its_value(
        self,
    ) -> None:
        from petstore_client.models import Metadata

        original = ObjectSerializer().deserialize(
            '{"createdAt":"2024-01-01T00:00:00Z"}', Metadata
        )
        assert original is not None
        cleared = original.model_copy(update={"created_at": None})
        filled = Metadata().model_copy(update={"created_at": original.created_at})

        assert cleared.created_at is None
        assert original.created_at is not None
        assert filled.created_at == original.created_at

    def test_bhv_models_021_each_member_of_an_integer_backed_enum_carries_its_declared_number(
        self,
    ) -> None:
        from petstore_client.models.priority import Priority

        assert [member.value for member in Priority] == [1, 2, 3]
        assert Priority.NUMBER_3.value == 3

    def test_bhv_models_022_a_model_whose_schema_allows_undeclared_properties_reports_it(
        self,
    ) -> None:
        from petstore_client.models import Metadata
        from petstore_client.models.category import Category

        assert "additional_properties" in Metadata.model_fields
        assert "additional_properties" not in Category.model_fields

    def test_bhv_models_019_two_models_with_a_different_field_value_compare_unequal(
        self,
    ) -> None:
        from petstore_client.models import Metadata

        first = ObjectSerializer().deserialize('{"origin":"a"}', Metadata)
        second = ObjectSerializer().deserialize('{"origin":"b"}', Metadata)

        assert first != second

    def test_bhv_models_020_models_with_the_same_fields_compare_equal_and_hash_alike(
        self,
    ) -> None:
        from petstore_client.models import Metadata

        first = ObjectSerializer().deserialize('{"origin":"a"}', Metadata)
        second = ObjectSerializer().deserialize('{"origin":"a"}', Metadata)

        assert first == second
        assert hash(first) == hash(second)

    def test_bhv_options_056_a_per_call_timeout_of_zero_is_refused_before_any_request(
        self,
    ) -> None:
        from petstore_client.api.options.find_pets_by_status_options import (
            FindPetsByStatusOptions,
        )

        with pytest.raises(ValueError, match="timeout"):
            FindPetsByStatusOptions(timeout=0)
