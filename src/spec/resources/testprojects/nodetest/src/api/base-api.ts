import type { ApiClient } from '../api-client.js';
import type { Authenticator } from '../auth/authenticator.js';
import { Configuration } from '../configuration.js';
import { DefaultApiClient } from '../default-api-client.js';
import { HeaderSelector } from '../header-selector.js';
import { injectTraceContext } from '../trace-context-util.js';

/**
 * Base class for all API classes. Provides the invokeApi method that
 * handles URL construction, header selection, body serialization,
 * request dispatch, and response deserialization.
 */
export abstract class BaseApi {
  protected readonly apiClient: ApiClient;
  protected readonly config: Configuration;
  protected readonly headerSelector: HeaderSelector;

  constructor(config?: Configuration, apiClient?: ApiClient) {
    this.config = config ?? Configuration.getDefault();
    this.apiClient = apiClient ?? new DefaultApiClient(this.config);
    this.headerSelector = new HeaderSelector();
  }

  /**
   * Invoke an API operation.
   *
   * @param method HTTP method
   * @param path URL path (with path params already substituted)
   * @param queryParams query parameters
   * @param headerParams custom header parameters
   * @param body request body (pre-serialized to plain JS object, or null)
   * @param accepts acceptable response content types
   * @param contentType request content type
   * @param returnType deserialization function (or null for void)
   * @param auth optional authenticator for operation-specific auth
   * @returns deserialized response or void
   */
  protected async invokeApi<T>(
    method: string,
    path: string,
    queryParams: Record<string, unknown>,
    headerParams: Record<string, string>,
    body: unknown,
    accepts: string[],
    contentType: string,
    returnType: ((json: unknown) => T) | null,
    auth?: Authenticator | null
  ): Promise<T | void> {
    let url = this.config.baseUrl + path;

    if (auth) {
      const authQueryParams = auth.getQueryParams();
      for (const [k, v] of Object.entries(authQueryParams)) {
        queryParams[k] = v;
      }
    }

    const filteredParams = Object.entries(queryParams)
      .filter(([, v]) => v != null)
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join('&');
    if (filteredParams) {
      url += '?' + filteredParams;
    }

    const isMultipart = contentType === 'multipart/form-data';
    const headers = this.headerSelector.selectHeaders(accepts, contentType, isMultipart);
    Object.assign(headers, this.config.defaultHeaders);
    Object.assign(headers, headerParams);
    if (auth) {
      Object.assign(headers, auth.getAuthHeaders());
      const cookies = auth.getCookieParams();
      const cookieEntries = Object.entries(cookies);
      if (cookieEntries.length > 0) {
        const cookieStr = cookieEntries.map(([k, v]) => `${k}=${v}`).join('; ');
        const existing = headers['Cookie'];
        headers['Cookie'] = existing ? `${existing}; ${cookieStr}` : cookieStr;
      }
    }
    await injectTraceContext(headers);

    const serializedBody = this.serializeBody(body, contentType);

    const response = await this.apiClient.sendRequest(method, url, headers, serializedBody);

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw new Error(`API returned status code ${response.statusCode}: ${response.body}`);
    }

    if (returnType != null && response.body) {
      const respContentType =
        Object.entries(response.headers)
          .find(([k]) => k.toLowerCase() === 'content-type')?.[1]
          ?.split(';')[0]
          ?.trim() ?? '';
      if (respContentType && !respContentType.startsWith('application/json')) {
        return response.body as unknown as T;
      }
      const json = JSON.parse(response.body);
      return returnType(json);
    }
  }

  /**
   * Serialize the request body based on content type.
   *
   * For multipart/form-data and binary content types (image/* or
   * application/octet-stream), the body is passed through as-is.
   * All other content types are JSON-serialized.
   */
  private serializeBody(body: unknown, contentType: string): string | Buffer | null {
    if (body == null) {
      return null;
    }
    if (contentType === 'multipart/form-data') {
      return body as string | Buffer;
    }
    if (contentType.startsWith('image/') || contentType === 'application/octet-stream') {
      return body as Buffer;
    }
    return JSON.stringify(body);
  }
}
