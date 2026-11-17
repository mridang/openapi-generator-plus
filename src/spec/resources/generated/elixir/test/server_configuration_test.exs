defmodule PetstoreClient.ServerConfigurationTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.ServerConfiguration
  alias PetstoreClient.ServerVariable

  describe "url/2" do
    test "bhv servers 011 resolves a plain template with no variables unchanged" do
      config = %ServerConfiguration{url_template: "https://api.example.com/v1"}

      assert ServerConfiguration.url(config) == "https://api.example.com/v1"
    end

    test "bhv servers 010 substitutes variables using their default values" do
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

    test "bhv servers 008 rejects an override outside the enum with ArgumentError" do
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

    test "bhv servers 012 the rejection names the value, the variable and the allowed values" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{default_value: "prod", enum_values: ["prod", "staging"]}
        }
      }

      assert_raise ArgumentError,
                   ~s(Invalid value 'dev' for server variable 'env'. Allowed values: [prod, staging]),
                   fn -> ServerConfiguration.url(config, %{"env" => "dev"}) end
    end

    test "the allowed values of a three-element enum render as a bracketed comma list" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{default_value: "a", enum_values: ["a", "b", "c"]}
        }
      }

      assert_raise ArgumentError,
                   ~s(Invalid value 'z' for server variable 'env'. Allowed values: [a, b, c]),
                   fn -> ServerConfiguration.url(config, %{"env" => "z"}) end
    end

    test "bhv servers 005 a placeholder introduced by a substituted value is not expanded again" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com/{version}",
        variables: %{
          "env" => %ServerVariable{default_value: "prod"},
          "version" => %ServerVariable{default_value: "v2"}
        }
      }

      assert ServerConfiguration.url(config, %{"env" => "{version}"}) ==
               "https://{version}.api.example.com/v2"
    end

    test "a placeholder with no declared variable is left as written" do
      config = %ServerConfiguration{url_template: "https://{unknown}.example.com"}

      assert ServerConfiguration.url(config) == "https://{unknown}.example.com"
    end

    test "bhv servers 004 a default outside the enum is rejected" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{default_value: "dev", enum_values: ["prod", "staging"]}
        }
      }

      assert_raise ArgumentError,
                   ~s(Invalid value 'dev' for server variable 'env'. Allowed values: [prod, staging]),
                   fn -> ServerConfiguration.url(config) end
    end

    test "bhv servers 014 a variable with no enum accepts any override" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{"env" => %ServerVariable{default_value: "prod"}}
      }

      assert ServerConfiguration.url(config, %{"env" => "anything-goes"}) ==
               "https://anything-goes.api.example.com"
    end

    test "bhv servers 001 a partial override keeps the other variables' defaults" do
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

    test "bhv servers 002 a variable repeated in the template is replaced everywhere" do
      config = %ServerConfiguration{
        url_template: "https://{region}.example.com/{region}/v1",
        variables: %{"region" => %ServerVariable{default_value: "eu"}}
      }

      assert ServerConfiguration.url(config) == "https://eu.example.com/eu/v1"
    end

    test "bhv servers 006 exposes the raw template and description" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        description: "Deployment server",
        variables: %{"env" => %ServerVariable{default_value: "prod"}}
      }

      assert config.url_template == "https://{env}.api.example.com"
      assert config.description == "Deployment server"
    end
  end

  describe "the hostile third global server" do
    test "its url, description, default, variable description and enum values come through unchanged" do
      config = PetstoreClient.Servers.server_2()
      tenant = config.variables["tenant"]

      assert config.url_template == "https://{tenant}.example.com/api"
      assert config.description == ~S(Bob's "sandbox" & co: $5 #{x} a*/b)
      assert tenant.default_value == "o'neil"
      assert tenant.description == ~S(Tenant's "id")
      assert tenant.enum_values == ["o'neil", "a&b"]
      assert ServerConfiguration.url(config) == "https://o'neil.example.com/api"
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
