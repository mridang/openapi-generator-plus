import { ClientError } from './client-error.js';

/**
 * Exception for HTTP 400 Bad Request.
 */
export class BadRequestError<T = unknown> extends ClientError<T> {
  constructor(
    message: string,
    responseHeaders: Record<string, string> = {},
    responseBody: string | null = null,
    errorBody: T | null = null
  ) {
    super(400, message, responseHeaders, responseBody, errorBody);
    this.name = 'BadRequestError';
  }
}
