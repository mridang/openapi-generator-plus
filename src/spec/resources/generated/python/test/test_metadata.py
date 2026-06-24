# ruff: noqa
# mypy: ignore-errors
import dataclasses
import json
import os
import tomllib

import pytest

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
        # SerializationError surfaces as a clear test failure.
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


class TestOptionsImmutability:
    def test_options_are_frozen(self) -> None:
        # Per-operation Options classes are frozen dataclasses so a caller
        # cannot mutate an Options instance after constructing it.
        from petstore_client.api.options.find_pets_by_status_options import (
            FindPetsByStatusOptions,
        )

        options = FindPetsByStatusOptions(status="available")

        with pytest.raises(dataclasses.FrozenInstanceError):
            options.status = "sold"  # type: ignore[misc]
