/**
 * Base error class for all API errors.
 */
export class ApiError<T = unknown> extends Error {
  /** HTTP status code. */
  public readonly statusCode: number;

  /** Raw response body string. */
  public readonly responseBody: string | null;

  /** Response headers. */
  public readonly responseHeaders: Record<string, string>;

  /** Deserialized error body, if available. */
  public readonly errorBody: T | null;

  constructor(
    statusCode: number,
    message: string,
    responseHeaders: Record<string, string> = {},
    responseBody: string | null = null,
    errorBody: T | null = null
  ) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.responseHeaders = responseHeaders;
    this.responseBody = responseBody;
    this.errorBody = errorBody;
  }
}
