import { GenericContainer, Wait } from 'testcontainers';
import * as path from 'path';
import * as fs from 'fs';

export default async function globalSetup() {
  const hostAppPath = process.env.HOST_APP_PATH || process.cwd();
  const specPath = path.join(hostAppPath, 'specs', 'openapi.yaml');

  const prism = await new GenericContainer('stoplight/prism:5')
    .withExposedPorts(4010)
    .withBindMounts([{ source: specPath, target: '/tmp/openapi.yaml', mode: 'ro' }])
    .withCommand(['mock', '-h', '0.0.0.0', '/tmp/openapi.yaml'])
    .withWaitStrategy(Wait.forLogMessage('Prism is listening'))
    .withStartupTimeout(120000)
    .start();

  const keystorePath = path.join(hostAppPath, 'certs', 'server-keystore.p12');
  const mappingsPath = path.join(hostAppPath, 'wiremock', 'mappings');

  const wiremock = await new GenericContainer('wiremock/wiremock:3.13.0')
    .withExposedPorts(8080, 8443)
    .withBindMounts([
      { source: keystorePath, target: '/tmp/keystore.p12', mode: 'ro' },
      { source: mappingsPath, target: '/home/wiremock/mappings', mode: 'ro' }
    ])
    .withCommand([
      '--port',
      '8080',
      '--https-port',
      '8443',
      '--https-keystore',
      '/tmp/keystore.p12',
      '--keystore-type',
      'PKCS12',
      '--keystore-password',
      'changeit',
      '--key-manager-password',
      'changeit',
      '--verbose'
    ])
    .withWaitStrategy(Wait.forLogMessage('port:'))
    .withStartupTimeout(120000)
    .start();

  const squidConfPath = path.join(hostAppPath, 'proxy', 'squid.conf');

  const squid = await new GenericContainer('ubuntu/squid:5.2-22.04_beta')
    .withExposedPorts(3128)
    .withBindMounts([{ source: squidConfPath, target: '/etc/squid/squid.conf', mode: 'ro' }])
    .withStartupTimeout(120000)
    .start();

  // Give Squid a moment to initialize
  await new Promise((resolve) => setTimeout(resolve, 3000));

  const baseUrl = `http://${prism.getHost()}:${prism.getMappedPort(4010)}`;
  const wiremockHost = wiremock.getHost();
  const wiremockHttpsUrl = `https://${wiremockHost}:${wiremock.getMappedPort(8443)}`;
  const wiremockHttpUrl = `http://${wiremockHost}:${wiremock.getMappedPort(8080)}`;
  const proxyUrl = `http://${squid.getHost()}:${squid.getMappedPort(3128)}`;
  const caCertPath = path.join(process.cwd(), 'certs', 'ca.pem');

  fs.writeFileSync(
    '/tmp/prism-config.json',
    JSON.stringify({
      baseUrl,
      wiremockHttpsUrl,
      wiremockHttpUrl,
      proxyUrl,
      caCertPath
    })
  );

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).__PRISM_CONTAINER__ = prism;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).__WIREMOCK_CONTAINER__ = wiremock;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).__SQUID_CONTAINER__ = squid;
}
