import type { ApiClient } from './api-client.js';
import type { ApiResponse } from './api-response.js';
import type { Configuration } from './configuration.js';
import * as crypto from 'node:crypto';
import * as https from 'node:https';
import * as http from 'node:http';
import * as fs from 'node:fs';
import * as zlib from 'node:zlib';
import { promisify } from 'node:util';

/**
 * Default implementation of ApiClient using the Fetch API.
 *
 * When a Configuration with proxy or TLS settings is provided,
 * falls back to Node.js http/https modules for full control.
 */
export class DefaultApiClient implements ApiClient {
  private readonly config?: Configuration;
  private readonly agent?: https.Agent;

  constructor(config?: Configuration) {
    this.config = config;
    if (config && (config.sslCaCert != null || !config.verifySsl)) {
      this.agent = new https.Agent({
        ca: config.sslCaCert ? fs.readFileSync(config.sslCaCert) : undefined,
        rejectUnauthorized: config.verifySsl
      });
    }
  }

  async sendRequest(
    method: string,
    url: string,
    headers: Record<string, string>,
    body: string | Buffer | null
  ): Promise<ApiResponse> {
    if (body instanceof Blob) {
      body = Buffer.from(await body.arrayBuffer());
    }

    headers['Accept-Encoding'] ??= DefaultApiClient.supportedEncodings();

    if (body != null && typeof body === 'object' && !Buffer.isBuffer(body)) {
      const boundary = crypto.randomUUID();
      headers['Content-Type'] = `multipart/form-data; boundary=${boundary}`;
      body = await this.buildMultipartBody(body as Record<string, unknown>, boundary);
    }

    if (this.config?.proxy || this.agent) {
      return this.sendWithNodeHttp(method, url, headers, body);
    }
    return this.sendWithFetch(method, url, headers, body);
  }

  private async sendWithFetch(
    method: string,
    url: string,
    headers: Record<string, string>,
    body: string | Buffer | null
  ): Promise<ApiResponse> {
    const response = await fetch(url, {
      method,
      headers,
      body: body ?? undefined
    });

    const responseBody = await response.text();
    const responseHeaders: Record<string, string> = {};
    response.headers.forEach((value, key) => {
      responseHeaders[key] = value;
    });

    return {
      statusCode: response.status,
      body: responseBody,
      headers: responseHeaders
    };
  }

  private sendWithNodeHttp(
    method: string,
    url: string,
    headers: Record<string, string>,
    body: string | Buffer | null
  ): Promise<ApiResponse> {
    return new Promise((resolve, reject) => {
      const parsed = new URL(url);
      const isHttps = parsed.protocol === 'https:';

      let requestOptions: http.RequestOptions;

      if (this.config?.proxy) {
        const proxyUrl = new URL(this.config.proxy);
        if (isHttps) {
          const connectReq = http.request({
            host: proxyUrl.hostname,
            port: Number(proxyUrl.port) || 3128,
            method: 'CONNECT',
            path: `${parsed.hostname}:${parsed.port || 443}`
          });

          connectReq.on('connect', (_res, socket) => {
            const tlsOptions: https.RequestOptions = {
              host: parsed.hostname,
              port: Number(parsed.port) || 443,
              path: parsed.pathname + parsed.search,
              method,
              headers,
              createConnection: () => socket,
              agent: this.agent ?? new https.Agent({ rejectUnauthorized: this.config?.verifySsl ?? true })
            };
            const req = https.request(tlsOptions, (res) => this.collectResponse(res, resolve));
            req.on('error', reject);
            if (body) req.write(body);
            req.end();
          });

          connectReq.on('error', reject);
          connectReq.end();
          return;
        } else {
          requestOptions = {
            host: proxyUrl.hostname,
            port: Number(proxyUrl.port) || 3128,
            path: url,
            method,
            headers: { ...headers, Host: parsed.host }
          };
        }
      } else {
        requestOptions = {
          hostname: parsed.hostname,
          port: parsed.port ? Number(parsed.port) : undefined,
          path: parsed.pathname + parsed.search,
          method,
          headers,
          agent: isHttps ? this.agent : undefined
        };
      }

      const transport = this.config?.proxy || !isHttps ? http : https;
      const req = transport.request(requestOptions, (res) => this.collectResponse(res, resolve));
      req.on('error', reject);
      if (body) req.write(body);
      req.end();
    });
  }

  private collectResponse(res: http.IncomingMessage, resolve: (value: ApiResponse) => void): void {
    const chunks: Buffer[] = [];
    res.on('data', (chunk: Buffer) => chunks.push(chunk));
    res.on('end', () => {
      const raw = Buffer.concat(chunks);
      const encoding = (res.headers['content-encoding'] ?? '').toLowerCase();
      this.decompressBody(raw, encoding).then((decompressed) => {
        const responseBody = decompressed.toString('utf-8');
        const responseHeaders: Record<string, string> = {};
        for (const [key, value] of Object.entries(res.headers)) {
          if (typeof value === 'string') {
            responseHeaders[key] = value;
          } else if (Array.isArray(value)) {
            responseHeaders[key] = value.join(', ');
          }
        }
        resolve({
          statusCode: res.statusCode ?? 0,
          body: responseBody,
          headers: responseHeaders
        });
      });
    });
  }

  static supportedEncodings(): string {
    const encodings = ['gzip', 'deflate', 'br'];
    if (typeof zlib.zstdDecompress === 'function') {
      encodings.push('zstd');
    }
    return encodings.join(', ');
  }

  private async decompressBody(data: Buffer, encoding: string): Promise<Buffer> {
    if (data.length === 0) return data;
    switch (encoding) {
      case 'gzip':
      case 'x-gzip':
        return promisify(zlib.gunzip)(data);
      case 'deflate':
        return promisify(zlib.inflate)(data);
      case 'br':
        return promisify(zlib.brotliDecompress)(data);
      case 'zstd':
        if (typeof zlib.zstdDecompress === 'function') {
          return promisify(zlib.zstdDecompress)(data);
        }
        return data;
      default:
        return data;
    }
  }

  private async buildMultipartBody(formParts: Record<string, unknown>, boundary: string): Promise<Buffer> {
    const parts: Buffer[] = [];
    for (const [name, value] of Object.entries(formParts)) {
      if (Array.isArray(value)) {
        for (const item of value) {
          parts.push(await this.multipartPart(name, item, boundary));
        }
      } else {
        parts.push(await this.multipartPart(name, value, boundary));
      }
    }
    parts.push(Buffer.from(`--${boundary}--\r\n`, 'utf-8'));
    return Buffer.concat(parts);
  }

  private async multipartPart(name: string, value: unknown, boundary: string): Promise<Buffer> {
    if (Buffer.isBuffer(value)) {
      const header = `--${boundary}\r\nContent-Disposition: form-data; name="${name}"; filename="${name}"\r\nContent-Type: application/octet-stream\r\n\r\n`;
      return Buffer.concat([Buffer.from(header, 'utf-8'), value, Buffer.from('\r\n', 'utf-8')]);
    }
    if (value instanceof Blob) {
      const buf = Buffer.from(await value.arrayBuffer());
      const header = `--${boundary}\r\nContent-Disposition: form-data; name="${name}"; filename="${name}"\r\nContent-Type: application/octet-stream\r\n\r\n`;
      return Buffer.concat([Buffer.from(header, 'utf-8'), buf, Buffer.from('\r\n', 'utf-8')]);
    }
    if (typeof value === 'object' && value !== null) {
      const json = JSON.stringify(value);
      return Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\nContent-Type: application/json\r\n\r\n${json}\r\n`,
        'utf-8'
      );
    }
    return Buffer.from(
      `--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${String(value)}\r\n`,
      'utf-8'
    );
  }
}
