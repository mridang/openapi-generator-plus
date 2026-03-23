package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class Order {

  public enum StatusEnum {
    PLACED("placed"),
    APPROVED("approved"),
    DELIVERED("delivered");

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

  /** Example: {@code 198772} */
  @JsonProperty("petId")
  @Nullable
  public Long petId;

  /** Example: {@code 7} */
  @JsonProperty("quantity")
  @Nullable
  public Integer quantity;

  /** Example: {@code null} */
  @JsonProperty("shipDate")
  @Nullable
  public OffsetDateTime shipDate;

  /**
   * Order Status
   *
   * <p>Example: {@code approved}
   */
  @JsonProperty("status")
  @Nullable
  public StatusEnum status;

  /** Example: {@code null} */
  @JsonProperty("complete")
  @Nullable
  public Boolean complete;
}
