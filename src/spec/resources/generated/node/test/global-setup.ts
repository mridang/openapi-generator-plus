import type { StartedTestContainer, StartedNetwork } from "testcontainers";
import * as path from "node:path";
import * as fs from "node:fs";

/* Every dependency an emitted test imports is listed, with the test that needs
 * it, in .openapi-generator/DEV-DEPENDENCIES. A client keep-lists its own
 * package.json, so a dependency the generator started using is missing there
 * until someone copies it across -- and the whole suite then dies at
 * collection with a module-not-found from whichever test loaded first. Probing
 * them together here, before the first value import, turns that into one
 * message naming the file to reconcile against. `testcontainers` is imported
 * for its types only above and loaded dynamically below so this check runs
 * before the failure it is reporting on. */
const DEV_DEPENDENCIES = ".openapi-generator/DEV-DEPENDENCIES";

const REQUIRED_TEST_PACKAGES = [
  "@opentelemetry/api",
  "@opentelemetry/context-async-hooks",
  "@opentelemetry/core",
  "@opentelemetry/sdk-trace-base",
  "testcontainers",
];

async function assertTestDependenciesInstalled(): Promise<void> {
  const missing: string[] = [];
  for (const name of REQUIRED_TEST_PACKAGES) {
    try {
      await import(name);
    } catch {
      missing.push(name);
    }
  }
  if (missing.length > 0) {
    throw new Error(
      `Missing test dependencies: ${missing.join(", ")}. Every dependency the generated ` +
        `tests import is listed in ${DEV_DEPENDENCIES}; add the missing entries to this ` +
        `project's package.json devDependencies and reinstall.`,
    );
  }
}

declare global {
  var __CHASM_CONTAINER__: StartedTestContainer | undefined;
  var __SQUID_CONTAINER__: StartedTestContainer | undefined;
  var __PROXY_NETWORK__: StartedNetwork | undefined;
}

// Under CI load Docker can be slow to publish a container's ports to the host,
// and testcontainers' port-binding wait then times out (e.g. "Timed out after
// 10000ms while waiting for container ports to be bound to the host"). Retry the
// whole start a few times — a fresh attempt usually wins once the host quiesces.
async function startWithRetry<T extends StartedTestContainer>(
  label: string,
  start: () => Promise<T>,
  attempts = 4,
): Promise<T> {
  let lastErr: unknown;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      return await start();
    } catch (err) {
      lastErr = err;
      console.warn(
        `${label} container start attempt ${attempt}/${attempts} failed: ${String(err)}`,
      );
      await new Promise((resolve) => setTimeout(resolve, 3000));
    }
  }
  throw lastErr;
}

export default async function globalSetup() {
  await assertTestDependenciesInstalled();
  const { GenericContainer, Network, Wait } = await import("testcontainers");

  const hostAppPath = process.env.HOST_APP_PATH || process.cwd();
  const specPath = path.join(hostAppPath, "test", "fixtures", "openapi.yaml");
  const chasmCertPath = path.join(
    hostAppPath,
    "test",
    "fixtures",
    "certs",
    "server.pem",
  );
  const chasmKeyPath = path.join(
    hostAppPath,
    "test",
    "fixtures",
    "certs",
    "server-key.pem",
  );

  // Create a shared Docker network so Squid can reach Chasm directly
  // via container alias, avoiding host.docker.internal DNS issues.
  const proxyNetwork = await new Network().start();

  const chasm = await startWithRetry("chasm", () =>
    new GenericContainer("mridang/chasm:1.3.0")
      .withExposedPorts(4010, 8443)
      .withBindMounts([
        { source: specPath, target: "/tmp/openapi.yaml", mode: "ro" },
        { source: chasmCertPath, target: "/certs/cert.pem", mode: "ro" },
        { source: chasmKeyPath, target: "/certs/key.pem", mode: "ro" },
      ])
      .withCommand([
        "mock",
        "/tmp/openapi.yaml",
        "--host",
        "0.0.0.0",
        "--tls-cert",
        "/certs/cert.pem",
        "--tls-key",
        "/certs/key.pem",
        "--tls-port",
        "8443",
      ])
      .withNetwork(proxyNetwork)
      .withNetworkAliases("chasm")
      .withWaitStrategy(Wait.forLogMessage("Listening on"))
      .withStartupTimeout(120000)
      .start(),
  );

  const squidConfPath = path.join(
    hostAppPath,
    "test",
    "fixtures",
    "proxy",
    "squid.conf",
  );

  /* ubuntu/squid declares VOLUME /var/log/squid and VOLUME /var/spool/squid,
   * so every proxy container Docker creates leaves two anonymous volumes
   * behind after the suite stops it. Mounting both as tmpfs keeps the
   * container writable without allocating a volume. mode=1777 because squid
   * drops to the unprivileged `proxy` user before it opens its logs. */
  const squid = await startWithRetry("squid", () =>
    new GenericContainer("ubuntu/squid:5.2-22.04_beta")
      .withExposedPorts(3128, 3129)
      .withTmpFs({
        "/var/log/squid": "rw,mode=1777",
        "/var/spool/squid": "rw,mode=1777",
      })
      .withBindMounts([
        { source: squidConfPath, target: "/etc/squid/squid.conf", mode: "ro" },
      ])
      .withNetwork(proxyNetwork)
      .withStartupTimeout(120000)
      .start(),
  );

  // Give Squid a moment to initialize
  await new Promise((resolve) => setTimeout(resolve, 3000));

  const chasmHost = chasm.getHost();
  const chasmHttpUrl = `http://${chasmHost}:${chasm.getMappedPort(4010)}`;
  const chasmHttpsUrl = `https://${chasmHost}:${chasm.getMappedPort(8443)}`;
  const chasmInternalHttpUrl = "http://chasm:4010";
  const chasmInternalHttpsUrl = "https://chasm:8443";
  const baseUrl = chasmHttpUrl;

  // Verify Chasm is reachable before proceeding (Docker for Mac port forwarding can be slow)
  for (let i = 0; i < 10; i++) {
    try {
      await fetch(baseUrl);
      break;
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }
  const proxyUrl = `http://${squid.getHost()}:${squid.getMappedPort(3128)}`;
  // host:port of the fixture proxy port that requires Basic proxy credentials.
  const proxyAuthHostPort = `${squid.getHost()}:${squid.getMappedPort(3129)}`;
  const caCertPath = path.join(
    process.cwd(),
    "test",
    "fixtures",
    "certs",
    "ca.pem",
  );

  fs.writeFileSync(
    "/tmp/chasm-config.json",
    JSON.stringify({
      baseUrl,
      chasmHttpUrl,
      chasmHttpsUrl,
      chasmInternalHttpUrl,
      chasmInternalHttpsUrl,
      proxyUrl,
      proxyAuthHostPort,
      caCertPath,
    }),
  );

  globalThis.__CHASM_CONTAINER__ = chasm;
  globalThis.__SQUID_CONTAINER__ = squid;
  globalThis.__PROXY_NETWORK__ = proxyNetwork;
}
