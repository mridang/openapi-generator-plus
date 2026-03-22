import { ClientError } from './client-error.js';

/**
 * Exception for HTTP 404 Not Found.
 */
export class NotFoundError<T = unknown> extends ClientError<T> {
  constructor(
    message: string,
    responseHeaders: Record<string, string> = {},
    responseBody: string | null = null,
    errorBody: T | null = null
  ) {
    super(404, message, responseHeaders, responseBody, errorBody);
    this.name = 'NotFoundError';
  }
}
