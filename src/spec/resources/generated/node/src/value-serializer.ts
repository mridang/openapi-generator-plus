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
