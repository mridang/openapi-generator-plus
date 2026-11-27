package com.example.petstore.api.options;

import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * The response headers the listPetFeed operation declares, read in their declared types.
 *
 * <p>Build it from the headers of a raw response with {@link #from(Map)}. A header the response
 * does not carry is {@code null}.
 */
public final class ListPetFeedResponseHeaders {

  /** The {@code Link} header. */
  @Nullable private final String link;

  private ListPetFeedResponseHeaders(Map<String, String> headers) {
    this.link = read(headers, "Link", (name, value) -> value);
  }

  /**
   * Reads the declared headers from the headers of a raw response, matching the names without
   * regard to case.
   *
   * @param headers the response headers, as {@code ApiResult.headers()} returns them
   * @return the typed headers; a header the response does not carry is {@code null}
   */
  public static ListPetFeedResponseHeaders from(Map<String, String> headers) {
    return new ListPetFeedResponseHeaders(headers);
  }

  /**
   * Returns the {@code Link} header.
   *
   * <p>The next page as rel="next", when there is one.
   *
   * @return the value, or {@code null} when the response does not carry the header
   */
  @Nullable
  public String link() {
    return link;
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
}
