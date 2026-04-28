import * as http from 'http';
import { DefaultApiClient } from '../src/default-api-client';
import { HeaderSelector } from '../src/header-selector';

let server: http.Server;
let baseUrl: string;

beforeAll(async () => {
  server = http.createServer((req, res) => {
    if (req.url === '/not-found') {
      res.writeHead(404);
      res.end('not found');
      return;
    }

    if (req.url === '/vendor-json') {
      const response = JSON.stringify({ format: 'vendor' });
      res.writeHead(200, {
        'Content-Type': 'application/vnd.api+json'
      });
      res.end(response);
      return;
    }

    let body = '';
    req.on('data', (chunk: Buffer) => (body += chunk.toString()));
    req.on('end', () => {
      const response = JSON.stringify({ method: req.method, body });
      res.writeHead(200, {
        'Content-Type': 'application/json',
        'X-Test-Header': 'test-value'
      });
      res.end(response);
    });
  });

  await new Promise<void>((resolve) => {
    server.listen(0, '127.0.0.1', resolve);
  });

  const addr = server.address() as { port: number };
  baseUrl = `http://127.0.0.1:${addr.port}`;
});

afterAll(async () => {
  await new Promise<void>((resolve) => server.close(() => resolve()));
});

describe('DefaultApiClient unit', () => {
  it('sends GET request and returns response', async () => {
    const client = new DefaultApiClient();
    const response = await client.sendRequest('GET', `${baseUrl}/echo`, {}, null);
    expect(response.statusCode).toBe(200);
    const body = JSON.parse(response.body);
    expect(body.method).toBe('GET');
  });

  it('sends POST with JSON body', async () => {
    const client = new DefaultApiClient();
    const response = await client.sendRequest(
      'POST',
      `${baseUrl}/echo`,
      { 'Content-Type': 'application/json' },
      '{"key":"value"}'
    );
    expect(response.statusCode).toBe(200);
    const body = JSON.parse(response.body);
    expect(body.method).toBe('POST');
    expect(body.body).toContain('key');
  });

  it('returns response headers', async () => {
    const client = new DefaultApiClient();
    const response = await client.sendRequest('GET', `${baseUrl}/echo`, {}, null);
    expect(response.headers['x-test-header']).toBe('test-value');
  });

  it('returns non-2xx status code', async () => {
    const client = new DefaultApiClient();
    const response = await client.sendRequest('GET', `${baseUrl}/not-found`, {}, null);
    expect(response.statusCode).toBe(404);
    expect(response.body).toBe('not found');
  });

  it('sends PUT request', async () => {
    const client = new DefaultApiClient();
    const response = await client.sendRequest('PUT', `${baseUrl}/echo`, {}, 'update');
    expect(response.statusCode).toBe(200);
    const body = JSON.parse(response.body);
    expect(body.method).toBe('PUT');
  });

  it('sends DELETE request', async () => {
    const client = new DefaultApiClient();
    const response = await client.sendRequest('DELETE', `${baseUrl}/echo`, {}, null);
    expect(response.statusCode).toBe(200);
    const body = JSON.parse(response.body);
    expect(body.method).toBe('DELETE');
  });

  it('deserializes response with application/vnd.api+json as JSON', async () => {
    const client = new DefaultApiClient();
    const response = await client.sendRequest('GET', `${baseUrl}/vendor-json`, {}, null);
    expect(response.statusCode).toBe(200);

    const contentType = response.headers['content-type']?.split(';')[0]?.trim() ?? '';
    const headerSelector = new HeaderSelector();
    expect(headerSelector.isJsonMime(contentType)).toBe(true);

    const body = JSON.parse(response.body);
    expect(body.format).toBe('vendor');
  });
});
