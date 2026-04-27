defmodule PetstoreClient.ComposedSchemaTest do
  use ExUnit.Case, async: true

  describe "allOf PetWithOwner" do
    test "deserializes all properties from allOf composition" do
      json = ~s({"name":"doggie","photoUrls":["http://example.com/photo.jpg"],"ownerName":"John","ownerEmail":"john@example.com"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetWithOwner")

      assert %PetstoreClient.Models.PetWithOwner{} = result
      assert result.name == "doggie"
      assert result.owner_name == "John"
      assert result.owner_email == "john@example.com"
    end
  end

  describe "oneOf with discriminator PetFood" do
    test "deserializes to DryFood via discriminator" do
      json = ~s({"foodType":"dry","weightKg":2.5})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetFood")

      assert %PetstoreClient.Models.DryFood{} = result
    end
  end

  describe "anyOf PetTreatment" do
    test "deserializes Medication from anyOf" do
      json = ~s({"drugName":"Amoxicillin","dosage":"500mg"})
      result = PetstoreClient.ObjectSerializer.deserialize(json, "PetTreatment")

      assert %PetstoreClient.Models.Medication{} = result
      assert result.drug_name == "Amoxicillin"
    end
  end
end
