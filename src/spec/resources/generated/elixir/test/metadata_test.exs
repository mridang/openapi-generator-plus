# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
defmodule PetstoreClient.Models.MetadataTest do
  use ExUnit.Case, async: true

  describe "typed additional properties" do
    test "serializes empty metadata" do
      metadata = %PetstoreClient.Models.Metadata{}
      json = PetstoreClient.ObjectSerializer.serialize(metadata)

      assert json != nil
    end

    test "deserializes empty object" do
      metadata = PetstoreClient.ObjectSerializer.deserialize("{}", "Metadata")

      assert metadata != nil
    end

    test "deserializes additional string properties" do
      json = ~s({"createdAt":"2024-01-01T00:00:00+0000","customField":"hello"})
      metadata = PetstoreClient.ObjectSerializer.deserialize(json, "Metadata")

      assert metadata != nil
      assert %PetstoreClient.Models.Metadata{} = metadata
    end

    test "round-trip preserves known properties" do
      metadata = %PetstoreClient.Models.Metadata{
        created_at: "2024-01-01T00:00:00+00:00"
      }

      json = PetstoreClient.ObjectSerializer.serialize(metadata)
      data = Jason.decode!(json)
      assert data["createdAt"] != nil
    end

    test "defines additional_properties function" do
      assert PetstoreClient.Models.Metadata.additional_properties() == true
    end
  end

  # manifest-description-missing: the Hex package manifest must carry a
  # non-empty description so the package does not publish with an empty
  # description field.
  describe "package manifest" do
    test "mix project package metadata declares a non-empty description" do
      description = PetstoreClient.MixProject.project()[:package][:description]
      assert is_binary(description)
      assert String.trim(description) != ""
    end
  end
end
