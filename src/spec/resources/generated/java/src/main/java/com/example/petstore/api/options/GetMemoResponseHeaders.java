package com.example.petstore.api.options;

import com.example.petstore.errors.SerializationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * The response headers the getMemo operation declares, read in their declared types.
 *
 * <p>Build it from the headers of a raw response with {@link #from(Map)}. A header the response
 * does not carry is {@code null}.
 */
public final class GetMemoResponseHeaders {

  /** A base-10 integer with an optional sign. */
  private static final Pattern INTEGRAL = Pattern.compile("[+-]?\\d+");

  /** The {@code X-Revision} header. */
  @Nullable private final Integer xRevision;

  /** The {@code X-Labels} header. */
  @Nullable private final List<String> xLabels;

  /** The {@code X-Archived} header. */
  @Nullable private final Boolean xArchived;

  private GetMemoResponseHeaders(Map<String, String> headers) {
    this.xRevision = read(headers, "X-Revision", GetMemoResponseHeaders::parseInteger);
    this.xLabels = readList(headers, "X-Labels", (name, value) -> value);
    this.xArchived = read(headers, "X-Archived", GetMemoResponseHeaders::parseBoolean);
  }

  /**
   * Reads the declared headers from the headers of a raw response, matching the names without
   * regard to case.
   *
   * @param headers the response headers, as {@code ApiResult.headers()} returns them
   * @return the typed headers; a header the response does not carry is {@code null}
   * @throws SerializationException if a header value does not parse as its declared type
   */
  public static GetMemoResponseHeaders from(Map<String, String> headers) {
    return new GetMemoResponseHeaders(headers);
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

  /**
   * Returns the {@code X-Labels} header.
   *
   * <p>The labels of the memo.
   *
   * @return the value, or {@code null} when the response does not carry the header
   */
  @Nullable
  public List<String> xLabels() {
    return xLabels == null ? null : List.copyOf(xLabels);
  }

  /**
   * Returns the {@code X-Archived} header.
   *
   * <p>Whether the memo is archived.
   *
   * <p>Example: {@code false}
   *
   * @return the value, or {@code null} when the response does not carry the header
   */
  @Nullable
  public Boolean xArchived() {
    return xArchived;
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

  @Nullable
  private static <T> List<T> readList(
      Map<String, String> headers, String name, BiFunction<String, String, T> parser) {
    String value = find(headers, name);
    if (value == null) {
      return null;
    }
    List<T> items = new ArrayList<>();
    for (String item : value.split(",", -1)) {
      items.add(parser.apply(name, item.trim()));
    }
    return List.copyOf(items);
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

  private static Boolean parseBoolean(String name, String value) {
    if ("true".equals(value)) {
      return Boolean.TRUE;
    }
    if ("false".equals(value)) {
      return Boolean.FALSE;
    }
    throw invalid(name, "boolean", value);
  }
}
