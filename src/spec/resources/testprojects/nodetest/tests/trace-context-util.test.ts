import { injectTraceContext } from '../trace-context-util';

describe('TraceContextUtil', () => {
  test('should not inject traceparent when @opentelemetry/api is not installed', async () => {
    const headers: Record<string, string> = {};
    await injectTraceContext(headers);
    expect(headers['traceparent']).toBeUndefined();
  });

  test('should not throw any exception', async () => {
    const headers: Record<string, string> = {};
    await expect(injectTraceContext(headers)).resolves.not.toThrow();
  });
});
