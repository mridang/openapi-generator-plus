defmodule PetstoreClient.ServerConfigurationTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.ServerConfiguration
  alias PetstoreClient.ServerVariable

  describe "url/2" do
    test "resolves a plain template with no variables unchanged" do
      config = %ServerConfiguration{url_template: "https://api.example.com/v1"}

      assert ServerConfiguration.url(config) == "https://api.example.com/v1"
    end

    test "substitutes variables using their default values" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com/v{version}",
        variables: %{
          "env" => %ServerVariable{default_value: "prod"},
          "version" => %ServerVariable{default_value: "2"}
        }
      }

      assert ServerConfiguration.url(config) == "https://prod.api.example.com/v2"
    end

    test "overrides take precedence over default values" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{"env" => %ServerVariable{default_value: "prod"}}
      }

      assert ServerConfiguration.url(config, %{"env" => "staging"}) ==
               "https://staging.api.example.com"
    end

    test "accepts an override matching an enum constraint" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{
            default_value: "prod",
            enum_values: ["prod", "staging"]
          }
        }
      }

      assert ServerConfiguration.url(config, %{"env" => "staging"}) ==
               "https://staging.api.example.com"
    end

    test "rejects an override outside the enum with ArgumentError" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{
            default_value: "prod",
            enum_values: ["prod", "staging"]
          }
        }
      }

      assert_raise ArgumentError, fn ->
        ServerConfiguration.url(config, %{"env" => "dev"})
      end
    end
  end

  describe "Servers.all/0" do
    test "lists every server in declaration order, as SKILLS.md documents" do
      assert PetstoreClient.Servers.all() == [
               PetstoreClient.Servers.server_0(),
               PetstoreClient.Servers.server_1()
             ]

      assert File.read!("SKILLS.md") =~
               "returning a `ServerConfiguration`, plus a `PetstoreClient.Servers.all/0` list."
    end
  end
end
