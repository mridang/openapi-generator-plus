import json
import os
import tomllib
from petstore_client.object_serializer import ObjectSerializer


class TestMetadataTypedAdditionalProperties:
    def test_serialize_empty_metadata(self) -> None:
        from petstore_client.models import Metadata

        metadata = Metadata()
        result = ObjectSerializer().serialize(metadata)
        assert result is not None

    def test_deserialize_empty_object(self) -> None:
        metadata = ObjectSerializer().deserialize('{}', 'Metadata')
        assert metadata is not None

    def test_deserializes_known_properties(self) -> None:
        json_str = '{"createdAt":"2024-01-01T00:00:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, 'Metadata')
        assert metadata is not None
        assert metadata.created_at is not None

    def test_round_trip_preserves_known_properties(self) -> None:
        json_str = '{"createdAt":"2024-01-15T10:30:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, 'Metadata')
        assert metadata is not None
        serialized = ObjectSerializer().serialize(metadata)
        data = json.loads(serialized)
        assert data.get('createdAt') is not None

    def test_additional_properties_field_exists(self) -> None:
        json_str = '{"createdAt":"2024-01-01T00:00:00Z"}'
        metadata = ObjectSerializer().deserialize(json_str, 'Metadata')
        assert metadata is not None
        assert hasattr(metadata, 'additional_properties')


class TestPackageManifest:
    def test_pyproject_declares_non_empty_description(self) -> None:
        # manifest-description-missing: the published pyproject.toml must carry
        # a non-empty description (wired from the spec's appDescription) so the
        # package does not publish with an empty description on PyPI.
        pyproject_path = os.path.join(os.getcwd(), 'pyproject.toml')
        assert os.path.exists(pyproject_path)
        with open(pyproject_path, 'rb') as handle:
            manifest = tomllib.load(handle)
        description = manifest['project']['description']
        assert isinstance(description, str)
        assert description.strip() != ''
