/**
 * The headers the success response of the listPetFeed operation declares,
 * read in their declared types. An absent header leaves its field undefined.
 */
export class ListPetFeedResponseHeaders {
  /** The next page as rel=\"next\", when there is one */
  readonly link: string | undefined;

  private constructor(headers: Readonly<Record<string, string>>) {
    this.link = readHeader(headers, "Link", (value) => value);
  }

  /**
   * Reads the declared headers from a response's headers, matching each name
   * without regard to case.
   *
   * @param headers the response headers, for example `ApiResult.headers`
   * @returns the headers in their declared types
   * @throws SerializationError when a header value does not parse as its declared type
   */
  static from(
    headers: Readonly<Record<string, string>>,
  ): ListPetFeedResponseHeaders {
    return new ListPetFeedResponseHeaders(headers);
  }
}

/** The value of the header with this name in any case, parsed, or undefined when it is absent. */
function readHeader<T>(
  headers: Readonly<Record<string, string>>,
  wireName: string,
  parse: (value: string) => T,
): T | undefined {
  const wanted = wireName.toLowerCase();
  for (const [name, value] of Object.entries(headers)) {
    if (name.toLowerCase() === wanted) {
      return parse(value);
    }
  }
  return undefined;
}
