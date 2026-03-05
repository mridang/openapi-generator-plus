/**
 * Represents an HTTP API response.
 */
export interface ApiResponse {
  statusCode: number;
  body: string;
  headers: Record<string, string>;
}
