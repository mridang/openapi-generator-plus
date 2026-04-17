import { TransportOptions } from '../src/transport-options';

describe('TransportOptions', () => {
  test('builder produces correct defaults', () => {
    const opts = TransportOptions.builder().build();

    expect(opts.verifySsl).toBe(true);
    expect(opts.caCertPath).toBeNull();
    expect(opts.proxy).toBeNull();
    expect(opts.timeout).toBeNull();
    expect(opts.followRedirects).toBe(true);
    expect(opts.maxRedirects).toBeNull();
    expect(opts.userAgent).toBe('openapi-typescript-client/1.0.0 (node)');
    expect(opts.defaultHeaders).toEqual({});
    expect(opts.injectRequestId).toBe(false);
  });

  test('builder sets all fields', () => {
    const opts = TransportOptions.builder()
      .verifySsl(false)
      .caCertPath('/path/to/ca.pem')
      .proxy('http://proxy:8080')
      .timeout(5000)
      .followRedirects(false)
      .maxRedirects(3)
      .userAgent('TestAgent/1.0')
      .defaultHeader('X-Custom', 'value')
      .injectRequestId(true)
      .build();

    expect(opts.verifySsl).toBe(false);
    expect(opts.caCertPath).toBe('/path/to/ca.pem');
    expect(opts.proxy).toBe('http://proxy:8080');
    expect(opts.timeout).toBe(5000);
    expect(opts.followRedirects).toBe(false);
    expect(opts.maxRedirects).toBe(3);
    expect(opts.userAgent).toBe('TestAgent/1.0');
    expect(opts.defaultHeaders).toEqual({ 'X-Custom': 'value' });
    expect(opts.injectRequestId).toBe(true);
  });

  test('defaultHeaders is a defensive copy', () => {
    const headers: Record<string, string> = { 'X-Original': 'original' };

    const opts = TransportOptions.builder().defaultHeaders(headers).build();

    headers['X-Added'] = 'added';

    expect(Object.keys(opts.defaultHeaders)).toHaveLength(1);
    expect(opts.defaultHeaders['X-Original']).toBe('original');
    expect(opts.defaultHeaders['X-Added']).toBeUndefined();
  });
});
