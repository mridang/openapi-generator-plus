import { DefaultApiClient } from '../src/default-api-client';
import { Configuration } from '../src/configuration';
import * as zlib from 'node:zlib';

describe('DefaultApiClient', () => {
  describe('TLS verification disabled', () => {
    test('makes HTTPS request with verifySsl=false', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTPS_URL']!;

      const config = new Configuration({
        baseUrl: wiremockUrl,
        verifySsl: false,
      });

      const client = new DefaultApiClient(config);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/test`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('success');
    });
  });

  describe('custom CA bundle', () => {
    test('makes HTTPS request with custom CA cert', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTPS_URL']!;
      const caCertPath = process.env['CA_CERT_PATH']!;

      const config = new Configuration({
        baseUrl: wiremockUrl,
        verifySsl: true,
        sslCaCert: caCertPath,
      });

      const client = new DefaultApiClient(config);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/test`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('success');
    });
  });

  describe('HTTP proxy', () => {
    test('makes HTTP request through proxy', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;
      const proxyUrl = process.env['PROXY_URL']!;

      const config = new Configuration({
        baseUrl: wiremockUrl,
        proxy: proxyUrl,
      });

      const client = new DefaultApiClient(config);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/test`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('success');
    });
  });

  describe('HTTP proxy with TLS', () => {
    test('makes HTTPS request through proxy with verifySsl=false', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTPS_URL']!;
      const proxyUrl = process.env['PROXY_URL']!;

      const config = new Configuration({
        baseUrl: wiremockUrl,
        proxy: proxyUrl,
        verifySsl: false,
      });

      const client = new DefaultApiClient(config);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/test`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('success');
    });
  });

  describe('HTTP compression', () => {
    test('decompresses gzip response', async () => {
      const client = new DefaultApiClient();
      const response = await client.sendRequest(
        'GET',
        'https://jsonplaceholder.typicode.com/posts/1',
        { 'Accept-Encoding': 'gzip' },
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('userId');
    });

    test('decompresses brotli response', async () => {
      const client = new DefaultApiClient();
      const response = await client.sendRequest(
        'GET',
        'https://jsonplaceholder.typicode.com/posts/1',
        { 'Accept-Encoding': 'br' },
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('userId');
    });

    const zstdAvailable = typeof zlib.zstdDecompress === 'function';
    (zstdAvailable ? test : test.skip)('decompresses zstd response', async () => {
      const client = new DefaultApiClient();
      const response = await client.sendRequest(
        'GET',
        'https://jsonplaceholder.typicode.com/posts/1',
        { 'Accept-Encoding': 'zstd' },
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('userId');
    });
  });
});
