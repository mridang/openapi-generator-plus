import { GenericContainer, Network, Wait } from 'testcontainers';
import * as path from 'node:path';
import * as fs from 'node:fs';

export default async function globalSetup() {
  const hostAppPath = process.env.HOST_APP_PATH || process.cwd();
  const specPath = path.join(hostAppPath, 'test', 'fixtures', 'openapi.yaml');

  const prism = await new GenericContainer('mridang/chasm:1.2.2')
    .withExposedPorts(4010)
    .withBindMounts([{ source: specPath, target: '/tmp/openapi.yaml', mode: 'ro' }])
    .withCommand(['mock', '/tmp/openapi.yaml', '--host', '0.0.0.0'])
    .withWaitStrategy(Wait.forLogMessage('Listening on'))
    .withStartupTimeout(120000)
    .start();

  // Create a shared Docker network so Squid can reach WireMock directly
  // via container alias, avoiding host.docker.internal DNS issues.
  const proxyNetwork = await new Network().start();

  const keystorePath = path.join(hostAppPath, 'test', 'fixtures', 'certs', 'server-keystore.p12');
  const mappingsPath = path.join(hostAppPath, 'test', 'fixtures', 'wiremock', 'mappings');

  const wiremock = await new GenericContainer('wiremock/wiremock:3.13.0')
    .withExposedPorts(8080, 8443)
    .withBindMounts([
      { source: keystorePath, target: '/tmp/keystore.p12', mode: 'ro' },
      { source: mappingsPath, target: '/home/wiremock/mappings', mode: 'ro' },
    ])
    .withCommand([
      '--port', '8080',
      '--https-port', '8443',
      '--https-keystore', '/tmp/keystore.p12',
      '--keystore-type', 'PKCS12',
      '--keystore-password', 'changeit',
      '--key-manager-password', 'changeit',
      '--verbose',
    ])
    .withNetwork(proxyNetwork)
    .withNetworkAliases('wiremock')
    .withWaitStrategy(Wait.forLogMessage('port:'))
    .withStartupTimeout(120000)
    .start();

  const squidConfPath = path.join(hostAppPath, 'test', 'fixtures', 'proxy', 'squid.conf');

  const squid = await new GenericContainer('ubuntu/squid:5.2-22.04_beta')
    .withExposedPorts(3128)
    .withBindMounts([
      { source: squidConfPath, target: '/etc/squid/squid.conf', mode: 'ro' },
    ])
    .withNetwork(proxyNetwork)
    .withStartupTimeout(120000)
    .start();

  // Give Squid a moment to initialize
  await new Promise(resolve => setTimeout(resolve, 3000));

  const baseUrl = `http://${prism.getHost()}:${prism.getMappedPort(4010)}`;

  // Verify Prism is reachable before proceeding (Docker for Mac port forwarding can be slow)
  for (let i = 0; i < 10; i++) {
    try {
      await fetch(baseUrl);
      break;
    } catch {
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  }
  const wiremockHost = wiremock.getHost();
  const wiremockHttpsUrl = `https://${wiremockHost}:${wiremock.getMappedPort(8443)}`;
  const wiremockHttpUrl = `http://${wiremockHost}:${wiremock.getMappedPort(8080)}`;
  const wiremockInternalHttpUrl = 'http://wiremock:8080';
  const wiremockInternalHttpsUrl = 'https://wiremock:8443';
  const proxyUrl = `http://${squid.getHost()}:${squid.getMappedPort(3128)}`;
  const caCertPath = path.join(process.cwd(), 'test', 'fixtures', 'certs', 'ca.pem');

  fs.writeFileSync('/tmp/prism-config.json', JSON.stringify({
    baseUrl,
    wiremockHttpsUrl,
    wiremockHttpUrl,
    wiremockInternalHttpUrl,
    wiremockInternalHttpsUrl,
    proxyUrl,
    caCertPath,
  }));

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).__PRISM_CONTAINER__ = prism;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).__WIREMOCK_CONTAINER__ = wiremock;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).__SQUID_CONTAINER__ = squid;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).__PROXY_NETWORK__ = proxyNetwork;
}
