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

  test "default_headers defaults to empty map" do
    config = PetstoreClient.Configuration.new()
    assert config.default_headers == %{}
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

  test "new sets multiple default headers" do
    config = PetstoreClient.Configuration.new(
      default_headers: %{
        "X-First" => "one",
        "X-Second" => "two",
        "X-Third" => "three"
      }
    )

    assert map_size(config.default_headers) == 3
    assert config.default_headers["X-First"] == "one"
    assert config.default_headers["X-Second"] == "two"
    assert config.default_headers["X-Third"] == "three"
  end

  test "new creates independent instances" do
    config1 = PetstoreClient.Configuration.new(base_url: "https://one.example.com")
    config2 = PetstoreClient.Configuration.new(base_url: "https://two.example.com")

    refute config1.base_url == config2.base_url
  end

  test "default returns consistent configuration" do
    first = PetstoreClient.Configuration.default()
    second = PetstoreClient.Configuration.default()

    assert first == second
    assert first.base_url == second.base_url
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

  test "from_server raises for invalid enum value" do
    server = %PetstoreClient.ServerConfiguration{
      url_template: "https://{env}.example.com",
      variables: %{
        "env" => %PetstoreClient.ServerVariable{
          default_value: "api",
          enum_values: ["api", "staging"]
        }
      }
    }

    assert_raise ArgumentError, fn ->
      PetstoreClient.Configuration.from_server(server, %{"env" => "invalid"})
    end
  end
end
