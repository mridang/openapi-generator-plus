ExUnit.start(formatters: [ExUnit.CLIFormatter, JUnitFormatter])

Application.put_env(:junit_formatter, :report_dir, ".out/reports")
Application.put_env(:junit_formatter, :report_file, "junit.xml")

System.put_env("TESTCONTAINERS_RYUK_DISABLED", "true")
Process.flag(:trap_exit, true)
{:ok, _} = Testcontainers.start_link()

host_app_path = System.get_env("HOST_APP_PATH", File.cwd!())
spec_path = Path.join([host_app_path, "test", "fixtures", "openapi.yaml"])
chasm_cert_path = Path.join([host_app_path, "test", "fixtures", "certs", "server.pem"])
chasm_key_path = Path.join([host_app_path, "test", "fixtures", "certs", "server-key.pem"])
squid_conf_path = Path.join([host_app_path, "test", "fixtures", "proxy", "squid.conf"])

# Create shared network for Squid → Chasm communication
network_name = "proxy-network-elixir"
{:ok, _} = Testcontainers.create_network(network_name)

# Start Chasm mock server
chasm_config =
  Testcontainers.Container.new("mridang/chasm:1.3.0")
  |> Testcontainers.Container.with_exposed_port(4010)
  |> Testcontainers.Container.with_exposed_port(8443)
  |> Testcontainers.Container.with_bind_mount(spec_path, "/tmp/openapi.yaml")
  |> Testcontainers.Container.with_bind_mount(chasm_cert_path, "/certs/cert.pem", "ro")
  |> Testcontainers.Container.with_bind_mount(chasm_key_path, "/certs/key.pem", "ro")
  |> Testcontainers.Container.with_cmd([
    "mock",
    "/tmp/openapi.yaml",
    "--host",
    "0.0.0.0",
    "--tls-cert",
    "/certs/cert.pem",
    "--tls-key",
    "/certs/key.pem",
    "--tls-port",
    "8443"
  ])
  |> Testcontainers.Container.with_waiting_strategy(Testcontainers.LogWaitStrategy.new(~r/Listening on/, 120_000))

{:ok, chasm} = Testcontainers.start_container(chasm_config)

# Connect Chasm to the shared proxy network with an alias so other containers
# can reach it by hostname via Docker's embedded DNS.
{docker_api_conn, _, _} = Testcontainers.Connection.get_connection()

{:ok, _} =
  DockerEngineAPI.Api.Network.network_connect(
    docker_api_conn,
    network_name,
    %DockerEngineAPI.Model.NetworkConnectRequest{
      Container: chasm.container_id,
      EndpointConfig: %DockerEngineAPI.Model.EndpointSettings{
        Aliases: ["chasm"]
      }
    }
  )

chasm_host = System.get_env("TESTCONTAINERS_HOST_OVERRIDE") || Testcontainers.get_host()
chasm_port = Testcontainers.Container.mapped_port(chasm, 4010)
chasm_https_port = Testcontainers.Container.mapped_port(chasm, 8443)
chasm_url = "http://#{chasm_host}:#{chasm_port}"
System.put_env("API_BASE_URL", chasm_url)
System.put_env("CHASM_HTTP_URL", "http://#{chasm_host}:#{chasm_port}")
System.put_env("CHASM_HTTPS_URL", "https://#{chasm_host}:#{chasm_https_port}")
System.put_env("CHASM_INTERNAL_HTTP_URL", "http://chasm:4010")
System.put_env("CHASM_INTERNAL_HTTPS_URL", "https://chasm:8443")
System.put_env("CA_CERT_PATH", Path.join([File.cwd!(), "test", "fixtures", "certs", "ca.pem"]))

# Start Squid proxy on the same network as Chasm
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

# Ryuk (the testcontainers reaper) is disabled above, so nothing tears the
# chasm/squid containers or the shared network down when the suite ends.
# Without this they leak as running containers that keep holding their host
# ports, so the next `mix test` run hangs waiting to bind. Stop them
# explicitly after the suite finishes (runs on pass or failure).
ExUnit.after_suite(fn _results ->
  Testcontainers.stop_container(squid.container_id)
  Testcontainers.stop_container(chasm.container_id)

  DockerEngineAPI.Api.Network.network_delete(docker_api_conn, network_name)

  :ok
end)
