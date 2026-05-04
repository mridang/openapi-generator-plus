defmodule PetstoreClient.ConfigurationTest do
  use ExUnit.Case, async: true

  test "new produces correct defaults" do
    config = PetstoreClient.Configuration.new()

    assert config.base_url == "/api/v3"
    assert config.default_headers == %{}
  end

  test "new sets base_url" do
    config = PetstoreClient.Configuration.new(base_url: "https://custom.example.com")
    assert config.base_url == "https://custom.example.com"
  end

  test "new sets default headers" do
    config = PetstoreClient.Configuration.new(
      default_headers: %{"Authorization" => "Bearer token123"}
    )

    assert config.default_headers == %{"Authorization" => "Bearer token123"}
  end

  test "new sets all fields" do
    config = PetstoreClient.Configuration.new(
      base_url: "https://api.example.com",
      default_headers: %{
        "Authorization" => "Bearer token",
        "X-Custom" => "value"
      }
    )

    assert config.base_url == "https://api.example.com"
    assert config.default_headers["Authorization"] == "Bearer token"
    assert config.default_headers["X-Custom"] == "value"
  end

  test "from_server resolves URL with default variables" do
    server = %PetstoreClient.ServerConfiguration{
      url_template: "https://{env}.example.com/api/{version}",
      description: "Test server",
      variables: %{
        "env" => %PetstoreClient.ServerVariable{
          default_value: "api",
          enum_values: ["api", "staging"]
        },
        "version" => %PetstoreClient.ServerVariable{
          default_value: "v3",
          enum_values: ["v2", "v3"]
        }
      }
    }

    config = PetstoreClient.Configuration.from_server(server)
    assert config.base_url == "https://api.example.com/api/v3"
  end

  test "from_server resolves URL with variable overrides" do
    server = %PetstoreClient.ServerConfiguration{
      url_template: "https://{env}.example.com/api/{version}",
      variables: %{
        "env" => %PetstoreClient.ServerVariable{
          default_value: "api",
          enum_values: ["api", "staging"]
        },
        "version" => %PetstoreClient.ServerVariable{
          default_value: "v3",
          enum_values: ["v2", "v3"]
        }
      }
    }

    config = PetstoreClient.Configuration.from_server(server, %{"env" => "staging", "version" => "v2"})
    assert config.base_url == "https://staging.example.com/api/v2"
  end

  test "base_url overrides server" do
    server = %PetstoreClient.ServerConfiguration{
      url_template: "https://api.example.com"
    }

    config =
      PetstoreClient.Configuration.from_server(server)
      |> Map.put(:base_url, "https://override.example.com")

    assert config.base_url == "https://override.example.com"
  end

  test "default returns an instance" do
    config = PetstoreClient.Configuration.default()
    assert %PetstoreClient.Configuration{} = config
    assert config.base_url == "/api/v3"
  end
end
