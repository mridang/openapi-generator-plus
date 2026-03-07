package com.example.petstore;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.openapitools.jackson.nullable.JsonNullableModule;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * Handles JSON serialization and deserialization for API requests and responses.
 *
 * <p>All serde operations in the generated client route through this class. The parameter encoding
 * methods provide consistent value conversion for URL path, query string, header, and form
 * parameters.
 */
public class ObjectSerializer {

  private final ObjectMapper objectMapper;

  /** Creates a new ObjectSerializer with default configuration. */
  public ObjectSerializer() {
    this.objectMapper = createDefaultObjectMapper();
  }

  /**
   * Creates a new ObjectSerializer with a custom ObjectMapper.
   *
   * @param objectMapper the ObjectMapper to use for serialization
   */
  public ObjectSerializer(ObjectMapper objectMapper) {
    if (objectMapper == null) {
      throw new IllegalArgumentException("ObjectMapper cannot be null");
    }
    this.objectMapper = objectMapper;
  }

  /**
   * Serialize an object to a JSON string.
   *
   * @param object the object to serialize (may be null)
   * @return JSON string representation, or "null" if object is null
   * @throws SerializationException if serialization fails
   */
  public String serialize(Object object) throws SerializationException {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new SerializationException("Failed to serialize object to JSON", e);
    }
  }

  /**
   * Deserialize a JSON string to an object of the specified type.
   *
   * @param <T> the type to deserialize to
   * @param jsonString the JSON string to deserialize (may be null or empty)
   * @param typeReference the type reference for the target type
   * @return the deserialized object, or null if jsonString is null or empty
   * @throws SerializationException if deserialization fails
   */
  public <T> T deserialize(String jsonString, TypeReference<T> typeReference)
      throws SerializationException {
    if (jsonString == null || jsonString.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(jsonString, typeReference);
    } catch (JsonProcessingException e) {
      throw new SerializationException(
          "Failed to deserialize JSON to " + typeReference.getType(), e);
    }
  }

  /**
   * Convert a value to a string suitable for use as a URL path parameter.
   *
   * @param value the value to convert (may be null)
   * @return string representation, or empty string if null
   */
  public static String toPathValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof Boolean) {
      return ((Boolean) value) ? "true" : "false";
    }
    if (value instanceof TemporalAccessor) {
      return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((TemporalAccessor) value);
    }
    if (value instanceof Date) {
      return new StdDateFormat().format((Date) value);
    }
    return String.valueOf(value);
  }

  /**
   * Convert a value to a representation suitable for use as a query parameter. For collections,
   * joins using the specified collection format delimiter.
   *
   * @param value the value to convert (may be null)
   * @param collectionFormat the format: csv, ssv, tsv, pipes, or multi (may be null)
   * @return the query value string, or a List for multi format, or null if value is null
   */
  public static Object toQueryValue(Object value, String collectionFormat) {
    if (value == null) {
      return null;
    }
    if (value instanceof java.util.Collection<?> col) {
      java.util.List<String> items = new java.util.ArrayList<>();
      for (Object item : col) {
        items.add(String.valueOf(item));
      }
      if ("multi".equals(collectionFormat)) {
        return items;
      }
      String sep;
      if ("ssv".equals(collectionFormat)) {
        sep = " ";
      } else if ("tsv".equals(collectionFormat)) {
        sep = "\t";
      } else if ("pipes".equals(collectionFormat)) {
        sep = "|";
      } else {
        sep = ",";
      }
      return String.join(sep, items);
    }
    if (value instanceof Boolean) {
      return ((Boolean) value) ? "true" : "false";
    }
    if (value instanceof TemporalAccessor) {
      return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((TemporalAccessor) value);
    }
    if (value instanceof Date) {
      return new StdDateFormat().format((Date) value);
    }
    return String.valueOf(value);
  }

  /**
   * Convert a value to a string suitable for use as an HTTP header value.
   *
   * @param value the value to convert (may be null)
   * @return string representation, or empty string if null
   */
  public static String toHeaderValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof java.util.Collection<?> col) {
      java.util.List<String> items = new java.util.ArrayList<>();
      for (Object item : col) {
        items.add(String.valueOf(item));
      }
      return String.join(",", items);
    }
    if (value instanceof Boolean) {
      return ((Boolean) value) ? "true" : "false";
    }
    if (value instanceof TemporalAccessor) {
      return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((TemporalAccessor) value);
    }
    if (value instanceof Date) {
      return new StdDateFormat().format((Date) value);
    }
    return String.valueOf(value);
  }

  /**
   * Convert a value to a representation suitable for use as a form parameter.
   *
   * @param value the value to convert (may be null)
   * @return string representation, or empty string if null
   */
  public static String toFormValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof Boolean) {
      return ((Boolean) value) ? "true" : "false";
    }
    if (value instanceof TemporalAccessor) {
      return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((TemporalAccessor) value);
    }
    if (value instanceof Date) {
      return new StdDateFormat().format((Date) value);
    }
    return String.valueOf(value);
  }

  private static ObjectMapper createDefaultObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
    mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new JsonNullableModule());
    mapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
    return mapper;
  }

  /** Exception raised when serialization or deserialization fails. */
  public static class SerializationException extends RuntimeException {

    public SerializationException(String message, Throwable cause) {
      super(message, cause);
    }

    public SerializationException(String message) {
      super(message);
    }
  }
}
