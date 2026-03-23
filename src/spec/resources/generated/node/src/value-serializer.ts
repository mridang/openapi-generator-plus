/**
 * Serializes parameter values for HTTP requests based on their location
 * (path, query, header, form) and handles type-specific conversions.
 *
 * This is the single entry point for all parameter serialization in generated
 * API methods. ObjectSerializer handles JSON object serde separately.
 */
export class ValueSerializer {
  /**
   * Serialize a parameter value for use in an HTTP request.
   *
   * @param value the value to serialize
   * @param location where the parameter appears: 'path', 'query', 'header', or 'form'
   * @param schemaType the OpenAPI schema type (e.g. 'string', 'integer', 'boolean', 'array')
   * @param collectionFormat for array query params: 'csv', 'ssv', 'tsv', 'pipes', or 'multi'
   * @returns the serialized value ready for the HTTP request
   */
  static serialize(
    value: unknown,
    location: 'path' | 'query' | 'header' | 'form',
    schemaType: string,
    collectionFormat?: string
  ): string | string[] | undefined {
    if (value === null || value === undefined) {
      if (location === 'query') return undefined;
      return '';
    }

    if (Array.isArray(value)) {
      const items = value.map((v) => ValueSerializer.stringify(v));
      if (location === 'query') {
        if (collectionFormat === 'multi') return items;
        if (collectionFormat === 'ssv') return items.join(' ');
        if (collectionFormat === 'tsv') return items.join('\t');
        if (collectionFormat === 'pipes') return items.join('|');
        return items.join(',');
      }
      if (location === 'header') {
        return items.join(',');
      }
    }

    const str = ValueSerializer.stringify(value);

    if (location === 'path') {
      return encodeURIComponent(str);
    }

    return str;
  }

  /**
   * Serialize a deepObject-style query parameter.
   *
   * Produces a record of flattened keys in the form `paramName[key]` to
   * stringified values, suitable for inclusion in a query string.
   *
   * @param paramName the parameter name (e.g. 'filter')
   * @param value the object value to serialize
   * @returns a record of expanded keys to serialized values
   */
  static serializeDeepObject(
    paramName: string,
    value: Record<string, unknown> | null | undefined
  ): Record<string, string> {
    const result: Record<string, string> = {};
    if (value == null) {
      return result;
    }
    for (const [key, val] of Object.entries(value)) {
      result[`${paramName}[${key}]`] = ValueSerializer.stringify(val);
    }
    return result;
  }

  private static stringify(value: unknown): string {
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
