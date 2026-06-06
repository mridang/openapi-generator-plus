import { GenericContainer, Network, Wait, StartedTestContainer, StartedNetwork } from 'testcontainers';
import * as path from 'node:path';
import * as fs from 'node:fs';

declare global {
  var __CHASM_CONTAINER__: StartedTestContainer | undefined;
  var __SQUID_CONTAINER__: StartedTestContainer | undefined;
  var __PROXY_NETWORK__: StartedNetwork | undefined;
}

export default async function globalSetup() {
  const hostAppPath = process.env.HOST_APP_PATH || process.cwd();
  const specPath = path.join(hostAppPath, 'test', 'fixtures', 'openapi.yaml');
  const chasmCertPath = path.join(hostAppPath, 'test', 'fixtures', 'certs', 'server.pem');
  const chasmKeyPath = path.join(hostAppPath, 'test', 'fixtures', 'certs', 'server-key.pem');

  // Create a shared Docker network so Squid can reach Chasm directly
  // via container alias, avoiding host.docker.internal DNS issues.
  const proxyNetwork = await new Network().start();

  const chasm = await new GenericContainer('mridang/chasm:1.3.0')
    .withExposedPorts(4010, 8443)
    .withBindMounts([
      { source: specPath, target: '/tmp/openapi.yaml', mode: 'ro' },
      { source: chasmCertPath, target: '/certs/cert.pem', mode: 'ro' },
      { source: chasmKeyPath, target: '/certs/key.pem', mode: 'ro' },
    ])
    .withCommand([
      'mock', '/tmp/openapi.yaml',
      '--host', '0.0.0.0',
      '--tls-cert', '/certs/cert.pem',
      '--tls-key', '/certs/key.pem',
      '--tls-port', '8443',
    ])
    .withNetwork(proxyNetwork)
    .withNetworkAliases('chasm')
    .withWaitStrategy(Wait.forLogMessage('Listening on'))
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

  const chasmHost = chasm.getHost();
  const chasmHttpUrl = `http://${chasmHost}:${chasm.getMappedPort(4010)}`;
  const chasmHttpsUrl = `https://${chasmHost}:${chasm.getMappedPort(8443)}`;
  const chasmInternalHttpUrl = 'http://chasm:4010';
  const chasmInternalHttpsUrl = 'https://chasm:8443';
  const baseUrl = chasmHttpUrl;

  // Verify Chasm is reachable before proceeding (Docker for Mac port forwarding can be slow)
  for (let i = 0; i < 10; i++) {
    try {
      await fetch(baseUrl);
      break;
    } catch {
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  }
  const proxyUrl = `http://${squid.getHost()}:${squid.getMappedPort(3128)}`;
  const caCertPath = path.join(process.cwd(), 'test', 'fixtures', 'certs', 'ca.pem');

  fs.writeFileSync('/tmp/chasm-config.json', JSON.stringify({
    baseUrl,
    chasmHttpUrl,
    chasmHttpsUrl,
    chasmInternalHttpUrl,
    chasmInternalHttpsUrl,
    proxyUrl,
    caCertPath,
  }));

  globalThis.__CHASM_CONTAINER__ = chasm;
  globalThis.__SQUID_CONTAINER__ = squid;
  globalThis.__PROXY_NETWORK__ = proxyNetwork;
}
