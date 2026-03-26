import type { ApiClient } from '../../src/api-client.js';
import type { ApiResponse } from '../../src/api-response.js';
import { BaseApi } from '../../src/api/base-api.js';
import { Configuration } from '../../src/configuration.js';

class TestApi extends BaseApi {
  constructor(config: Configuration, apiClient: ApiClient) {
    super(config, apiClient);
  }

  async callWithQueryParams(queryParams: Record<string, unknown>): Promise<string> {
    let capturedUrl = '';
    const originalSendRequest = this.apiClient.sendRequest.bind(this.apiClient);
    (this.apiClient as any).sendRequest = async (
      method: string,
      url: string,
      headers: Record<string, string>,
      body: string | Buffer | null
    ): Promise<ApiResponse> => {
      capturedUrl = url;
      return { statusCode: 200, body: '{}', headers: {} };
    };
    await this.invokeApi('GET', '/test', queryParams, {}, null, ['application/json'], 'application/json', null);
    return capturedUrl;
  }
}

function createApi(): TestApi {
  const mockClient: ApiClient = {
    async sendRequest() {
      return { statusCode: 200, body: '{}', headers: {} };
    }
  };
  const config = new Configuration({ baseUrl: 'http://test' });
  return new TestApi(config, mockClient);
}

describe('BaseApi', () => {
  it('expands array query params', async () => {
    const api = createApi();
    const url = await api.callWithQueryParams({ tags: ['dog', 'cat'] });
    expect(url).toContain('tags=dog');
    expect(url).toContain('tags=cat');
    expect(url).not.toContain('[');
    expect(url).not.toContain(']');
  });

  it('serializes boolean query params', async () => {
    const api = createApi();
    const url = await api.callWithQueryParams({ active: true });
    expect(url).toContain('active=true');
  });

  it('serializes number query params', async () => {
    const api = createApi();
    const url = await api.callWithQueryParams({ limit: 10 });
    expect(url).toContain('limit=10');
  });

  it('handles empty query params', async () => {
    const api = createApi();
    const url = await api.callWithQueryParams({});
    expect(url).not.toContain('?');
  });
});
