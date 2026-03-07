package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;

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

  @JsonProperty("id")
  public Long id;

  @JsonProperty("petId")
  public Long petId;

  @JsonProperty("quantity")
  public Integer quantity;

  @JsonProperty("shipDate")
  public OffsetDateTime shipDate;

  /** Order Status */
  @JsonProperty("status")
  public StatusEnum status;

  @JsonProperty("complete")
  public Boolean complete;
}
