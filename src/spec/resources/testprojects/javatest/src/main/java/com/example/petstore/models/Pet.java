package com.example.petstore.models;

import com.example.petstore.models.Category;
import com.example.petstore.models.Tag;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
  public Long id;

  @JsonProperty("name")
  public String name;

  @JsonProperty("category")
  public Category category;

  @JsonProperty("photoUrls")
  public List<String> photoUrls = new ArrayList<>();

  @JsonProperty("tags")
  public List<Tag> tags = new ArrayList<>();

  /** pet status in the store */
  @JsonProperty("status")
  public StatusEnum status;

  public Pet() {}

  public Pet(String name, List<String> photoUrls) {
    this.name = name;
    this.photoUrls = photoUrls;
  }
}
