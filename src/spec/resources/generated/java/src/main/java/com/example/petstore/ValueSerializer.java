package com.example.petstore;

import com.fasterxml.jackson.databind.util.StdDateFormat;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Serializes parameter values for HTTP requests based on their location and format.
 *
 * <p>This utility class converts Java objects into their string representations suitable for
 * inclusion in HTTP request paths, query strings, and headers. It handles null values, collections
 * with various collection formats, temporal types, and URL encoding.
 */
public final class ValueSerializer {

  private ValueSerializer() {}

  /**
   * Serialize a value for use in an HTTP request parameter.
   *
   * <p>The serialization strategy depends on the {@code location}:
   *
   * <ul>
   *   <li><b>path</b> — scalar values are stringified and URL-encoded
   *   <li><b>query</b> — nulls are omitted (returns {@code null}); collections are formatted
   *       according to {@code collectionFormat}
   *   <li><b>header</b> — collections are joined with commas; nulls become empty strings
   *   <li>Any other location — nulls become empty strings, scalars are stringified
   * </ul>
   *
   * @param value the value to serialize (may be null)
   * @param location where the parameter appears: "path", "query", or "header"
   * @param schemaType the OpenAPI schema type (currently unused, reserved for future format-aware
   *     serialization)
   * @param collectionFormat the collection format: "multi", "csv", "ssv", "tsv", or "pipes" (only
   *     relevant for query parameters)
   * @return the serialized value — a {@link String}, a {@link List} of strings (for "multi" query
   *     collections), or {@code null} to omit the parameter
   */
  @Nullable
  public static Object serialize(
      @Nullable Object value,
      String location,
      @Nullable String schemaType,
      @Nullable String collectionFormat) {

    if (value == null) {
      if ("query".equals(location)) {
        return null;
      }
      return "";
    }

    if (value instanceof Collection<?> col) {
      List<String> items =
          col.stream().map(ValueSerializer::stringify).collect(Collectors.toList());

      if ("query".equals(location)) {
        if ("multi".equals(collectionFormat)) {
          return items;
        }
        String separator;
        if ("ssv".equals(collectionFormat)) {
          separator = " ";
        } else if ("tsv".equals(collectionFormat)) {
          separator = "\t";
        } else if ("pipes".equals(collectionFormat)) {
          separator = "|";
        } else {
          separator = ",";
        }
        return String.join(separator, items);
      }

      if ("header".equals(location)) {
        return String.join(",", items);
      }
    }

    String str = stringify(value);

    if ("path".equals(location)) {
      return URLEncoder.encode(str, StandardCharsets.UTF_8).replace("+", "%20");
    }

    return str;
  }

  /**
   * Convert a scalar value to its string representation.
   *
   * <p>Booleans produce lowercase {@code "true"}/{@code "false"}. Temporal types are formatted with
   * {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME}. Legacy {@link Date} instances are formatted
   * with Jackson's {@link StdDateFormat}. All other values use {@link String#valueOf(Object)}.
   *
   * @param value the value to stringify (must not be null)
   * @return the string representation
   */
  /**
   * Serialize a deepObject-style query parameter.
   *
   * <p>Produces a map of flattened keys in the form {@code paramName[key]} to URL-encoded string
   * values, suitable for inclusion in a query string.
   *
   * @param paramName the parameter name (e.g. "filter")
   * @param value the map value to serialize
   * @return a map of expanded keys to serialized values, or an empty map if value is null
   */
  public static Map<String, String> serializeDeepObject(
      String paramName, @Nullable Map<?, ?> value) {
    Map<String, String> result = new LinkedHashMap<>();
    if (value == null) {
      return result;
    }
    for (Map.Entry<?, ?> entry : value.entrySet()) {
      String key = paramName + "[" + entry.getKey() + "]";
      String val = stringify(entry.getValue());
      result.put(key, val);
    }
    return result;
  }

  private static String stringify(Object value) {
    if (value instanceof Boolean b) {
      return b ? "true" : "false";
    }
    if (value instanceof TemporalAccessor t) {
      return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t);
    }
    if (value instanceof Date d) {
      return new StdDateFormat().format(d);
    }
    return String.valueOf(value);
  }
}
