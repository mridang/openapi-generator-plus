defmodule PetstoreClient.Models.MetadataTest do
  use ExUnit.Case, async: true

  describe "typed additional properties" do
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
end
