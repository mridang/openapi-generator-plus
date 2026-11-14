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

    test "the rejection names the value, the variable and the allowed values" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{default_value: "prod", enum_values: ["prod", "staging"]}
        }
      }

      assert_raise ArgumentError,
                   ~s(Invalid value 'dev' for server variable 'env'. Allowed values: ["prod", "staging"]),
                   fn -> ServerConfiguration.url(config, %{"env" => "dev"}) end
    end

    test "a default outside the enum is rejected" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{default_value: "dev", enum_values: ["prod", "staging"]}
        }
      }

      assert_raise ArgumentError,
                   ~s(Invalid value 'dev' for server variable 'env'. Allowed values: ["prod", "staging"]),
                   fn -> ServerConfiguration.url(config) end
    end

    test "a variable with no enum accepts any override" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{"env" => %ServerVariable{default_value: "prod"}}
      }

      assert ServerConfiguration.url(config, %{"env" => "anything-goes"}) ==
               "https://anything-goes.api.example.com"
    end

    test "a partial override keeps the other variables' defaults" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com/v{version}",
        variables: %{
          "env" => %ServerVariable{default_value: "prod"},
          "version" => %ServerVariable{default_value: "2"}
        }
      }

      assert ServerConfiguration.url(config, %{"version" => "3"}) ==
               "https://prod.api.example.com/v3"
    end

    test "a variable repeated in the template is replaced everywhere" do
      config = %ServerConfiguration{
        url_template: "https://{region}.example.com/{region}/v1",
        variables: %{"region" => %ServerVariable{default_value: "eu"}}
      }

      assert ServerConfiguration.url(config) == "https://eu.example.com/eu/v1"
    end

    test "exposes the raw template and description" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        description: "Deployment server",
        variables: %{"env" => %ServerVariable{default_value: "prod"}}
      }

      assert config.url_template == "https://{env}.api.example.com"
      assert config.description == "Deployment server"
    end
  end

  describe "Servers.all/0" do
    test "lists every server in declaration order, as SKILLS.md documents" do
      assert PetstoreClient.Servers.all() == [
               PetstoreClient.Servers.server_0(),
               PetstoreClient.Servers.server_1(),
               PetstoreClient.Servers.server_2()
             ]

      assert File.read!("SKILLS.md") =~
               "returning a `ServerConfiguration`, plus a `PetstoreClient.Servers.all/0` list."
    end
  end
end
