/**
 * Exception raised when serialization or deserialization fails.
 */
export class SerializationError extends Error {
  public readonly cause?: Error;

  constructor(message: string, cause?: Error) {
    super(message);
    this.name = 'SerializationError';
    this.cause = cause;
  }
}

/**
 * Handles JSON serialization and deserialization for API requests and responses.
 *
 * All serde operations in the generated client route through this class.
 * The parameter encoding methods provide consistent value conversion for
 * URL path, query string, header, and form parameters.
 */
export class ObjectSerializer {
  /**
   * Serialize an object to a JSON string.
   *
   * @param obj the object to serialize
   * @param toJSON function that converts the object to a plain JS object
   * @returns plain JS object suitable for JSON.stringify, or undefined if null
   */
  static serialize<T>(obj: T | null | undefined, toJSON: (obj: T) => any): any {
    if (obj === null || obj === undefined) {
      return undefined;
    }
    try {
      return toJSON(obj);
    } catch (e) {
      throw new SerializationError(
        `Failed to serialize object: ${e instanceof Error ? e.message : String(e)}`,
        e instanceof Error ? e : undefined
      );
    }
  }

  /**
   * Deserialize a JSON string to an object of the specified type.
   *
   * @param json the parsed JSON value
   * @param fromJSON function that converts a plain JS object to the target type
   * @returns the deserialized object
   */
  static deserialize<T>(json: any, fromJSON: (json: any) => T): T {
    try {
      return fromJSON(json);
    } catch (e) {
      throw new SerializationError(
        `Failed to deserialize object: ${e instanceof Error ? e.message : String(e)}`,
        e instanceof Error ? e : undefined
      );
    }
  }

  /**
   * Deserialize an array of objects using the provided FromJSON function.
   *
   * @param json the parsed JSON array
   * @param fromJSON function that converts a plain JS object to the target type
   * @returns array of deserialized objects
   */
  static deserializeArray<T>(json: any[], fromJSON: (json: any) => T): T[] {
    return json.map((item) => ObjectSerializer.deserialize(item, fromJSON));
  }

  /**
   * Convert a value to a string suitable for use as a URL path parameter.
   *
   * @param value the value to convert (may be null or undefined)
   * @returns string representation, or empty string if null
   */
  static toPathValue(value: any): string {
    if (value === null || value === undefined) {
      return '';
    }
    if (typeof value === 'boolean') {
      return value ? 'true' : 'false';
    }
    if (value instanceof Date) {
      return value.toISOString();
    }
    return String(value);
  }

  /**
   * Convert a value to a representation suitable for use as a query parameter.
   * For collections, joins using the specified collection format delimiter.
   *
   * @param value the value to convert (may be null or undefined)
   * @param collectionFormat the format: csv, ssv, tsv, pipes, or multi
   * @returns the query value, or undefined if null
   */
  static toQueryValue(value: any, collectionFormat?: string): string | string[] | undefined {
    if (value === null || value === undefined) {
      return undefined;
    }
    if (Array.isArray(value)) {
      const items = value.map((v) => String(v));
      if (collectionFormat === 'multi') return items;
      if (collectionFormat === 'ssv') return items.join(' ');
      if (collectionFormat === 'tsv') return items.join('\t');
      if (collectionFormat === 'pipes') return items.join('|');
      return items.join(',');
    }
    if (typeof value === 'boolean') {
      return value ? 'true' : 'false';
    }
    if (value instanceof Date) {
      return value.toISOString();
    }
    return String(value);
  }

  /**
   * Convert a value to a string suitable for use as an HTTP header value.
   *
   * @param value the value to convert (may be null or undefined)
   * @returns string representation, or empty string if null
   */
  static toHeaderValue(value: any): string {
    if (value === null || value === undefined) {
      return '';
    }
    if (Array.isArray(value)) {
      return value.map((v) => String(v)).join(',');
    }
    if (typeof value === 'boolean') {
      return value ? 'true' : 'false';
    }
    if (value instanceof Date) {
      return value.toISOString();
    }
    return String(value);
  }

  /**
   * Convert a value to a representation suitable for use as a form parameter.
   *
   * @param value the value to convert (may be null or undefined)
   * @returns string representation, or empty string if null
   */
  static toFormValue(value: any): string {
    if (value === null || value === undefined) {
      return '';
    }
    if (typeof value === 'boolean') {
      return value ? 'true' : 'false';
    }
    if (value instanceof Date) {
      return value.toISOString();
    }
    return String(value);
  }
}
