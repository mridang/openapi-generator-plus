import { ServerError } from './server-error.js';

/**
 * Exception for HTTP 500 Internal Server Error.
 */
export class InternalServerError<T = unknown> extends ServerError<T> {
  constructor(
    message: string,
    responseHeaders: Record<string, string> = {},
    responseBody: string | null = null,
    errorBody: T | null = null
  ) {
    super(500, message, responseHeaders, responseBody, errorBody);
    this.name = 'InternalServerError';
  }
}
