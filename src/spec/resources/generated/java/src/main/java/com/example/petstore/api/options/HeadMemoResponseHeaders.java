package com.example.petstore.api.options;

import com.example.petstore.errors.SerializationException;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * The response headers the headMemo operation declares, read in their declared types.
 *
 * <p>Build it from the headers of a raw response with {@link #from(Map)}. A header the response
 * does not carry is {@code null}.
 */
public final class HeadMemoResponseHeaders {

  /** A base-10 integer with an optional sign. */
  private static final Pattern INTEGRAL = Pattern.compile("[+-]?\\d+");

  /** The {@code X-Revision} header. */
  @Nullable private final Integer xRevision;

  private HeadMemoResponseHeaders(Map<String, String> headers) {
    this.xRevision = read(headers, "X-Revision", HeadMemoResponseHeaders::parseInteger);
  }

  /**
   * Reads the declared headers from the headers of a raw response, matching the names without
   * regard to case.
   *
   * @param headers the response headers, as {@code ApiResult.headers()} returns them
   * @return the typed headers; a header the response does not carry is {@code null}
   * @throws SerializationException if a header value does not parse as its declared type
   */
  public static HeadMemoResponseHeaders from(Map<String, String> headers) {
    return new HeadMemoResponseHeaders(headers);
  }

  /**
   * Returns the {@code X-Revision} header.
   *
   * <p>The revision of the memo.
   *
   * @return the value, or {@code null} when the response does not carry the header
   */
  @Nullable
  public Integer xRevision() {
    return xRevision;
  }

  @Nullable
  private static String find(Map<String, String> headers, String name) {
    for (Map.Entry<String, String> header : headers.entrySet()) {
      if (header.getKey().equalsIgnoreCase(name)) {
        return header.getValue();
      }
    }
    return null;
  }

  @Nullable
  private static <T> T read(
      Map<String, String> headers, String name, BiFunction<String, String, T> parser) {
    String value = find(headers, name);
    return value == null ? null : parser.apply(name, value);
  }

  private static SerializationException invalid(String name, String kind, String value) {
    return invalid(name, kind, value, null);
  }

  private static SerializationException invalid(
      String name, String kind, String value, @Nullable Throwable cause) {
    return new SerializationException(
        "Response header '" + name + "' is not a valid " + kind + ": '" + value + "'",
        cause,
        0,
        null,
        null,
        null,
        null);
  }

  private static Integer parseInteger(String name, String value) {
    if (!INTEGRAL.matcher(value).matches()) {
      throw invalid(name, "integer", value);
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      throw invalid(name, "integer", value, e);
    }
  }
}
