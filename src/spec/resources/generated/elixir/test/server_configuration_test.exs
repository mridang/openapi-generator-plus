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

    test "bhv servers 003 accepts an override that is in the variable enum" do
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

    test "bhv servers 009 resolves the url template from the default variable values" do
      config = %ServerConfiguration{
        url_template: "https://{region}.{env}.example.com",
        variables: %{
          "region" => %ServerVariable{default_value: "eu"},
          "env" => %ServerVariable{default_value: "prod"}
        }
      }

      assert ServerConfiguration.url(config) == "https://eu.prod.example.com"
    end

    test "bhv servers 019 an enum accepts only its listed values and rejects any other" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{default_value: "prod", enum_values: ["prod", "staging"]}
        }
      }

      for allowed <- ["prod", "staging"] do
        assert ServerConfiguration.url(config, %{"env" => allowed}) ==
                 "https://#{allowed}.api.example.com"
      end

      assert_raise ArgumentError, fn ->
        ServerConfiguration.url(config, %{"env" => "dev"})
      end
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

    test "bhv misc 260 passing nil for an enum variable raises instead of substituting it" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{default_value: "prod", enum_values: ["prod", "staging"]}
        }
      }

      assert_raise ArgumentError, fn -> ServerConfiguration.url(config, %{"env" => nil}) end
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

    test "bhv langelixir 019 the allowed values of a three-element enum render as a bracketed comma list" do
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

    test "bhv servers 029 a placeholder introduced by a default value stays literal and is never expanded again" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com/{version}",
        variables: %{
          "env" => %ServerVariable{default_value: "{version}"},
          "version" => %ServerVariable{default_value: "v2"}
        }
      }

      assert ServerConfiguration.url(config) == "https://{version}.api.example.com/v2"
    end

    test "bhv servers 023 a server variable value containing replacement patterns is substituted literally" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{"env" => %ServerVariable{default_value: "a\\1b$&"}}
      }

      assert ServerConfiguration.url(config) == "https://a\\1b$&.api.example.com"
    end

    test "bhv misc 261 a value containing backslash sequences is inserted into the URL exactly as given" do
      value = "a\\n\\1\\\\b"

      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{"env" => %ServerVariable{default_value: "prod"}}
      }

      assert ServerConfiguration.url(config, %{"env" => value}) ==
               "https://#{value}.api.example.com"
    end

    test "bhv servers 007 leaves an unknown placeholder in the template untouched" do
      config = %ServerConfiguration{url_template: "https://{unknown}.example.com"}

      assert ServerConfiguration.url(config) == "https://{unknown}.example.com"
    end

    test "bhv misc 259 a placeholder with no declared variable stays in the URL exactly as written" do
      config = %ServerConfiguration{
        url_template: "https://{env}.{region}.api.example.com",
        variables: %{"env" => %ServerVariable{default_value: "prod"}}
      }

      assert ServerConfiguration.url(config) == "https://prod.{region}.api.example.com"
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

    test "bhv misc 258 without an override the default value of an enum variable is used in the URL" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{
          "env" => %ServerVariable{default_value: "staging", enum_values: ["prod", "staging"]}
        }
      }

      assert ServerConfiguration.url(config) == "https://staging.api.example.com"
    end

    test "bhv misc 257 a variable declared with an empty enum accepts any value" do
      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: %{"env" => %ServerVariable{default_value: "prod", enum_values: []}}
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

    test "bhv misc 297 every variable in the template is replaced when more than one is overridden" do
      config = %ServerConfiguration{
        url_template: "https://{env}.{region}.example.com/v{version}",
        variables: %{
          "env" => %ServerVariable{default_value: "prod"},
          "region" => %ServerVariable{default_value: "eu"},
          "version" => %ServerVariable{default_value: "1"}
        }
      }

      assert ServerConfiguration.url(config, %{
               "env" => "staging",
               "region" => "us",
               "version" => "2"
             }) ==
               "https://staging.us.example.com/v2"
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
    test "bhv servers 021 a server URL with special characters in its default and enum values resolves to the raw characters" do
      server = PetstoreClient.Servers.server_2()
      assert ServerConfiguration.url(server) == "https://o'neil.example.com/api"

      assert ServerConfiguration.url(server, %{"tenant" => "a&b"}) ==
               "https://a&b.example.com/api"
    end

    test "bhv servers 013 the third global server keeps its hostile text unchanged" do
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
    test "bhv misc 156 lists every server in declaration order, as SKILLS.md documents" do
      assert PetstoreClient.Servers.all() == [
               PetstoreClient.Servers.server_0(),
               PetstoreClient.Servers.server_1(),
               PetstoreClient.Servers.server_2()
             ]

      assert File.read!("SKILLS.md") =~
               "returning a `ServerConfiguration`, plus a `PetstoreClient.Servers.all/0` list."
    end
  end

  describe "copies and the generated server list" do
    test "bhv servers 025 changing the variables map after building leaves the configuration unchanged" do
      variables = %{"env" => %ServerVariable{default_value: "prod"}}

      config = %ServerConfiguration{
        url_template: "https://{env}.api.example.com",
        variables: variables
      }

      _changed = Map.put(variables, "env", %ServerVariable{default_value: "dev"})

      assert ServerConfiguration.url(config) == "https://prod.api.example.com"
    end

    test "bhv servers 026 changing the allowed values list after building leaves them unchanged" do
      enum_values = ["prod", "staging"]
      variable = %ServerVariable{default_value: "prod", enum_values: enum_values}

      _changed = List.delete(enum_values, "staging")

      assert variable.enum_values == ["prod", "staging"]
    end

    test "bhv servers 028 the generated server list cannot be changed after creation" do
      servers = PetstoreClient.Servers.all()

      assert Enum.all?(servers, &match?(%ServerConfiguration{}, &1))
      assert PetstoreClient.Servers.all() == servers
    end
  end
end
