package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.List;
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

  @JsonProperty("id")
  @Nullable
  public Long id;

  @JsonProperty("name")
  @Nullable
  public String name;

  @JsonProperty("category")
  @Nullable
  public Category category;

  @JsonProperty("photoUrls")
  @Nullable
  public List<String> photoUrls = new ArrayList<>();

  @JsonProperty("tags")
  @Nullable
  public List<Tag> tags = new ArrayList<>();

  /**
   * pet status in the store
   *
   * @deprecated This property is deprecated.
   */
  @Deprecated
  @JsonProperty("status")
  @Nullable
  public StatusEnum status;

  public Pet() {}

  public Pet(String name, List<String> photoUrls) {
    this.name = name;
    this.photoUrls = photoUrls;
  }
}
