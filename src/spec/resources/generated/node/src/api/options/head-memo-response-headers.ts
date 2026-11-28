import { SerializationError } from "../../errors/serialization-error.js";

/**
 * The headers the success response of the headMemo operation declares,
 * read in their declared types. An absent header leaves its field undefined.
 */
export class HeadMemoResponseHeaders {
  /** The revision of the memo */
  readonly xRevision: number | undefined;

  private constructor(headers: Readonly<Record<string, string>>) {
    this.xRevision = readHeader(headers, "X-Revision", (value) =>
      parseIntegerHeader("X-Revision", "integer", value),
    );
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
  ): HeadMemoResponseHeaders {
    return new HeadMemoResponseHeaders(headers);
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

/** The error for a header value that does not parse as its declared type. */
function invalidHeader(
  wireName: string,
  kind: string,
  value: string,
): SerializationError {
  return new SerializationError(
    `Response header '${wireName}' is not a valid ${kind}: '${value}'`,
  );
}

/** A base-10 integer with an optional sign. */
function parseIntegerHeader(
  wireName: string,
  kind: string,
  value: string,
): number {
  if (!/^[+-]?\d+$/.test(value)) {
    throw invalidHeader(wireName, kind, value);
  }
  return Number(value);
}
