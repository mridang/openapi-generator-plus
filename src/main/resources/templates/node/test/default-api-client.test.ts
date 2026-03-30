import { DefaultApiClient } from '../src/default-api-client';
import { TransportOptions } from '../src/transport-options';
import * as zlib from 'node:zlib';

describe('DefaultApiClient', () => {
  describe('TLS verification disabled', () => {
    test('makes HTTPS request with verifySsl=false', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTPS_URL']!;

      const transport = TransportOptions.builder()
        .verifySsl(false)
        .build();

      const client = new DefaultApiClient(transport);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/test`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('success');
    }, 30000);
  });

  describe('custom CA bundle', () => {
    test('makes HTTPS request with custom CA cert', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTPS_URL']!;
      const caCertPath = process.env['CA_CERT_PATH']!;

      const transport = TransportOptions.builder()
        .verifySsl(true)
        .caCertPath(caCertPath)
        .build();

      const client = new DefaultApiClient(transport);
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

      const transport = TransportOptions.builder()
        .proxy(proxyUrl)
        .build();

      const client = new DefaultApiClient(transport);
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

      const transport = TransportOptions.builder()
        .proxy(proxyUrl)
        .verifySsl(false)
        .build();

      const client = new DefaultApiClient(transport);
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

  describe('request timeout', () => {
    test('times out on slow endpoint', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;

      const transport = TransportOptions.builder()
        .timeout(1)
        .build();

      const client = new DefaultApiClient(transport);
      await expect(
        client.sendRequest('GET', `${wiremockUrl}/api/slow`, {}, null)
      ).rejects.toThrow();
    });
  });

  describe('User-Agent header', () => {
    test('injects custom User-Agent header', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;

      const transport = TransportOptions.builder()
        .userAgent('MyApp/1.0')
        .build();

      const client = new DefaultApiClient(transport);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/echo-headers`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      const json = JSON.parse(response.body);
      expect(json['user-agent']).toBe('MyApp/1.0');
    });
  });

  describe('X-Request-ID injection', () => {
    test('injects X-Request-ID header with UUID format', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;

      const transport = TransportOptions.builder()
        .injectRequestId(true)
        .build();

      const client = new DefaultApiClient(transport);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/echo-headers`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      const json = JSON.parse(response.body);
      const requestId = json['x-request-id'];
      expect(requestId).toBeDefined();
      expect(requestId).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
      );
    });

    test('generates unique X-Request-ID per request', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;

      const transport = TransportOptions.builder()
        .injectRequestId(true)
        .build();

      const client = new DefaultApiClient(transport);

      const response1 = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/echo-headers`,
        {},
        null
      );
      const requestId1 = JSON.parse(response1.body)['x-request-id'];

      const response2 = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/echo-headers`,
        {},
        null
      );
      const requestId2 = JSON.parse(response2.body)['x-request-id'];

      expect(requestId1).not.toBe(requestId2);
    });
  });

  describe('default headers', () => {
    test('includes transport-level default headers', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;

      const transport = TransportOptions.builder()
        .defaultHeader('X-Custom', 'custom-value')
        .build();

      const client = new DefaultApiClient(transport);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/echo-headers`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      const json = JSON.parse(response.body);
      expect(json['x-custom']).toBe('custom-value');
    });

    test('caller headers override transport default headers', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;

      const transport = TransportOptions.builder()
        .defaultHeader('Accept', 'text/plain')
        .build();

      const client = new DefaultApiClient(transport);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/echo-headers`,
        { Accept: 'application/json' },
        null
      );

      expect(response.statusCode).toBe(200);
      const json = JSON.parse(response.body);
      expect(json['accept']).toBe('application/json');
    });
  });

  describe('redirect handling', () => {
    test('follows redirects when enabled', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;

      const transport = TransportOptions.builder()
        .followRedirects(true)
        .build();

      const client = new DefaultApiClient(transport);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/redirect`,
        {},
        null
      );

      expect(response.statusCode).toBe(200);
      expect(response.body).toContain('success');
    });

    test('returns redirect response when disabled', async () => {
      const wiremockUrl = process.env['WIREMOCK_HTTP_URL']!;

      const transport = TransportOptions.builder()
        .followRedirects(false)
        .build();

      const client = new DefaultApiClient(transport);
      const response = await client.sendRequest(
        'GET',
        `${wiremockUrl}/api/redirect`,
        {},
        null
      );

      expect(response.statusCode).toBe(302);
    });
  });
});
