defmodule PetstoreClient.ServerVariableTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.ServerVariable

  describe "ServerVariable struct" do
    test "bhv servers 017 exposes the default value" do
      variable = %ServerVariable{default_value: "prod"}

      assert variable.default_value == "prod"
    end

    test "bhv servers 015 a null description is allowed and the default value is kept" do
      variable = %ServerVariable{default_value: "v1", description: nil}

      assert variable.description == nil
      assert variable.default_value == "v1"
    end

    test "bhv servers 016 description and enum_values default to nil and an empty list" do
      variable = %ServerVariable{default_value: "v1"}

      assert variable.description == nil
      assert variable.enum_values == []
    end

    test "retains an enum constraint when provided" do
      variable = %ServerVariable{
        default_value: "prod",
        description: "Deployment environment",
        enum_values: ["prod", "staging"]
      }

      assert variable.description == "Deployment environment"
      assert variable.enum_values == ["prod", "staging"]
    end

    test "bhv servers 018 enum values are exposed for validation" do
      variable = %ServerVariable{default_value: "prod", enum_values: ["prod", "staging"]}

      assert variable.enum_values == ["prod", "staging"]
    end

    test "requires the default_value key" do
      assert_raise ArgumentError, fn ->
        struct!(ServerVariable, description: "missing default")
      end
    end
  end
end
