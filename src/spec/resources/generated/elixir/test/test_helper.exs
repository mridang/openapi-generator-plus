ExUnit.start(formatters: [ExUnit.CLIFormatter, JUnitFormatter])

Application.put_env(:junit_formatter, :report_dir, ".out/reports")
Application.put_env(:junit_formatter, :report_file, "junit.xml")

System.put_env("TESTCONTAINERS_RYUK_DISABLED", "true")
Process.flag(:trap_exit, true)
{:ok, _} = Testcontainers.start_link()

host_app_path = System.get_env("HOST_APP_PATH", File.cwd!())
spec_path = Path.join([host_app_path, "test", "fixtures", "openapi.yaml"])
keystore_path = Path.join([host_app_path, "test", "fixtures", "certs", "server-keystore.p12"])
mappings_path = Path.join([host_app_path, "test", "fixtures", "wiremock", "mappings"])
squid_conf_path = Path.join([host_app_path, "test", "fixtures", "proxy", "squid.conf"])

# Create shared network for Squid → WireMock communication
network_name = "proxy-network-elixir"
{:ok, _} = Testcontainers.create_network(network_name)

# Start Prism mock server
prism_config =
  Testcontainers.Container.new("mridang/chasm:1.2.4")
  |> Testcontainers.Container.with_exposed_port(4010)
  |> Testcontainers.Container.with_bind_mount(spec_path, "/tmp/openapi.yaml")
  |> Testcontainers.Container.with_cmd(["mock", "/tmp/openapi.yaml", "--host", "0.0.0.0"])
  |> Testcontainers.Container.with_waiting_strategy(Testcontainers.LogWaitStrategy.new(~r/Listening on/, 120_000))

{:ok, prism} = Testcontainers.start_container(prism_config)

prism_host = System.get_env("TESTCONTAINERS_HOST_OVERRIDE") || Testcontainers.get_host()
prism_port = Testcontainers.Container.mapped_port(prism, 4010)
prism_url = "http://#{prism_host}:#{prism_port}"
System.put_env("API_BASE_URL", prism_url)

# Start WireMock with HTTPS support
wiremock_config =
  Testcontainers.Container.new("wiremock/wiremock:3.13.0")
  |> Testcontainers.Container.with_exposed_port(8080)
  |> Testcontainers.Container.with_exposed_port(8443)
  |> Testcontainers.Container.with_bind_mount(keystore_path, "/tmp/keystore.p12")
  |> Testcontainers.Container.with_bind_mount(mappings_path, "/home/wiremock/mappings")
  |> Testcontainers.Container.with_cmd([
    "--port",
    "8080",
    "--https-port",
    "8443",
    "--https-keystore",
    "/tmp/keystore.p12",
    "--keystore-type",
    "PKCS12",
    "--keystore-password",
    "changeit",
    "--key-manager-password",
    "changeit",
    "--verbose"
  ])
  |> Testcontainers.Container.with_waiting_strategy(Testcontainers.LogWaitStrategy.new(~r/port:/, 120_000))

{:ok, wiremock} = Testcontainers.start_container(wiremock_config)

# Connect WireMock to the shared proxy network with an alias so that Squid can
# reach it by hostname via Docker's embedded DNS.
{docker_api_conn, _, _} = Testcontainers.Connection.get_connection()

{:ok, _} =
  DockerEngineAPI.Api.Network.network_connect(
    docker_api_conn,
    network_name,
    %DockerEngineAPI.Model.NetworkConnectRequest{
      Container: wiremock.container_id,
      EndpointConfig: %DockerEngineAPI.Model.EndpointSettings{
        Aliases: ["wiremock"]
      }
    }
  )

wiremock_host = System.get_env("TESTCONTAINERS_HOST_OVERRIDE") || Testcontainers.get_host()
wiremock_http_port = Testcontainers.Container.mapped_port(wiremock, 8080)
wiremock_https_port = Testcontainers.Container.mapped_port(wiremock, 8443)

System.put_env("WIREMOCK_HTTP_URL", "http://#{wiremock_host}:#{wiremock_http_port}")
System.put_env("WIREMOCK_HTTPS_URL", "https://#{wiremock_host}:#{wiremock_https_port}")
System.put_env("WIREMOCK_INTERNAL_HTTP_URL", "http://wiremock:8080")
System.put_env("WIREMOCK_INTERNAL_HTTPS_URL", "https://wiremock:8443")
System.put_env("CA_CERT_PATH", Path.join([File.cwd!(), "test", "fixtures", "certs", "ca.pem"]))

# Start Squid proxy on the same network as WireMock
squid_config =
  Testcontainers.Container.new("ubuntu/squid:5.2-22.04_beta")
  |> Testcontainers.Container.with_exposed_port(3128)
  |> Testcontainers.Container.with_bind_mount(squid_conf_path, "/etc/squid/squid.conf")
  |> Testcontainers.Container.with_network(network_name)

{:ok, squid} = Testcontainers.start_container(squid_config)

Process.sleep(3000)

squid_host = System.get_env("TESTCONTAINERS_HOST_OVERRIDE") || Testcontainers.get_host()
squid_port = Testcontainers.Container.mapped_port(squid, 3128)
System.put_env("PROXY_URL", "http://#{squid_host}:#{squid_port}")
