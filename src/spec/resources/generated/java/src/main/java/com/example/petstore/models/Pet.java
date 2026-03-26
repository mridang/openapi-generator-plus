package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Pet.
 *
 * @see <a href="https://example.com/docs/pet">Learn more about the Pet model</a>
 */
@SuppressWarnings("deprecation")
public class Pet {

  public enum StatusEnum {
    AVAILABLE("available"),
    PENDING("pending"),
    SOLD("sold");

    private final String value;

    StatusEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @JsonCreator
    public static StatusEnum fromValue(String value) {
      for (StatusEnum b : values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  /** Example: {@code 10} */
  @JsonProperty("id")
  @Nullable
  public Long id;

  /** Example: {@code doggie} */
  @JsonProperty("name")
  public String name;

  /** Example: {@code null} */
  @JsonProperty("category")
  @Nullable
  public Category category;

  /** Example: {@code null} */
  @JsonProperty("photoUrls")
  public Set<String> photoUrls = new LinkedHashSet<>();

  /** Example: {@code null} */
  @JsonProperty("tags")
  @Nullable
  public List<Tag> tags = new ArrayList<>();

  /**
   * pet status in the store
   *
   * <p>Example: {@code null}
   *
   * @deprecated This property is deprecated.
   */
  @Deprecated
  @JsonProperty("status")
  @Nullable
  public StatusEnum status;

  @SuppressWarnings("NullAway.Init")
  public Pet() {}

  @com.fasterxml.jackson.annotation.JsonCreator
  public Pet(
      @JsonProperty(value = "name", required = true) String name,
      @JsonProperty(value = "photoUrls", required = true) Set<String> photoUrls) {
    this.name = java.util.Objects.requireNonNull(name, "name is required");
    this.photoUrls = java.util.Objects.requireNonNull(photoUrls, "photoUrls is required");
  }
}
