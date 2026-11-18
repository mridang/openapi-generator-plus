defmodule PetstoreClient.ConfigurationTest do
  use ExUnit.Case, async: true

  test "bhv config 005 new produces correct defaults" do
    config = PetstoreClient.Configuration.new()

    assert config.base_url == "/api/v3"
    assert config.default_headers == %{}
  end

  test "bhv config 011 builder sets base url" do
    config = PetstoreClient.Configuration.new(base_url: "https://custom.example.com")
    assert config.base_url == "https://custom.example.com"
  end

  test "bhv config 013 new sets default headers" do
    config =
      PetstoreClient.Configuration.new(default_headers: %{"Authorization" => "Bearer token123"})

    assert config.default_headers == %{"Authorization" => "Bearer token123"}
  end

  test "bhv langelixir 025 default_headers defaults to empty map" do
    config = PetstoreClient.Configuration.new()
    assert config.default_headers == %{}
  end

  test "bhv config 010 new sets all fields" do
    config =
      PetstoreClient.Configuration.new(
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

  test "bhv config 017 configuration is immutable: an update returns a new value and leaves the original" do
    config = PetstoreClient.Configuration.new(default_headers: %{"X-Key" => "value"})

    updated = %{config | default_headers: Map.put(config.default_headers, "X-New", "other")}

    assert config.default_headers == %{"X-Key" => "value"}
    assert updated.default_headers == %{"X-Key" => "value", "X-New" => "other"}
  end

  test "bhv config 012 builder sets multiple default headers" do
    config =
      PetstoreClient.Configuration.new(
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

  test "bhv config 006 builder produces independent instances" do
    config1 = PetstoreClient.Configuration.new(base_url: "https://one.example.com")
    config2 = PetstoreClient.Configuration.new(base_url: "https://two.example.com")

    refute config1.base_url == config2.base_url
  end

  test "bhv config 018 a default constructed or built configuration uses the base URL from the spec" do
    assert PetstoreClient.Configuration.new().base_url == "/api/v3"

    built = PetstoreClient.Configuration.new(default_headers: %{"X-Trace" => "1"})
    assert built.base_url == "/api/v3"
  end

  test "bhv config 019 the default configuration factory returns the spec base URL and no default headers" do
    config = PetstoreClient.Configuration.default()

    assert config.base_url == "/api/v3"
    assert config.default_headers == %{}
  end

  test "bhv config 020 a configuration built with a base URL and default headers sets both in one configuration" do
    config =
      PetstoreClient.Configuration.new(
        base_url: "https://custom.example.com",
        default_headers: %{"X-One" => "1", "X-Two" => "2"}
      )

    assert config.base_url == "https://custom.example.com"
    assert config.default_headers == %{"X-One" => "1", "X-Two" => "2"}
  end

  test "bhv config 021 a server URL template resolves its variables to their defaults" do
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

  test "bhv config 022 mutating the returned default headers does not change the configuration" do
    config = PetstoreClient.Configuration.new(default_headers: %{"X-One" => "1"})
    _changed = Map.put(config.default_headers, "X-Two", "2")

    assert config.default_headers == %{"X-One" => "1"}
  end

  test "bhv config 024 the configuration has no settable process-wide default" do
    Code.ensure_loaded!(PetstoreClient.Configuration)

    for name <- [:set_default, :put_default, :set_default_base_url, :set_default_headers] do
      refute function_exported?(PetstoreClient.Configuration, name, 1)
    end
  end

  test "bhv config 023 each call to the default configuration factory returns a fresh instance with the same base URL" do
    first = PetstoreClient.Configuration.default()
    second = PetstoreClient.Configuration.default()

    assert first == second
    assert first.base_url == second.base_url
  end

  test "bhv config 008 from_server resolves URL with default variables" do
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

  test "bhv config 009 from_server resolves URL with variable overrides" do
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

    config =
      PetstoreClient.Configuration.from_server(server, %{"env" => "staging", "version" => "v2"})

    assert config.base_url == "https://staging.example.com/api/v2"
  end

  test "bhv config 001 base_url overrides server" do
    server = %PetstoreClient.ServerConfiguration{
      url_template: "https://api.example.com"
    }

    config =
      PetstoreClient.Configuration.from_server(server)
      |> Map.put(:base_url, "https://override.example.com")

    assert config.base_url == "https://override.example.com"
  end

  test "bhv config 014 default returns an instance" do
    config = PetstoreClient.Configuration.default()
    assert %PetstoreClient.Configuration{} = config
    assert config.base_url == "/api/v3"
  end

  test "bhv config 016 from_server raises for invalid enum value" do
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

  test "bhv config 015 inspect masks sensitive default headers" do
    config =
      PetstoreClient.Configuration.new(
        default_headers: %{"Authorization" => "Bearer abc123", "Accept" => "application/json"}
      )

    shown = inspect(config)
    refute shown =~ "abc123"
    assert shown =~ "application/json"
    assert config.default_headers["Authorization"] == "Bearer abc123"
  end

  test "bhv skills 002 the skills guide documents the retries in its configuration section" do
    skills = File.read!("SKILLS.md")

    assert skills =~ ~r/## Configuration.*### Retries.*max_retries/s
  end

  defmodule SomeHook do
    @behaviour PetstoreClient.CallHook

    @impl true
    def start(_info, headers), do: {headers, nil}

    @impl true
    def finish(_state, _outcome), do: :ok
  end

  test "bhv langelixir 026 a call hook is none by default, set by call_hook and never printed" do
    assert PetstoreClient.Configuration.new().call_hook == nil

    config = PetstoreClient.Configuration.new(call_hook: {SomeHook, %{password: "hunter2"}})

    assert config.call_hook == {SomeHook, %{password: "hunter2"}}
    shown = inspect(config)
    refute shown =~ "hunter2"
    refute shown =~ "SomeHook"
    assert shown =~ "call_hook: :set"
  end

  test "bhv config 026 a configuration built through the builder entry point returns the base URL that was set" do
    config = PetstoreClient.Configuration.new(base_url: "https://builder.example.com")

    assert config.base_url == "https://builder.example.com"
  end

  describe "misc parity four" do
    test "bhv misc 136 the debug text shows the base URL and the non-sensitive default headers" do
      config =
        PetstoreClient.Configuration.new(
          base_url: "https://api.example.com",
          default_headers: %{"Accept" => "text/plain"}
        )

      text = inspect(config)
      assert text =~ "https://api.example.com"
      assert text =~ "text/plain"
    end
  end
end
