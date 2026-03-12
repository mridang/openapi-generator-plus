import type { ApiClient } from './api-client.js';
import type { ApiResponse } from './api-response.js';
import type { Configuration } from './configuration.js';
import * as https from 'node:https';
import * as http from 'node:http';
import * as fs from 'node:fs';

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
    body: string | null
  ): Promise<ApiResponse> {
    if (this.config?.proxy || this.agent) {
      return this.sendWithNodeHttp(method, url, headers, body);
    }
    return this.sendWithFetch(method, url, headers, body);
  }

  private async sendWithFetch(
    method: string,
    url: string,
    headers: Record<string, string>,
    body: string | null
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
    body: string | null
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
      const responseBody = Buffer.concat(chunks).toString('utf-8');
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
  }
}
